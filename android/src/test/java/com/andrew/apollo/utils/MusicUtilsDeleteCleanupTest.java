/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Cleaning up after deleted songs cost one playlist query and one delete per playlist for every
 * song: about 2,000 provider calls for a 200-song artist and 10 playlists (GitHub #1450). Each
 * playlist now gets one delete for all the songs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class MusicUtilsDeleteCleanupTest {

  private static final long[] SONGS = {11L, 12L, 13L};
  private static final long[] PLAYLISTS = {100L, 101L};

  /** MediaStore with three songs and two playlists; records what is queried and deleted. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> playlistQueries = new CopyOnWriteArrayList<>();
    static final List<String> memberDeletes = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (uri.getPath().contains("/playlists")) {
        playlistQueries.add(uri.toString());
        for (long id : PLAYLISTS) {
          cursor.addRow(new Object[] {id, "playlist " + id});
        }
      }
      return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      if (uri.getPath().endsWith("/members")) {
        memberDeletes.add(uri.getPath() + " " + selection);
      }
      return 0;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.playlistQueries.clear();
    FakeMediaProvider.memberDeletes.clear();
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

  @Test
  public void eachPlaylistLosesAllTheDeletedSongsInOneDelete() throws Exception {
    Thread worker =
        new Thread(
            () ->
                MusicUtils.deleteTracks(ApplicationProvider.getApplicationContext(), SONGS, false));
    worker.start();
    worker.join(10_000);

    assertEquals("the playlists are listed once", 1, FakeMediaProvider.playlistQueries.size());
    assertEquals(
        "one delete per playlist: " + FakeMediaProvider.memberDeletes,
        PLAYLISTS.length,
        FakeMediaProvider.memberDeletes.size());
    for (String delete : FakeMediaProvider.memberDeletes) {
      assertTrue(delete, delete.endsWith("audio_id IN (11,12,13)"));
    }
  }
}
