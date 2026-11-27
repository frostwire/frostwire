/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.search.relay.LocalSharedTorrent;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Opening, closing and upgrading the on-device index, and what it makes of odd file lists. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AndroidLocalIndexLifecycleTest {

  private static final String DB = "lifecycle-shared-torrents.db";

  private final Context context = ApplicationProvider.getApplicationContext();
  private AndroidLocalIndex index;

  @Before
  public void setUp() {
    context.deleteDatabase(DB);
    index = AndroidLocalIndex.open(context, DB);
  }

  @After
  public void tearDown() {
    index.close();
    context.deleteDatabase(DB);
  }

  private static LocalSharedTorrent torrent(String name, String filesJson) {
    byte[] infoHash = new byte[20];
    Arrays.fill(infoHash, (byte) name.length());
    infoHash[0] = (byte) name.charAt(0);
    byte[] nodeId = new byte[20];
    Arrays.fill(nodeId, (byte) 'n');
    byte[] pub = new byte[32];
    Arrays.fill(pub, (byte) 'p');
    return new LocalSharedTorrent.Builder()
        .infoHash(infoHash)
        .name(name)
        .sizeBytes(10)
        .fileCount(2)
        .filesJson(filesJson)
        .tags("")
        .publisherNodeId(nodeId)
        .publisherEd25519Pub(pub)
        .publisherUtpPort(6881)
        .addedAt(1)
        .lastSeenAt(2)
        .build();
  }

  @Test
  public void aClosedIndexRejectsEveryOperation() {
    LocalSharedTorrent t = torrent("closed", "[]");
    assertTrue(index.isOpen());
    index.close();
    index.close(); // closing twice is harmless
    assertFalse(index.isOpen());
    assertThrows(IllegalStateException.class, () -> index.upsert(t));
    assertThrows(IllegalStateException.class, () -> index.get(t.infoHashHex()));
    assertThrows(IllegalStateException.class, () -> index.delete(t.infoHashHex()));
    assertThrows(IllegalStateException.class, () -> index.search("closed", 5));
    assertThrows(IllegalStateException.class, () -> index.size());
    assertThrows(IllegalStateException.class, () -> index.listAll());
    assertThrows(IllegalStateException.class, () -> index.markPublished(t.infoHashHex(), 1));
    assertThrows(IllegalStateException.class, () -> index.updateLastSeen(t.infoHashHex(), 1));
    assertThrows(IllegalStateException.class, () -> index.needsRepublish(100, 10));
  }

  @Test
  public void reopeningKeepsTheIndexedTorrents() {
    LocalSharedTorrent t = torrent("persisted", "[]");
    index.upsert(t);
    index.close();

    index = AndroidLocalIndex.open(context, DB);

    assertEquals(1, index.size());
    assertEquals("persisted", index.get(t.infoHashHex()).get().name());
  }

  @Test
  public void anOldSchemaIsRebuiltEmptyAndUsable() {
    index.close();
    context.deleteDatabase(DB);
    SQLiteDatabase old = context.openOrCreateDatabase(DB, Context.MODE_PRIVATE, null);
    old.execSQL("CREATE TABLE shared_torrents (info_hash TEXT PRIMARY KEY, junk TEXT)");
    old.execSQL("INSERT INTO shared_torrents VALUES ('aa', 'old row')");
    old.setVersion(1);
    old.close();

    index = AndroidLocalIndex.open(context, DB);

    assertEquals("the old rows are dropped with the old schema", 0, index.size());
    LocalSharedTorrent t = torrent("rebuilt", "[]");
    index.upsert(t);
    assertEquals(1, index.size());
  }

  @Test
  public void fileListsAreReadLeniently() {
    String files =
        "[{\"path\":\"music/song.mp3\",\"size\":5},"
            + "{\"path\":null,\"size\":1},"
            + "{\"path\":\"\",\"size\":2},"
            + "{\"size\":3},"
            + "{\"path\":\"music/other.mp3\"},"
            + "{\"path\":\"music/sized.mp3\",\"size\":null}]";
    index.upsert(torrent("odd", files));

    List<LocalSharedTorrent> bySong = index.search("song", 5);
    assertEquals(1, bySong.size());
    assertEquals("music/song.mp3", bySong.get(0).matchedFile());
    assertEquals("music/other.mp3", index.search("other", 5).get(0).matchedFile());
    assertEquals("music/sized.mp3", index.search("sized", 5).get(0).matchedFile());
  }

  @Test
  public void aFileListThatIsNotJsonIndexesTheTorrentWithoutFiles() {
    LocalSharedTorrent t = torrent("garbled", "this is not json");
    index.upsert(t);

    assertTrue(index.get(t.infoHashHex()).isPresent());
    List<LocalSharedTorrent> byName = index.search("garbled", 5);
    assertEquals(1, byName.size());
    assertNull(byName.get(0).matchedFile());
    assertTrue(index.search("anything", 5).isEmpty());
  }

  @Test
  public void aHugeFileListIsCappedAtTheMaximumPerTorrent() {
    StringBuilder json = new StringBuilder("[");
    int count = AndroidLocalIndex.MAX_FILES_PER_TORRENT + 5;
    for (int i = 0; i < count; i++) {
      if (i > 0) json.append(',');
      json.append("{\"path\":\"d/f").append(i).append(".bin\",\"size\":1}");
    }
    json.append(']');
    index.upsert(torrent("huge", json.toString()));

    assertEquals(1, index.search("f9999", 5).size());
    assertTrue("the entries past the cap are not indexed", index.search("f10001", 5).isEmpty());
  }

  @Test
  public void searchTermsThatSanitizeToNothingFindNothing() {
    index.upsert(torrent("nothing", "[]"));
    assertTrue(index.search("   ", 5).isEmpty());
    assertTrue(index.search(null, 5).isEmpty());
    assertTrue(index.search("\u0001\u0002", 5).isEmpty());
  }

  @Test
  public void deletingOrUpdatingAnUnknownHashChangesNothing() {
    LocalSharedTorrent t = torrent("known", "[]");
    index.upsert(t);
    index.delete("ffffffffffffffffffffffffffffffffffffffff");
    index.updateLastSeen("ffffffffffffffffffffffffffffffffffffffff", 99);
    index.markPublished("ffffffffffffffffffffffffffffffffffffffff", 99);
    assertEquals(1, index.size());
    assertEquals(2, index.get(t.infoHashHex()).get().lastSeenAt());
  }
}
