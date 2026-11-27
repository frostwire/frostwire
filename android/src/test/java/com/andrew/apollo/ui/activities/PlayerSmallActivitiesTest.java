/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config.*;

/** The notification's entry into the player and the long press that stops the music. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class PlayerSmallActivitiesTest {

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    music = ApolloUiTestSupport.mockMusicUtils(calls);
  }

  @After
  public void tearDown() {
    music.close();
  }

  @Test
  public void theNotificationOpensTheMainScreenWithThePlayerOnTop() {
    AudioPlayerNotificationActivity activity =
        Robolectric.buildActivity(AudioPlayerNotificationActivity.class).setup().get();

    assertTrue(activity.isFinishing());
    java.util.List<String> started = new java.util.ArrayList<>();
    Intent next;
    while ((next = shadowOf(activity).getNextStartedActivity()) != null) {
      started.add(next.getComponent().getClassName());
    }
    assertTrue(started.toString(), started.contains(AudioPlayerActivity.class.getName()));
    assertTrue(started.toString(), started.contains(MainActivity.class.getName()));
  }

  @Test
  public void theNotificationsPendingIntentOpensTheNotificationActivity() {
    PendingIntent pending =
        AudioPlayerNotificationActivity.createPendingIntent(
            ApplicationProvider.getApplicationContext());

    assertNotNull(pending);
    assertEquals(
        AudioPlayerNotificationActivity.class.getName(),
        shadowOf(pending).getSavedIntent().getComponent().getClassName());
  }

  @Test
  public void aLongPressStopsTheServiceAnnouncesItAndLeavesTheScreen() {
    MusicPlaybackService service = Mockito.mock(MusicPlaybackService.class);
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(service);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View button = new View(activity);
    activity.setContentView(button);

    assertTrue(new StopListener(activity, true).onLongClick(button));

    Mockito.verify(service).stop();
    assertTrue(
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getBroadcastIntents()
            .stream()
            .anyMatch(i -> Constants.ACTION_MEDIA_PLAYER_STOPPED.equals(i.getAction())));
  }

  @Test
  public void aLongPressWithoutAServiceStillAnnouncesTheStop() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View button = new View(activity);

    assertTrue(new StopListener(activity, false).onLongClick(button));

    assertTrue(!activity.isFinishing());
  }

  @Test
  public void aLongPressAfterTheScreenIsGoneStillStopsTheMusic() {
    MusicPlaybackService service = Mockito.mock(MusicPlaybackService.class);
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(service);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View button = new View(activity);
    StopListener listener = new StopListener(activity, true);
    activity.finish();
    System.gc();

    assertTrue(listener.onLongClick(button));

    Mockito.verify(service).stop();
  }

  @Test
  public void aServiceThatFailsToStopDoesNotBreakThePress() {
    MusicPlaybackService service = Mockito.mock(MusicPlaybackService.class);
    Mockito.doThrow(new IllegalStateException("not running")).when(service).stop();
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(service);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();

    assertTrue(new StopListener(activity, false).onLongClick(new View(activity)));
  }
}
