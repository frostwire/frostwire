/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.database.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.content.ContentValues;
import com.frostwire.database.Cursor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLiteDatabase writes and reads rows of a real SQLite file and reports SQL errors as -1. */
class SQLiteDatabaseTest {

  @TempDir Path tmp;

  private String path;
  private Connection connection;
  private SQLiteDatabase db;

  @BeforeEach
  void openDatabase() throws Exception {
    path = tmp.resolve("crawldb").toString();
    connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    db = new SQLiteDatabase(path, connection);
    db.execSQL(
        "CREATE TABLE cache_data (id INTEGER PRIMARY KEY, key VARCHAR, data BLOB,"
            + " date_added INTEGER)");
  }

  @AfterEach
  void closeDatabase() throws Exception {
    db.close();
    connection.close();
  }

  private static ContentValues row(String key, byte[] data, long dateAdded) {
    ContentValues values = new ContentValues();
    values.put("key", key);
    values.put("data", data);
    values.put("date_added", dateAdded);
    return values;
  }

  private List<String> keys(String where) {
    Cursor c =
        db.rawQueryWithFactory(
            "SELECT key FROM cache_data"
                + (where != null ? " WHERE " + where : "")
                + " ORDER BY id",
            null);
    List<String> keys = new ArrayList<>();
    while (c.moveToNext()) {
      keys.add(c.getString(1));
    }
    c.close();
    return keys;
  }

  private void insertAbc() {
    db.insert("cache_data", null, row("a", new byte[] {1}, 10));
    db.insert("cache_data", null, row("b", new byte[] {2}, 20));
    db.insert("cache_data", null, row("c", new byte[] {3}, 30));
  }

  @Test
  void insertedRowsCanBeReadBack() {
    db.insert("cache_data", null, row("magnet:?xt=1", new byte[] {7, 8, 9}, 1234L));

    Cursor c =
        db.rawQueryWithFactory(
            "SELECT key, data, date_added FROM cache_data WHERE key = ?",
            new String[] {"magnet:?xt=1"});

    assertTrue(c.moveToNext());
    assertEquals("magnet:?xt=1", c.getString(c.getColumnIndex("key")));
    assertArrayEquals(new byte[] {7, 8, 9}, c.getBytes(c.getColumnIndex("data")));
    assertEquals(1234L, c.getLong(c.getColumnIndex("date_added")));
    assertFalse(c.moveToNext());
    c.close();
  }

  @Test
  void insertWithoutValuesAddsARowThroughTheNullColumnHack() {
    db.insert("cache_data", "key", new ContentValues());
    db.insert("cache_data", "key", null);

    Cursor c = db.rawQueryWithFactory("SELECT count(*), count(key) FROM cache_data", null);
    assertTrue(c.moveToNext());
    assertEquals(2, c.getInt(1));
    assertEquals(0, c.getInt(2));
    c.close();
  }

  @Test
  void insertIntoAMissingTableReportsMinusOne() {
    assertEquals(-1, db.insert("no_such_table", null, row("a", null, 1)));
  }

  @Test
  void updateChangesOnlyTheMatchingRowsAndCountsThem() {
    insertAbc();
    ContentValues values = new ContentValues();
    values.put("key", "z");

    assertEquals(2, db.update("cache_data", values, "date_added >= ?", new String[] {"20"}));
    assertEquals(List.of("a", "z", "z"), keys(null));
  }

  @Test
  void updateWithoutWhereChangesEveryRow() {
    insertAbc();
    ContentValues values = new ContentValues();
    values.put("date_added", 99L);

    assertEquals(3, db.update("cache_data", values, null, null));
    assertEquals(List.of("a", "b", "c"), keys("date_added = 99"));
  }

  @Test
  void updateWritesNullForNullValues() {
    insertAbc();
    ContentValues values = new ContentValues();
    values.put("data", (byte[]) null);

    assertEquals(1, db.update("cache_data", values, "key = 'b'", null));
    assertEquals(List.of("b"), keys("data IS NULL"));
  }

  @Test
  void updateRejectsEmptyValues() {
    assertThrows(IllegalArgumentException.class, () -> db.update("cache_data", null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> db.update("cache_data", new ContentValues(), null, null));
  }

  @Test
  void updateOfAMissingColumnReportsMinusOne() {
    ContentValues values = new ContentValues();
    values.put("no_such_column", 1);

    assertEquals(-1, db.update("cache_data", values, null, null));
  }

  @Test
  void deleteRemovesOnlyTheMatchingRowsAndCountsThem() {
    insertAbc();

    assertEquals(1, db.delete("cache_data", "key = ?", new String[] {"b"}));
    assertEquals(List.of("a", "c"), keys(null));
  }

  @Test
  void deleteWithoutWhereRemovesEveryRow() {
    insertAbc();
    assertEquals(3, db.delete("cache_data", null, null));
    assertEquals(List.of(), keys(null));

    insertAbc();
    assertEquals(3, db.delete("cache_data", "", null));
    assertEquals(List.of(), keys(null));
  }

  @Test
  void bindArgumentsAreNotParsedAsSql() {
    insertAbc();

    assertEquals(0, db.delete("cache_data", "key = ?", new String[] {"a' OR '1'='1"}));
    assertEquals(List.of("a", "b", "c"), keys(null));
  }

  @Test
  void invalidSqlInExecSqlIsLoggedAndTheDatabaseStaysUsable() {
    insertAbc();

    assertDoesNotThrow(() -> db.execSQL("DELETE FROM no_such_table"));
    assertDoesNotThrow(() -> db.execSQL("NOT SQL AT ALL"));

    db.execSQL("DELETE FROM cache_data WHERE key = 'a'");
    assertEquals(List.of("b", "c"), keys(null));
  }

  @Test
  void invalidQueriesReturnNoCursor() {
    assertNull(db.rawQueryWithFactory("SELECT * FROM no_such_table", null));
    assertNull(db.rawQueryWithFactory("SELEKT nothing", null));
  }

  @Test
  void aClosedDatabaseRejectsReadsAndWritesNamingItsPath() {
    db.close();

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> db.rawQueryWithFactory("SELECT 1", null));
    assertTrue(e.getMessage().contains(path), e.getMessage());
    assertThrows(
        IllegalStateException.class, () -> db.insert("cache_data", null, row("a", null, 1)));
    assertThrows(
        IllegalStateException.class, () -> db.update("cache_data", row("a", null, 1), null, null));
    assertThrows(IllegalStateException.class, () -> db.delete("cache_data", null, null));
  }

  @Test
  void closingTwiceIsHarmless() {
    db.close();

    assertDoesNotThrow(db::close);
  }

  @Test
  void sqlExceptionsAreUncheckedSoCallersMayCatchThemSelectively() {
    RuntimeException e = new SQLiteException();

    assertTrue(e instanceof com.frostwire.database.SQLException);
  }
}
