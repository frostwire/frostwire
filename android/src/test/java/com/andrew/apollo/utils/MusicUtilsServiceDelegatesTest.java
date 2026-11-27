/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.MusicPlaybackService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * MusicUtils is the UI's handle on the playback service: with the service running its calls reach
 * the service, and with no service they answer with harmless defaults instead of crashing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsServiceDelegatesTest {

  private ApolloServiceFixture fixture;

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.stop();
    }
    ApolloServiceFixture.noService();
  }

  @Test
  public void withNoServiceTheQueriesAnswerWithDefaults() throws Exception {
    ApolloServiceFixture.noService();

    assertFalse(MusicUtils.isMusicPlaybackServiceRunning());
    assertFalse(MusicUtils.isPlaying());
    assertTrue(MusicUtils.isStopped());
    assertFalse("stopped isn't paused", MusicUtils.isPaused());
    assertFalse(MusicUtils.isShuffleEnabled());
    assertEquals(0, MusicUtils.getRepeatMode());
    assertNull(MusicUtils.getTrackName());
    assertNull(MusicUtils.getArtistName());
    assertNull(MusicUtils.getAlbumName());
    assertEquals(-1, MusicUtils.getCurrentAlbumId());
    assertEquals(-1, MusicUtils.getCurrentAudioId());
    assertEquals(-1, MusicUtils.getCurrentSimplePlayerAudioId());
    assertEquals(-1, MusicUtils.getCurrentArtistId());
    assertEquals(-1, MusicUtils.getAudioSessionId());
    assertEquals(0, MusicUtils.getQueue().length);
    assertEquals(0, MusicUtils.removeTrack(5));
    assertEquals(0, MusicUtils.removeTracks(new long[] {5}));
    assertEquals(0, MusicUtils.position());
    assertEquals(0, MusicUtils.duration());
    assertFalse(MusicUtils.isFavorite());
    assertNull(MusicUtils.getFilePath());
    // None of these may throw with nobody to talk to.
    MusicUtils.next();
    MusicUtils.previous();
    MusicUtils.play();
    MusicUtils.pause();
    MusicUtils.playPauseOrResume();
    MusicUtils.cycleRepeat();
    MusicUtils.cycleShuffle();
    MusicUtils.toggleFavorite();
    MusicUtils.seek(10);
    MusicUtils.setQueuePosition(1);
    MusicUtils.moveQueueItem(0, 1);
    MusicUtils.clearQueue();
    MusicUtils.refresh();
  }

  @Test
  public void withTheServiceRunningTheCallsReachTheQueue() throws Exception {
    fixture = ApolloServiceFixture.start();
    assertTrue(MusicUtils.isMusicPlaybackServiceRunning());
    assertSame(fixture.service, MusicUtils.getMusicPlaybackService());

    fixture.service.open(new long[] {10, 20, 30}, 1);
    idle();

    assertArrayEquals(new long[] {10, 20, 30}, MusicUtils.getQueue());
    assertEquals(20, MusicUtils.getCurrentAudioId());
    assertEquals(1, fixture.service.getQueuePosition());
    MusicUtils.setQueuePosition(2);
    idle();
    assertEquals(30, MusicUtils.getCurrentAudioId());
    MusicUtils.moveQueueItem(2, 0);
    idle();
    assertArrayEquals(new long[] {30, 10, 20}, MusicUtils.getQueue());
    assertEquals(1, MusicUtils.removeTrack(10));
    assertEquals(1, MusicUtils.removeTracks(new long[] {20}));
    assertArrayEquals(new long[] {30}, MusicUtils.getQueue());
  }

  @Test
  public void playbackControlsDriveTheService() throws Exception {
    fixture = ApolloServiceFixture.start();
    fixture.service.open(new long[] {1, 2, 3}, 0);
    idle();

    MusicUtils.play();
    idle();
    assertTrue(MusicUtils.isPlaying());
    assertFalse(MusicUtils.isStopped());
    assertFalse(MusicUtils.isPaused());

    MusicUtils.pause();
    idle();
    assertFalse(MusicUtils.isPlaying());
    assertTrue("a paused song isn't stopped", MusicUtils.isPaused());

    MusicUtils.playPauseOrResume();
    idle();
    assertTrue(MusicUtils.isPlaying());
    MusicUtils.playPauseOrResume();
    idle();
    assertFalse(MusicUtils.isPlaying());

    MusicUtils.next();
    idle();
    assertEquals(2, MusicUtils.getCurrentAudioId());
    assertTrue("next keeps the music going", MusicUtils.isPlaying());
    MusicUtils.previous();
    idle();
    assertEquals(1, MusicUtils.getCurrentAudioId());

    MusicUtils.seek(0);
    idle();
    assertEquals(0, MusicUtils.position());
  }

  @Test
  public void repeatCyclesNoneAllCurrentAndShuffleToggles() throws Exception {
    fixture = ApolloServiceFixture.start();
    fixture.service.setRepeatMode(MusicPlaybackService.REPEAT_NONE);

    MusicUtils.cycleRepeat();
    assertEquals(MusicPlaybackService.REPEAT_ALL, MusicUtils.getRepeatMode());
    MusicUtils.cycleRepeat();
    assertEquals(MusicPlaybackService.REPEAT_CURRENT, MusicUtils.getRepeatMode());
    MusicUtils.cycleRepeat();
    assertEquals(MusicPlaybackService.REPEAT_NONE, MusicUtils.getRepeatMode());

    assertFalse(MusicUtils.isShuffleEnabled());
    MusicUtils.cycleShuffle();
    assertTrue(MusicUtils.isShuffleEnabled());
    MusicUtils.cycleShuffle();
    assertFalse(MusicUtils.isShuffleEnabled());
  }

  @Test
  public void clearQueueKeepsThePlayingSong() throws Exception {
    fixture = ApolloServiceFixture.start();
    fixture.service.open(new long[] {1, 2, 3}, 1);
    idle();

    MusicUtils.clearQueue();
    idle();

    assertArrayEquals(new long[] {2}, MusicUtils.getQueue());
  }

  @Test
  public void shutdownRequestGoesToARunningServiceOnly() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.noService();
    MusicUtils.requestMusicPlaybackServiceShutdown(context);
    MusicUtils.requestMusicPlaybackServiceShutdown(null);

    fixture = ApolloServiceFixture.start();
    fixture.service.open(new long[] {1, 2}, 0);
    MusicUtils.play();
    idle();
    assertTrue(MusicUtils.isPlaying());
    MusicUtils.requestMusicPlaybackServiceShutdown(context);
    idle();

    assertFalse("the shutdown stops the music", MusicUtils.isPlaying());
  }

  @Test
  public void startIntentCarriesTheServiceCommandAction() {
    Context context = ApplicationProvider.getApplicationContext();
    Intent intent = MusicUtils.buildStartMusicPlaybackServiceIntent(context);

    assertEquals(MusicPlaybackService.SERVICECMD, intent.getAction());
    assertEquals(MusicPlaybackService.class.getName(), intent.getComponent().getClassName());
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  public void executeWithTheServiceRunsTheTaskOnItsThread() throws Exception {
    fixture = ApolloServiceFixture.start();
    final boolean[] ran = {false};

    MusicUtils.executeWithMusicPlaybackService(
        ApplicationProvider.getApplicationContext(), () -> ran[0] = true);
    idle();

    assertTrue(ran[0]);
  }

  private static void assertSame(Object expected, Object actual) {
    org.junit.Assert.assertSame(expected, actual);
  }

  private static void idle() {
    shadowOf(android.os.Looper.getMainLooper()).idle();
    ShadowLooper.idleMainLooper();
  }
}
