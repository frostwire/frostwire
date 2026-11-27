/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.Looper;
import android.os.Message;
import androidx.media3.session.MediaSession;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The service's start-up, the work queued before its thread exists, and its shutdown. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceLifecycleTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    service = fixture.service;
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void workPostedBeforeThePlayerThreadExistsRunsOnceTheServiceIsInitialized()
      throws Exception {
    MusicPlaybackService.MusicPlayerHandler handler = MusicPlaybackService.getMusicPlayerHandler();
    ApolloServiceFixture.setPlayerHandler(null);
    AtomicInteger ran = new AtomicInteger();

    MusicPlaybackService.safePost(ran::incrementAndGet);
    MusicPlaybackService.safePost(null);
    idle();
    assertEquals("nothing runs without a thread", 0, ran.get());

    ApolloServiceFixture.setPlayerHandler(handler);
    service.handleCommandIntent(new Intent());
    idle();

    assertEquals(1, ran.get());
  }

  @Test
  public void onlyTheNewestSixtyFourWaitingTasksAreKept() throws Exception {
    MusicPlaybackService.MusicPlayerHandler handler = MusicPlaybackService.getMusicPlayerHandler();
    ApolloServiceFixture.setPlayerHandler(null);
    AtomicInteger ran = new AtomicInteger();

    for (int i = 0; i < 70; i++) {
      MusicPlaybackService.safePost(ran::incrementAndGet);
    }
    ApolloServiceFixture.setPlayerHandler(handler);
    service.handleCommandIntent(new Intent());
    idle();

    assertEquals(64, ran.get());
  }

  @Test
  public void workPostedWhileTheServiceIsNotRunningIsDropped() throws Exception {
    ApolloServiceFixture.noService();
    AtomicInteger ran = new AtomicInteger();

    MusicPlaybackService.safePost(ran::incrementAndGet);
    idle();

    assertEquals(0, ran.get());
  }

  @Test
  public void workPostedToARunningServiceRunsOnItsThread() {
    AtomicInteger ran = new AtomicInteger();

    MusicPlaybackService.safePost(ran::incrementAndGet);
    idle();

    assertEquals(1, ran.get());
  }

  @Test
  public void aStartDeliveredToTheServiceIsStickyUnlessItIsAShutdown() {
    assertEquals(
        Service.START_STICKY,
        service.onStartCommand(new Intent(MusicPlaybackService.SERVICECMD), 0, 1));
    assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 2));
    assertEquals(
        Service.START_NOT_STICKY,
        service.onStartCommand(new Intent(MusicPlaybackService.SHUTDOWN_ACTION), 0, 3));
  }

  @Test
  public void startingTheServiceMakesItAForegroundServiceAtOnce() {
    service.onStartCommand(new Intent(MusicPlaybackService.SERVICECMD), 0, 1);

    assertNotNull(
        "the system kills an app whose started service doesn't call startForeground()",
        shadowOf(service).getLastForegroundNotification());
  }

  @Test
  public void removingTheTaskWhileNothingPlaysStopsTheService() {
    service.handleCommandIntent(new Intent());
    idle();
    service.open(new long[] {1, 2}, 0);
    service.pause();
    idle();

    service.onTaskRemoved(new Intent());
    idle();

    assertTrue(shadowOf(service).isStoppedBySelf());
  }

  @Test
  public void aMusicScreenInTheForegroundLetsTheNotificationBePromoted() {
    service.handleCommandIntent(new Intent());
    idle();
    service.open(new long[] {1, 2}, 0);
    idle();
    Intent foreground = new Intent(MusicPlaybackService.FOREGROUND_STATE_CHANGED);
    foreground.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, true);
    service.handleCommandIntent(foreground);
    idle();
    MediaSession session = service.getSessions().get(0);

    service.onUpdateNotification(session, true);

    assertNotNull(shadowOf(service).getLastForegroundNotification());
  }

  @Test
  public void aBackgroundedAppStillUpdatesTheNotificationWithoutCrashing() {
    service.handleCommandIntent(new Intent());
    idle();
    service.open(new long[] {1, 2}, 0);
    idle();
    MediaSession session = service.getSessions().get(0);

    service.onUpdateNotification(session, true);
    service.onUpdateNotification(session, false);

    assertFalse(service.isMusicActivityInForeground());
  }

  @Test
  public void destroyingTheServiceReleasesItsPlayerAndForgetsTheInstance() {
    service.handleCommandIntent(new Intent());
    idle();
    service.open(new long[] {1, 2}, 0);
    idle();

    fixture.controller.destroy();

    assertNull(MusicPlaybackService.getInstance());
    assertFalse(MusicPlaybackService.instanceReady());
  }

  @Test
  public void theHandlerFadesTheVolumeDownAndUpWithinItsLimits() {
    MusicPlaybackService.MusicPlayerHandler handler = MusicPlaybackService.getMusicPlayerHandler();
    service.handleCommandIntent(new Intent());
    idle();

    for (int i = 0; i < 40; i++) {
      handler.handleMessage(Message.obtain(handler, 6)); // FADE_DOWN
    }
    handler.removeMessages(6); // the fade re-posts itself until it is done
    idle();
    assertEquals("it fades down only to a fifth", 0.2f, currentVolume(), 0.001f);

    for (int i = 0; i < 100; i++) {
      handler.handleMessage(Message.obtain(handler, 7)); // FADE_UP
    }
    handler.removeMessages(7);
    idle();
    assertEquals("and back up to full", 1f, currentVolume(), 0.001f);

    handler.handleMessage(Message.obtain(handler, 4)); // SERVER_DIED with nothing wrong
    handler.handleMessage(Message.obtain(handler, 99));
    idle();
    assertEquals(1f, currentVolume(), 0.001f);
  }

  private float currentVolume() {
    // The service's own player, attached by its first command.
    return servicePlayer().getVolume();
  }

  private androidx.media3.exoplayer.ExoPlayer servicePlayer() {
    return (androidx.media3.exoplayer.ExoPlayer) service.getSessions().get(0).getPlayer();
  }
}
