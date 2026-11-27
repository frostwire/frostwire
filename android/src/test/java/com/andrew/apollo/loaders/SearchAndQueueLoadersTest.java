/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.loaders;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.MusicUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Search results, and the queue the player shows as a cursor over MediaStore. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@SuppressWarnings("deprecation")
public class SearchAndQueueLoadersTest {

  private Context context;
  private ApolloServiceFixture fixture;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.installPlatform(false);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.stop();
    }
    FakeConfig.uninstall();
    ApolloServiceFixture.noService();
  }

  @Test
  public void aSearchMatchesSongsAlbumsAndArtistsAndKeepsTheMostSpecificId() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(
                p,
                row("_id", 5L, "title", "Hey", "artist", "Ann", "album", "LP"),
                row("_id", 6L, "title", "", "artist", "Ann", "album", "LP"),
                row("_id", 7L, "title", "", "artist", "Ann", "album", ""),
                row("_id", 8L, "title", "", "artist", "", "album", ""));

    List<Song> result = new SearchLoader(context, "an n").loadInBackground();

    assertEquals(4, result.size());
    assertEquals("a song", 5, result.get(0).mSongId);
    assertEquals("an album with no song", 6, result.get(1).mSongId);
    assertEquals("an artist with no album or song", 7, result.get(2).mSongId);
    assertEquals("nothing to point at", -1, result.get(3).mSongId);
    assertEquals("Hey", result.get(0).mSongName);
    assertEquals("Ann", result.get(0).mArtistName);
    assertEquals("LP", result.get(0).mAlbumName);
  }

  @Test
  public void aSearchAsksForTheEncodedQueryInEveryColumn() {
    SearchLoader.SearchCursorParameters parameters =
        SearchLoader.SearchCursorParameters.buildSearchCursorParameters("a b&c");

    assertArrayEquals(
        new String[] {"%a%20b%26c%", "%a%20b%26c%", "%a%20b%26c%"}, parameters.selectionArgs);
    assertTrue(parameters.selection.contains("artist like ?"));
    assertTrue(parameters.selection.contains("album like ?"));
    assertTrue(parameters.selection.contains("title like ?"));
  }

  @Test
  public void aSearchWithoutResultsOrWhenTheStoreFailsFindsNothing() {
    assertTrue(new SearchLoader(context, "x").loadInBackground().isEmpty());

    FakeMediaStore.failure = new IllegalStateException("down");
    assertTrue(new SearchLoader(context, "x").loadInBackground().isEmpty());
  }

  private static Cursor songsInSelection(String[] p, String selection, long... known) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (long id : known) {
      if (selection.matches(".*[(,]" + id + "[,)].*")) {
        rows.add(
            row(
                "_id",
                id,
                "title",
                "t" + id,
                "artist",
                "a" + id,
                "album",
                "al" + id,
                "duration",
                9));
      }
    }
    return cursor(p, rows);
  }

  @Test
  public void theQueueCursorWalksTheQueueInOrderAndDropsSongsMediaStoreForgot() throws Exception {
    fixture = ApolloServiceFixture.start();
    ApolloServiceFixture.installPlatform(false);
    fixture.service.open(new long[] {3, 1, 2}, 0);
    shadowOf(Looper.getMainLooper()).idle();
    FakeMediaStore.onQuery = (u, p, s, a, o) -> songsInSelection(p, s, 1, 3);

    Cursor queue = QueueLoader.makeQueueCursor(context);

    assertEquals("song 2 is gone from MediaStore", 2, queue.getCount());
    assertArrayEquals("and from the queue", new long[] {3, 1}, MusicUtils.getQueue());
    assertTrue(queue.moveToFirst());
    assertEquals("t3", queue.getString(1));
    assertEquals(3, queue.getLong(0));
    assertEquals(9, queue.getInt(4));
    assertTrue(queue.moveToNext());
    assertEquals("t1", queue.getString(1));
    assertEquals("a1", queue.getString(2));
    assertFalse(queue.isNull(1));
    assertEquals(
        java.util.Arrays.asList("_id", "title", "artist", "album", "duration"),
        java.util.Arrays.asList(queue.getColumnNames()));
    assertEquals(Cursor.FIELD_TYPE_STRING, queue.getType(1));
    assertEquals(9.0, queue.getDouble(4), 0.0);
    assertEquals(9.0f, queue.getFloat(4), 0.0f);
    assertEquals(9, queue.getShort(4));
    assertFalse("past the end", queue.moveToPosition(2));
    queue.deactivate();
    queue.close();
    assertEquals("a closed cursor reads as blank, not as a crash", "", queue.getString(1));
    assertEquals(0, queue.getInt(4));
    assertEquals(0, queue.getLong(0));
  }

  @Test
  public void aQueueCursorCanBeRequeriedAfterTheQueueChanges() throws Exception {
    fixture = ApolloServiceFixture.start();
    ApolloServiceFixture.installPlatform(false);
    fixture.service.open(new long[] {1}, 0);
    shadowOf(Looper.getMainLooper()).idle();
    FakeMediaStore.onQuery = (u, p, s, a, o) -> songsInSelection(p, s, 1, 2);
    Cursor queue = QueueLoader.makeQueueCursor(context);
    assertEquals(1, queue.getCount());

    fixture.service.enqueue(new long[] {2}, com.andrew.apollo.MusicPlaybackService.LAST);
    shadowOf(Looper.getMainLooper()).idle();

    assertTrue(queue.requery());
    assertEquals(2, queue.getCount());
    queue.close();
  }

  @Test
  public void anEmptyQueueIsAnEmptyCursorAndTheLoaderListsNothing() throws Exception {
    Cursor queue = QueueLoader.makeQueueCursor(context);

    assertEquals(0, queue.getCount());
    assertTrue(new QueueLoader(context).loadInBackground().isEmpty());
    queue.close();
  }

  @Test
  public void aStoreThatFailsLeavesTheQueueCursorEmpty() throws Exception {
    fixture = ApolloServiceFixture.start();
    ApolloServiceFixture.installPlatform(false);
    fixture.service.open(new long[] {1, 2}, 0);
    shadowOf(Looper.getMainLooper()).idle();
    FakeMediaStore.failure = new IllegalStateException("down");

    Cursor queue = QueueLoader.makeQueueCursor(context);

    assertEquals(0, queue.getCount());
    queue.close();
  }
}
