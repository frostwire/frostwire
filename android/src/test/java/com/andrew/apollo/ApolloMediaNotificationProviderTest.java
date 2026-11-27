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
import android.app.Notification;
import android.os.Bundle;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.Constants;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The lock screen and notification shade card for the music that is playing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ApolloMediaNotificationProviderTest {

  private ApolloServiceFixture fixture;
  private MediaSession session;
  private ApolloMediaNotificationProvider provider;
  private MediaNotification.ActionFactory actions;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    session =
        new MediaSession.Builder(ApplicationProvider.getApplicationContext(), fixture.player)
            .setId("NotificationProviderTest")
            .build();
    provider = new ApolloMediaNotificationProvider(fixture.service);
    actions = Mockito.mock(MediaNotification.ActionFactory.class);
    Mockito.when(
            actions.createMediaAction(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
        .thenAnswer(i -> new NotificationCompat.Action.Builder(0, i.getArgument(2), null).build());
  }

  @After
  public void tearDown() throws Exception {
    session.release();
    fixture.stop();
  }

  private MediaNotification create() {
    return provider.createNotification(
        session, ImmutableList.<CommandButton>of(), actions, notification -> {});
  }

  private void queue(String title) {
    fixture.player.setMediaItem(
        new MediaItem.Builder()
            .setMediaId("1")
            .setUri("file:///a.mp3")
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist("Ann").build())
            .build());
    fixture.player.addMediaItem(
        new MediaItem.Builder().setMediaId("2").setUri("file:///b.mp3").build());
    fixture.player.prepare();
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void theCardShowsTheSongAndArtistWithPreviousPlayAndNextButtons() {
    queue("Hey");

    MediaNotification notification = create();

    assertEquals(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification.notificationId);
    Notification card = notification.notification;
    assertEquals("Hey", card.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    assertEquals("Ann", card.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    assertEquals(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, card.getChannelId());
    assertEquals(3, card.actions.length);
    assertEquals(Notification.CATEGORY_TRANSPORT, card.category);
    assertEquals(Notification.VISIBILITY_PUBLIC, card.visibility);
    assertFalse(
        "music can be swiped away when paused",
        (card.flags & Notification.FLAG_ONGOING_EVENT) != 0);
  }

  @Test
  public void thePlayButtonShowsWhilePausedAndThePauseButtonWhilePlaying() {
    queue("Hey");
    String paused = create().notification.actions[1].title.toString();

    fixture.player.play();
    shadowOf(Looper.getMainLooper()).idle();
    String playing = create().notification.actions[1].title.toString();

    assertFalse("the label changes with the state", paused.equals(playing));
  }

  @Test
  public void aPausedSongShowsNoChronometerAndNoTimestamp() {
    queue("Hey");

    Notification paused = create().notification;

    assertEquals(0L, paused.when);
    assertFalse(paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
  }

  @Test
  public void withNothingQueuedTheCardHasNoTransportButtonsToSeekWith() {
    MediaNotification notification = create();

    assertNull(notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE));
    assertTrue(
        notification.notification.actions == null || notification.notification.actions.length < 3);
  }

  @Test
  public void customCommandsAreNotHandledAndTheChannelIsFrostWires() {
    assertFalse(provider.handleCustomCommand(session, "x", new Bundle()));
    assertNotNull(provider.getNotificationChannelInfo());
  }
}
