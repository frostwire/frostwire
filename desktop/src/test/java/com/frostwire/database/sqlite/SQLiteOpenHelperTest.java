/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.database.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.content.ContentValues;
import com.frostwire.content.Context;
import com.frostwire.database.Cursor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLiteOpenHelper keeps each schema version in its own folder and creates it on first open. */
class SQLiteOpenHelperTest {

  @TempDir Path tmp;

  /** Keeps its databases in the test's folder instead of FrostWire's settings folder. */
  private static final class FolderContext extends Context {
    private final Path folder;

    FolderContext(Path folder) {
      this.folder = folder;
    }

    @Override
    public File getDatabasePath(String name) {
      return folder.resolve(name).toFile();
    }
  }

  /** Creates one table and records every onCreate and onUpgrade call. */
  private static class Helper extends SQLiteOpenHelper {
    // Static: onCreate runs inside the super constructor, before instance fields exist.
    static final List<String> calls = new ArrayList<>();

    Helper(Path folder, int version) {
      super(new FolderContext(folder), "crawldb", version, null);
    }

    @Override
    protected void onCreate(SQLiteDatabase db) {
      calls.add("onCreate");
      db.execSQL("CREATE TABLE cache_data (id INTEGER PRIMARY KEY, key VARCHAR)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      calls.add("onUpgrade " + oldVersion + " -> " + newVersion);
    }
  }

  private static void insertKey(SQLiteOpenHelper helper, String key) {
    ContentValues values = new ContentValues();
    values.put("key", key);
    helper.getWritableDatabase().insert("cache_data", null, values);
  }

  private static List<String> keys(SQLiteOpenHelper helper) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables("cache_data");
    Cursor c = qb.query(helper.getReadableDatabase(), null, null, null, null, null, "id");
    List<String> keys = new ArrayList<>();
    while (c.moveToNext()) {
      keys.add(c.getString(c.getColumnIndex("key")));
    }
    c.close();
    return keys;
  }

  @Test
  void firstOpenCreatesTheVersionFolderAndTheSchema() {
    Helper.calls.clear();
    Helper helper = new Helper(tmp, 2);

    assertTrue(Files.isRegularFile(tmp.resolve("crawldb.2").resolve("crawldb")));
    assertEquals(List.of("onCreate"), Helper.calls);
    insertKey(helper, "a");
    assertEquals(List.of("a"), keys(helper));
    helper.getWritableDatabase().close();
  }

  @Test
  void reopeningKeepsTheDataAndDoesNotRecreateTheSchema() {
    Helper.calls.clear();
    Helper first = new Helper(tmp, 2);
    insertKey(first, "a");
    first.getWritableDatabase().close();

    Helper second = new Helper(tmp, 2);

    assertEquals(List.of("onCreate"), Helper.calls);
    assertEquals(List.of("a"), keys(second));
    second.getWritableDatabase().close();
  }

  @Test
  void aMissingDatabaseFileIsRecreatedInAnExistingFolder() throws Exception {
    Helper.calls.clear();
    Files.createDirectories(tmp.resolve("crawldb.2"));

    Helper helper = new Helper(tmp, 2);

    assertEquals(List.of("onCreate"), Helper.calls);
    assertEquals(List.of(), keys(helper));
    helper.getWritableDatabase().close();
  }

  @Test
  void aNewVersionStartsFromAnEmptyDatabaseInItsOwnFolder() {
    Helper.calls.clear();
    Helper v1 = new Helper(tmp, 1);
    insertKey(v1, "old");
    v1.getWritableDatabase().close();

    Helper v2 = new Helper(tmp, 2);

    // Versions never share a file, so there is nothing to upgrade.
    assertEquals(List.of("onCreate", "onCreate"), Helper.calls);
    assertEquals(List.of(), keys(v2));
    assertTrue(Files.exists(tmp.resolve("crawldb.1").resolve("crawldb")));
    v2.getWritableDatabase().close();
  }

  @Test
  void readsAndWritesShareOneDatabase() {
    Helper helper = new Helper(tmp, 1);

    assertSame(helper.getWritableDatabase(), helper.getReadableDatabase());
    helper.getWritableDatabase().close();
  }

  @Test
  void sizeInBytesCountsEveryFileInTheVersionFolder() throws Exception {
    Helper helper = new Helper(tmp, 1);
    long empty = helper.sizeInBytes();
    assertTrue(empty > 0, "size " + empty);

    Path nested = Files.createDirectories(tmp.resolve("crawldb.1").resolve("nested"));
    Files.write(nested.resolve("extra"), new byte[1000]);

    assertEquals(empty + 1000, helper.sizeInBytes());
    helper.getWritableDatabase().close();
  }

  @Test
  void sizeInBytesIsZeroOnceTheFolderIsGone() throws Exception {
    Helper helper = new Helper(tmp, 1);
    helper.getWritableDatabase().close();

    Path folder = tmp.resolve("crawldb.1");
    try (var files = Files.walk(folder)) {
      files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    assertFalse(Files.exists(folder));
    assertEquals(0, helper.sizeInBytes());
  }

  @Test
  void aFolderThatCannotBeCreatedFailsTheOpen() throws Exception {
    // A regular file where the parent folder should be makes mkdirs() fail.
    Path blocker = Files.createFile(tmp.resolve("not-a-folder"));

    RuntimeException e = assertThrows(RuntimeException.class, () -> new Helper(blocker, 1));

    assertTrue(e.getMessage().contains("Could not create database folder"), e.getMessage());
  }

  @Test
  void aDatabaseFileThatCannotBeCreatedFailsTheOpen() throws Exception {
    Path folder = Files.createDirectories(tmp.resolve("crawldb.1"));
    File folderFile = folder.toFile();
    assumeTrue(folderFile.setWritable(false) && !folderFile.canWrite(), "folder stays writable");
    try {
      RuntimeException e = assertThrows(RuntimeException.class, () -> new Helper(tmp, 1));

      assertTrue(e.getMessage().contains("Could not create database file"), e.getMessage());
      assertFalse(Files.exists(folder.resolve("crawldb")));
    } finally {
      folderFile.setWritable(true);
    }
  }

  @Test
  void aFailingOnCreateFailsTheOpenWithItsCause() {
    IllegalStateException cause = new IllegalStateException("schema failed");

    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () ->
                new SQLiteOpenHelper(new FolderContext(tmp), "broken", 1, null) {
                  @Override
                  protected void onCreate(SQLiteDatabase db) {
                    throw cause;
                  }

                  @Override
                  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
                });

    assertSame(cause, e.getCause());
  }
}
