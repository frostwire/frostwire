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
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
    static final List<Thread> threads = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    private static boolean known(Uri uri) {
      memberUris.add(uri.getPath());
      threads.add(Thread.currentThread());
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
    FakeMediaProvider.threads.clear();
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

  /** "Add to this playlist" calls it from a menu click, with no playback service running. */
  @Test
  @Config(sdk = 34)
  public void addingFromTheMainThreadReachesMediaStoreOffIt() throws Exception {
    MusicUtils.addToPlaylistSilently(
        ApplicationProvider.getApplicationContext(), new long[] {1}, PLAYLIST);
    long deadline = System.currentTimeMillis() + 5_000;
    while (FakeMediaProvider.members.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      ShadowLooper.idleMainLooper();
    }

    assertEquals(1, FakeMediaProvider.members.size());
    for (Thread thread : FakeMediaProvider.threads) {
      assertTrue("MediaStore reached on the main thread", thread != Thread.currentThread());
    }
  }

  /** Adding to the playlist that is playing also queues the new songs, so they get played. */
  @Test
  @Config(sdk = 34)
  public void songsAddedToThePlayingPlaylistJoinTheQueue() throws Exception {
    FakeMediaProvider.members.add(new long[] {1, 0});
    FakeMediaProvider.members.add(new long[] {2, 1});
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    // A player thread with a real ExoPlayer playing fake media, as the service makes in the app.
    HandlerThread playerThread = new HandlerThread("test-player");
    playerThread.start();
    Looper looper = playerThread.getLooper();
    Constructor<?> newHandler =
        Class.forName("com.andrew.apollo.MusicPlaybackService$MusicPlayerHandler")
            .getDeclaredConstructor(Looper.class);
    newHandler.setAccessible(true);
    Field playerHandler = MusicPlaybackService.class.getDeclaredField("mPlayerHandler");
    playerHandler.setAccessible(true);
    playerHandler.set(null, newHandler.newInstance(looper));
    Method attachPlayer =
        MusicPlaybackService.class.getDeclaredMethod("attachPlayer", ExoPlayer.class);
    attachPlayer.setAccessible(true);
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    new Handler(looper)
        .post(
            () -> {
              player.set(
                  new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
                      .setMediaSourceFactory(new FakeMediaSourceFactory())
                      .setLooper(looper)
                      .build());
              try {
                attachPlayer.invoke(service, player.get());
              } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
              }
            });
    shadowOf(looper).idle();
    Field ref = MusicUtils.class.getDeclaredField("musicPlaybackServiceRef");
    ref.setAccessible(true);
    ref.set(null, new WeakReference<>(service));
    try {
      service.open(new long[] {1, 2, 3}, 0);
      shadowOf(looper).idle();

      add(9);
      long deadline = System.currentTimeMillis() + 5_000;
      while (service.getQueue().length < 4 && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
        shadowOf(looper).idle();
      }

      assertArrayEquals(new long[] {1, 2, 3, 9}, service.getQueue());
    } finally {
      ref.set(null, null);
      new Handler(looper).post(() -> player.get().release());
      shadowOf(looper).idle();
      playerHandler.set(null, (Handler) null);
      playerThread.quitSafely();
    }
  }
}
