/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.loaders.PlaylistSongLoader;
import com.andrew.apollo.model.Song;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Reordering and removing songs in a playlist (GitHub #1448). The screen hides members that aren't
 * music or have no title, so its row numbers aren't MediaStore's member positions and a drag moved
 * the wrong song. Removing deleted by song id, which took out every copy of a song that was in the
 * playlist twice.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsPlaylistMembersTest {

  private static final long PLAYLIST = 5L;

  /**
   * Playlist 5 in play order: member 50 (a ringtone the screen hides), 51 (song 1), 52 (song 2), 53
   * (song 1 again), 54 (song 3).
   */
  public static final class FakeMediaProvider extends ContentProvider {
    static final long[][] MEMBERS = { // {member id, audio id, is music}
      {50, 900, 0}, {51, 1, 1}, {52, 2, 1}, {53, 1, 1}, {54, 3, 1}
    };
    static final List<String> updates = new CopyOnWriteArrayList<>();
    static final List<String> deletes = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (!uri.getPath().endsWith("/" + PLAYLIST + "/members")) {
        return cursor;
      }
      boolean musicOnly = selection != null && selection.contains("is_music");
      for (long[] member : MEMBERS) {
        if (musicOnly && member[2] == 0) {
          continue;
        }
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
          switch (projection[i]) {
            case "_id":
              row[i] = member[0];
              break;
            case "audio_id":
              row[i] = member[1];
              break;
            case "duration":
              row[i] = 180_000L;
              break;
            default:
              row[i] = "song " + member[1];
          }
        }
        cursor.addRow(row);
      }
      return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      updates.add(uri.getPath() + " play_order=" + values.getAsInteger("play_order"));
      return 1;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      deletes.add(uri.getPath() + " " + selection + " " + String.join(",", args));
      return 1;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      return null;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.updates.clear();
    FakeMediaProvider.deletes.clear();
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  private static Context context() {
    return ApplicationProvider.getApplicationContext();
  }

  private static void onWorker(Runnable work) throws InterruptedException {
    Thread worker = new Thread(work);
    worker.start();
    worker.join(5_000);
  }

  @Test
  public void eachRowKnowsItsPlaylistMember() throws Exception {
    List<Song> songs = new ArrayList<>();
    onWorker(() -> songs.addAll(new PlaylistSongLoader(context(), PLAYLIST).loadInBackground()));

    List<Long> members = new ArrayList<>();
    for (Song song : songs) {
      members.add(song.mPlaylistMemberId);
    }
    assertEquals(List.of(51L, 52L, 53L, 54L), members);
  }

  @Test
  public void draggingMovesTheDraggedSongPastTheHiddenMember() throws Exception {
    // On screen: song 1 (51), song 2 (52), song 1 (53), song 3 (54). Drag the first row onto the
    // third: MediaStore's positions are one further on, past the hidden ringtone.
    onWorker(() -> MusicUtils.movePlaylistMember(context(), PLAYLIST, 51L, 53L));

    assertEquals(
        List.of("/external/audio/playlists/5/members/1 play_order=3"), FakeMediaProvider.updates);
  }

  @Test
  public void removingARowKeepsTheOtherCopyOfTheSameSong() throws Exception {
    onWorker(() -> MusicUtils.removePlaylistMember(context(), PLAYLIST, 53L));

    assertEquals(1, FakeMediaProvider.deletes.size());
    assertEquals(
        MusicUtils.getPlaylistMembersUri(PLAYLIST).getPath() + " _id=? 53",
        FakeMediaProvider.deletes.get(0));
  }
}
