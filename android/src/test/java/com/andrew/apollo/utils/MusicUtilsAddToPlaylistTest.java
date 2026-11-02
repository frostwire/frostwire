/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertArrayEquals;
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
import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * "Add to playlist" (GitHub #1448): on Android 8 and 9 it asked MediaStore for the
 * "external_primary" volume, which those versions don't have, and added nothing; and every add
 * numbered its songs from PLAY_ORDER 1000, so songs from different adds ended up in no particular
 * order.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class MusicUtilsAddToPlaylistTest {

  private static final long PLAYLIST = 7L;

  /** One playlist's members; only answers on the volumes the running Android version has. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<long[]> members = new ArrayList<>(); // {audio id, play order}
    static final List<String> memberUris = new ArrayList<>();
    static boolean onlyLegacyVolumes;

    @Override
    public boolean onCreate() {
      return true;
    }

    private static boolean known(Uri uri) {
      memberUris.add(uri.getPath());
      return !onlyLegacyVolumes || uri.getPathSegments().get(0).equals("external");
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      if (!uri.getPath().endsWith("/members") || !known(uri)) {
        return null;
      }
      MatrixCursor cursor = new MatrixCursor(projection);
      for (long[] member : members) {
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
          switch (projection[i]) {
            case "audio_id":
              row[i] = member[0];
              break;
            case "play_order":
              row[i] = member[1];
              break;
            default:
              row[i] = members.indexOf(member) + 1L;
          }
        }
        cursor.addRow(row);
      }
      return cursor;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
      if (!known(uri)) {
        return 0;
      }
      for (ContentValues value : values) {
        members.add(new long[] {value.getAsLong("audio_id"), value.getAsLong("play_order")});
      }
      return values.length;
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
    public int delete(Uri uri, String selection, String[] args) {
      return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.members.clear();
    FakeMediaProvider.memberUris.clear();
    FakeMediaProvider.onlyLegacyVolumes = false;
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

  /** Adds from a worker thread, as the app's callers do, and waits for it. */
  private static void add(long... ids) {
    Thread worker =
        new Thread(
            () ->
                MusicUtils.addToPlaylistSilently(
                    ApplicationProvider.getApplicationContext(), ids, PLAYLIST));
    worker.start();
    try {
      worker.join(5_000);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  @Config(sdk = 28)
  public void addingWorksOnAndroidVersionsWithoutTheExternalPrimaryVolume() {
    FakeMediaProvider.onlyLegacyVolumes = true;

    add(1, 2);

    assertEquals(
        "songs added: " + FakeMediaProvider.memberUris, 2, FakeMediaProvider.members.size());
  }

  @Test
  @Config(sdk = 34)
  public void songsFromALaterAddComeAfterTheOnesAlreadyThere() {
    add(1, 2);
    add(3);
    add(4, 5);

    long previous = Long.MIN_VALUE;
    for (long[] member : FakeMediaProvider.members) {
      assertTrue(
          "play order " + member[1] + " of song " + member[0] + " after " + previous,
          member[1] > previous);
      previous = member[1];
    }
    assertEquals(5, FakeMediaProvider.members.size());
  }

  /** Adding to the playlist that is playing also queues the new songs, so they get played. */
  @Test
  @Config(sdk = 34)
  public void songsAddedToThePlayingPlaylistJoinTheQueue() throws Exception {
    FakeMediaProvider.members.add(new long[] {1, 0});
    FakeMediaProvider.members.add(new long[] {2, 1});
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    setField(service, "mPlayList", new long[] {1, 2, 3});
    setField(service, "mPlayListLen", 3);
    setField(service, "mPlayPos", 0);
    Field ref = MusicUtils.class.getDeclaredField("musicPlaybackServiceRef");
    ref.setAccessible(true);
    ref.set(null, new WeakReference<>(service));
    try {
      add(9);
      long deadline = System.currentTimeMillis() + 5_000;
      while (service.getQueue().length < 4 && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
        ShadowLooper.idleMainLooper();
      }

      assertArrayEquals(new long[] {1, 2, 3, 9}, service.getQueue());
    } finally {
      ref.set(null, null);
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
