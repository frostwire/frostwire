/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Favorites toggled from the player, and queue changes asked for from other threads. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceFavoritesTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    FakeConfig.install();
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.getPath().contains("/albums")
                ? cursor(p)
                : cursor(
                    p,
                    row(
                        "_id",
                        5L,
                        "title",
                        "Hey",
                        "artist",
                        "Ann",
                        "album",
                        "LP",
                        "album_id",
                        9L,
                        "artist_id",
                        3L,
                        "duration",
                        61_000));
    service = fixture.service;
    service.handleCommandIntent(new Intent());
    idle();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
    FakeConfig.uninstall();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void togglingFavoriteMarksTheCurrentSongThenUnmarksIt() {
    service.open(new long[] {5}, 0);
    service.pause();
    idle();
    assertFalse(service.isFavorite());

    service.toggleFavorite();
    assertTrue(service.isFavorite());

    service.toggleFavorite();
    assertFalse(service.isFavorite());
  }

  @Test
  public void aWorkerThreadsQueueChangeWaitsForThePlayerThreadAndReportsTheResult()
      throws Exception {
    service.open(new long[] {1, 2, 3}, 0);
    service.pause();
    idle();
    // A background thread isn't the UI thread: it waits for the player thread's answer.
    ApolloServiceFixture.installPlatform(false);
    AtomicInteger removed = new AtomicInteger(-1);
    Thread worker = new Thread(() -> removed.set(service.removeTrack(2)));

    worker.start();
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (worker.isAlive() && System.nanoTime() < deadline) {
      idle();
      Thread.onSpinWait();
    }
    worker.join(1_000);

    assertEquals(1, removed.get());
    assertArrayEquals(new long[] {1, 3}, service.getQueue());
  }
}
