/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Commands sent to the service as intents (the notification's buttons, a headset, other apps), and
 * the preview player the file lists use.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceCommandsTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    service = fixture.service;
    // The first command initializes the service, which restores the (empty) saved queue.
    service.handleCommandIntent(new Intent());
    idle();
    service.open(new long[] {1, 2, 3}, 0);
    service.pause();
    idle();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private void send(String action) {
    service.handleCommandIntent(new Intent(action));
    idle();
  }

  private void sendCommand(String command) {
    Intent intent = new Intent(MusicPlaybackService.SERVICECMD);
    intent.putExtra(MusicPlaybackService.CMDNAME, command);
    service.handleCommandIntent(intent);
    idle();
  }

  @Test
  public void theNotificationsToggleStartsPausedMusicAndPausesPlayingMusic() {
    send(MusicPlaybackService.TOGGLEPAUSE_ACTION);
    assertTrue(service.isPlaying());

    send(MusicPlaybackService.TOGGLEPAUSE_ACTION);
    assertFalse(service.isPlaying());

    sendCommand(MusicPlaybackService.CMDTOGGLEPAUSE);
    assertTrue(service.isPlaying());
  }

  @Test
  public void nextAndPreviousActionsMoveThroughTheQueue() {
    send(MusicPlaybackService.NEXT_ACTION);
    assertEquals(2, service.getAudioId());

    send(MusicPlaybackService.PREVIOUS_ACTION);
    assertEquals(1, service.getAudioId());

    sendCommand(MusicPlaybackService.CMDNEXT);
    assertEquals(2, service.getAudioId());
    sendCommand(MusicPlaybackService.CMDPREVIOUS);
    assertEquals(1, service.getAudioId());
  }

  @Test
  public void playAndPauseCommandsSetThePlayingState() {
    sendCommand(MusicPlaybackService.CMDPLAY);
    assertTrue(service.isPlaying());

    sendCommand(MusicPlaybackService.CMDPAUSE);
    assertFalse(service.isPlaying());

    send("com.andrew.apollo.pause");
    assertFalse(service.isPlaying());
  }

  @Test
  public void aStopCommandPausesAndRewindsTheSong() {
    sendCommand(MusicPlaybackService.CMDPLAY);
    assertTrue(service.isPlaying());

    sendCommand(MusicPlaybackService.CMDSTOP);

    assertFalse(service.isPlaying());
    assertEquals(0, service.position());
    assertEquals("the queue stays", 3, service.getQueue().length);

    sendCommand(MusicPlaybackService.CMDPLAY);
    send(MusicPlaybackService.STOP_ACTION);
    assertFalse(service.isPlaying());
  }

  @Test
  public void theRepeatActionCyclesTheRepeatMode() {
    service.setRepeatMode(MusicPlaybackService.REPEAT_NONE);

    send("com.andrew.apollo.repeat");
    assertEquals(MusicPlaybackService.REPEAT_ALL, service.getRepeatMode());
    send("com.andrew.apollo.repeat");
    assertEquals(MusicPlaybackService.REPEAT_CURRENT, service.getRepeatMode());
    send("com.andrew.apollo.repeat");
    assertEquals(MusicPlaybackService.REPEAT_NONE, service.getRepeatMode());
  }

  @Test
  public void anIntentWithoutAnActionDoesNothing() {
    service.handleCommandIntent(new Intent());
    idle();

    assertEquals(1, service.getAudioId());
    assertFalse(service.isPlaying());
  }

  @Test
  public void aForegroundStateIntentIsRemembered() {
    Intent intent = new Intent(MusicPlaybackService.FOREGROUND_STATE_CHANGED);
    intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, true);

    service.handleCommandIntent(intent);
    idle();

    assertTrue(service.isMusicActivityInForeground());
    intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, false);
    service.handleCommandIntent(intent);
    idle();
    assertFalse(service.isMusicActivityInForeground());
  }

  @Test
  public void aShutdownWhilePlayingIsRefusedUnlessForced() {
    sendCommand(MusicPlaybackService.CMDPLAY);

    send(MusicPlaybackService.SHUTDOWN_ACTION);
    assertTrue("a plain shutdown doesn't interrupt the music", service.isPlaying());

    Intent forced = new Intent(MusicPlaybackService.SHUTDOWN_ACTION);
    forced.putExtra("force", true);
    service.handleCommandIntent(forced);
    idle();
    assertFalse(service.isPlaying());
  }

  @Test
  public void commandsAreDroppedBeforeThePlayerThreadExists() throws Exception {
    ApolloServiceFixture.noService();

    service.handleCommandIntent(new Intent(MusicPlaybackService.NEXT_ACTION));
    idle();

    assertEquals("nothing moved", 1, service.getAudioId());
  }

  @Test
  public void aPreviewPlaysAFileAndPlayingItAgainStopsIt() {
    service.playSimple("/storage/emulated/0/Music/a.mp3");
    long stopsBefore = stops();

    service.playSimple("/storage/emulated/0/Music/a.mp3");

    assertEquals("the same file again stops it", stopsBefore + 1, stops());
    assertTrue(lastStoppedPath().endsWith("a.mp3"));
  }

  @Test
  public void aNewPreviewReplacesTheOneThatWasPlaying() {
    service.playSimple("/a.mp3");

    service.playSimple("/b.mp3");

    assertEquals("the first preview was stopped", "/a.mp3", lastStoppedPath());
    service.stopSimplePlayer();
    assertEquals("/b.mp3", lastStoppedPath());
    service.stopSimplePlayer();
    assertEquals("a second stop has nothing to stop", "/b.mp3", lastStoppedPath());
  }

  @Test
  public void theIdOfThePlayingPreviewIsLookedUpInMediaStore() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.toString().contains("external") ? cursor(p, row("_id", 42L)) : cursor(p);
    assertEquals("nothing is previewing", -1, service.getCurrentSimplePlayerAudioId());

    service.playSimple("/a.mp3");

    assertEquals(42, service.getCurrentSimplePlayerAudioId());
    service.stopSimplePlayer();
  }

  private long stops() {
    return shadowOf((Application) ApplicationProvider.getApplicationContext())
        .getBroadcastIntents()
        .stream()
        .filter(i -> MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED.equals(i.getAction()))
        .count();
  }

  private String lastStoppedPath() {
    List<Intent> intents =
        shadowOf((Application) ApplicationProvider.getApplicationContext()).getBroadcastIntents();
    String path = null;
    for (Intent i : intents) {
      if (MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED.equals(i.getAction())) {
        path = i.getStringExtra("path");
      }
    }
    return path;
  }
}
