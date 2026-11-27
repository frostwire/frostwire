/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

/**
 * The music player's home screen and its base class: the browser fragment, the notification
 * permission flow, the bottom bar that follows playback and the broadcasts that refresh it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class HomeActivityTest {

  private MockedStatic<MusicUtils> music;

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
  }

  @After
  public void tearDown() {
    if (music != null) {
      music.close();
    }
  }

  private ActivityController<HomeActivity> launch() {
    ActivityController<HomeActivity> controller = Robolectric.buildActivity(HomeActivity.class);
    controller.setup();
    shadowOf(Looper.getMainLooper()).idle();
    while (shadowOf(controller.get()).getNextStartedActivity() != null) {
      // intents started while the screen came up are not what the tests look for
    }
    return controller;
  }

  /** A player that is playing "Song" by "Artist". */
  private void playing() {
    music = Mockito.mockStatic(MusicUtils.class, Mockito.CALLS_REAL_METHODS);
    music.when(MusicUtils::isStopped).thenReturn(false);
    music.when(MusicUtils::isPlaying).thenReturn(true);
    music.when(MusicUtils::getTrackName).thenReturn("Song");
    music.when(MusicUtils::getArtistName).thenReturn("Artist");
    music.when(MusicUtils::getAlbumName).thenReturn("Album");
    music.when(MusicUtils::getCurrentAudioId).thenReturn(7L);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(70L);
  }

  private static AlertDialog latestDialog() {
    return (AlertDialog) ShadowDialog.getLatestDialog();
  }

  private static String titleOf(AlertDialog dialog) {
    return ((TextView) dialog.findViewById(androidx.appcompat.R.id.alertTitle))
        .getText()
        .toString();
  }

  @Test
  public void theBrowserFragmentIsShownAndTheInstanceTracksTheLifecycle() {
    ActivityController<HomeActivity> controller = launch();
    HomeActivity activity = controller.get();

    assertSame(activity, HomeActivity.instance());
    assertNotNull(
        activity.getSupportFragmentManager().findFragmentById(R.id.activity_base_content));
    assertTrue(
        activity.getSupportFragmentManager().findFragmentById(R.id.activity_base_content)
            instanceof MusicBrowserPhoneFragment);
    assertEquals(
        activity.getString(R.string.my_music),
        ((TextView) activity.findViewById(R.id.view_toolbar_header_title)).getText().toString());

    controller.pause().stop().destroy();

    assertNull(HomeActivity.instance());
  }

  @Test
  public void aRecreatedActivityKeepsTheFragmentItAlreadyHas() {
    ActivityController<HomeActivity> controller = launch();
    android.os.Bundle state = new android.os.Bundle();
    controller.saveInstanceState(state);

    HomeActivity second = Robolectric.buildActivity(HomeActivity.class).setup(state).get();

    assertNotNull(second.getSupportFragmentManager().findFragmentById(R.id.activity_base_content));
  }

  @Test
  public void theNotificationPermissionIsAskedForOnLaunch() {
    HomeActivity activity = launch().get();

    assertEquals(
        Manifest.permission.POST_NOTIFICATIONS,
        shadowOf(activity).getLastRequestedPermission().requestedPermissions[0]);
  }

  @Test
  public void aGrantedNotificationPermissionAsksNothingMore() {
    shadowOf((Application) ApplicationProvider.getApplicationContext())
        .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

    HomeActivity activity = launch().get();

    assertNull(shadowOf(activity).getLastRequestedPermission());
  }

  @Test
  public void aGrantedAnswerShowsNoDialog() {
    HomeActivity activity = launch().get();

    activity.onRequestPermissionsResult(
        DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        new int[] {PackageManager.PERMISSION_GRANTED});

    assertNull(latestDialog());
  }

  @Test
  public void anAnswerToAnotherRequestIsIgnored() {
    HomeActivity activity = launch().get();

    activity.onRequestPermissionsResult(
        12345, new String[] {"x"}, new int[] {PackageManager.PERMISSION_DENIED});

    assertNull(latestDialog());
  }

  @Test
  public void aDeniedAnswerWithARationaleExplainsAndCanAskAgain() {
    HomeActivity activity = launch().get();
    shadowOf(activity.getPackageManager())
        .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);

    activity.onRequestPermissionsResult(
        DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        new int[] {PackageManager.PERMISSION_DENIED});

    AlertDialog dialog = latestDialog();
    assertNotNull(dialog);
    assertEquals("Notification Permission Required", titleOf(dialog));
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertNotNull("Allow asks again", shadowOf(activity).getLastRequestedPermission());
  }

  @Test
  public void denyingTheRationaleTellsTheUser() {
    HomeActivity activity = launch().get();
    shadowOf(activity.getPackageManager())
        .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);

    activity.onRequestPermissionsResult(
        DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        new int[] {PackageManager.PERMISSION_DENIED});
    latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(
        "Permission denied. The app may close in the background.",
        ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void aPermanentDenialSendsTheUserToTheAppSettings() {
    HomeActivity activity = launch().get();

    activity.onRequestPermissionsResult(
        DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        new int[0]);

    AlertDialog dialog = latestDialog();
    assertEquals("Permission Required", titleOf(dialog));
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    Intent settings = shadowOf(activity).getNextStartedActivity();
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.getAction());
    assertEquals("package:" + activity.getPackageName(), settings.getData().toString());
  }

  @Test
  public void cancellingTheSettingsRedirectTellsTheUser() {
    HomeActivity activity = launch().get();

    activity.onRequestPermissionsResult(
        DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        new int[] {PackageManager.PERMISSION_DENIED});
    latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(
        "Notification permission denied. Some features may not work properly.",
        ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void backWhilePlayingLeavesTheMusicAlone() {
    playing();
    HomeActivity activity = launch().get();

    activity.handleOnBackPressed();

    assertFalse(activity.isFinishing());
  }

  @Test
  public void backWhileIdleGoesThroughTheExitOffer() {
    HomeActivity activity = launch().get();

    activity.handleOnBackPressed();

    shadowOf(Looper.getMainLooper()).idle();
    assertNotNull(activity);
  }

  // ---------------------------------------------------------------------------------------------
  // BaseActivity

  @Test
  public void anIdlePlayerHidesTheBottomBar() {
    HomeActivity activity = launch().get();

    assertEquals(View.GONE, activity.findViewById(R.id.bottom_action_bar_parent).getVisibility());
  }

  @Test
  public void aPlayingPlayerShowsTheBottomBarWithTheTrack() {
    playing();
    HomeActivity activity = launch().get();

    assertEquals(
        View.VISIBLE, activity.findViewById(R.id.bottom_action_bar_parent).getVisibility());
    assertEquals(
        "Song",
        ((TextView) activity.findViewById(R.id.bottom_action_bar_line_one)).getText().toString());
    assertEquals(
        "Artist",
        ((TextView) activity.findViewById(R.id.bottom_action_bar_line_two)).getText().toString());
  }

  @Test
  public void tappingTheBottomBarOpensTheNowPlayingScreen() {
    playing();
    HomeActivity activity = launch().get();

    activity.findViewById(R.id.bottom_action_bar).performClick();

    Intent next = shadowOf(activity).getNextStartedActivity();
    assertNotNull(next);
    assertEquals(AudioPlayerActivity.class.getName(), next.getComponent().getClassName());
  }

  @Test
  public void tappingTheBottomBarWithNothingPlayingShufflesEverything() {
    playing();
    music.when(MusicUtils::getCurrentAudioId).thenReturn(-1L);
    music.when(() -> MusicUtils.shuffleAll(Mockito.any())).thenAnswer(i -> null);
    HomeActivity activity = launch().get();

    activity.findViewById(R.id.bottom_action_bar).performClick();

    music.verify(() -> MusicUtils.shuffleAll(activity));
  }

  @Test
  public void longPressingPlayStopsThePlayerAndHidesTheBar() {
    playing();
    HomeActivity activity = launch().get();

    assertTrue(activity.findViewById(R.id.action_button_play).performLongClick());

    assertEquals(View.GONE, activity.findViewById(R.id.bottom_action_bar_parent).getVisibility());
  }

  @Test
  public void tappingTheAlbumArtWithNothingPlayingShufflesEverything() {
    playing();
    music.when(MusicUtils::getCurrentAudioId).thenReturn(-1L);
    music.when(() -> MusicUtils.shuffleAll(Mockito.any())).thenAnswer(i -> null);
    HomeActivity activity = launch().get();

    activity.findViewById(R.id.bottom_action_bar_album_art).performClick();

    music.verify(() -> MusicUtils.shuffleAll(activity));
  }

  @Test
  public void tappingTheAlbumArtOpensTheAlbumProfile() throws Exception {
    playing();
    music
        .when(() -> MusicUtils.getSongListForAlbum(Mockito.any(), Mockito.eq(70L)))
        .thenReturn(new long[] {7});
    HomeActivity activity = launch().get();

    activity.findViewById(R.id.bottom_action_bar_album_art).performClick();

    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    Intent next = shadowOf(activity).getNextStartedActivity();
    while (next == null && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(5);
      next = shadowOf(activity).getNextStartedActivity();
    }
    assertNotNull("the profile screen opens", next);
    assertEquals(ProfileActivity.class.getName(), next.getComponent().getClassName());
  }

  @Test
  public void playbackBroadcastsRefreshTheBarAndTellTheListeners() {
    playing();
    HomeActivity activity = launch().get();
    final int[] counts = new int[2];
    activity.setMusicStateListenerListener(
        new com.andrew.apollo.MusicStateListener() {
          @Override
          public void restartLoader() {
            counts[0]++;
          }

          @Override
          public void onMetaChanged() {
            counts[1]++;
          }
        });
    music.when(MusicUtils::getTrackName).thenReturn("Next song");

    activity.sendBroadcast(new Intent(MusicPlaybackService.META_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.REFRESH));
    activity.sendBroadcast(new Intent(MusicPlaybackService.PLAYSTATE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.REPEATMODE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.SHUFFLEMODE_CHANGED));
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(1, counts[0]);
    assertEquals(1, counts[1]);
    assertEquals(
        "Next song",
        ((TextView) activity.findViewById(R.id.bottom_action_bar_line_one)).getText().toString());
  }

  @Test
  public void aRemovedListenerHearsNothingMore() {
    HomeActivity activity = launch().get();
    final int[] counts = new int[1];
    com.andrew.apollo.MusicStateListener listener =
        new com.andrew.apollo.MusicStateListener() {
          @Override
          public void restartLoader() {
            counts[0]++;
          }

          @Override
          public void onMetaChanged() {
            counts[0]++;
          }
        };
    activity.setMusicStateListenerListener(listener);
    activity.setMusicStateListenerListener(listener);
    activity.setMusicStateListenerListener(null);
    activity.removeMusicStateListener(listener);

    activity.sendBroadcast(new Intent(MusicPlaybackService.REFRESH));
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(0, counts[0]);
  }

  @Test
  public void thePlayerStillRefreshesWhenResumedAfterPlaybackStarts() {
    ActivityController<HomeActivity> controller = launch();
    playing();

    controller.pause().resume();

    assertEquals(
        View.VISIBLE, controller.get().findViewById(R.id.bottom_action_bar_parent).getVisibility());
    assertEquals(
        "Song",
        ((TextView) controller.get().findViewById(R.id.bottom_action_bar_line_one))
            .getText()
            .toString());
  }

  @Test
  public void leavingTheScreenReportsTheForegroundChange() {
    playing();
    music
        .when(() -> MusicUtils.notifyForegroundStateChanged(Mockito.any(), Mockito.anyBoolean()))
        .thenAnswer(i -> null);
    ActivityController<HomeActivity> controller = launch();

    controller.pause().stop();

    music.verify(() -> MusicUtils.notifyForegroundStateChanged(controller.get(), true));
    music.verify(() -> MusicUtils.notifyForegroundStateChanged(controller.get(), false));
  }

  @Test
  public void homeFinishesTheScreenWhenItIsNotTheTaskRoot() {
    HomeActivity activity = launch().get();
    android.view.MenuItem home = new org.robolectric.fakes.RoboMenuItem(android.R.id.home);

    assertTrue(activity.onOptionsItemSelected(home));

    assertTrue(activity.isFinishing() || shadowOf(activity).getNextStartedActivity() != null);
  }

  @Test
  public void theSearchMenuInflatesAndSubmittingASearchOpensIt() {
    HomeActivity activity = launch().get();
    android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(activity);

    assertTrue(activity.onCreateOptionsMenu(menu));

    androidx.appcompat.widget.SearchView searchView =
        (androidx.appcompat.widget.SearchView)
            menu.findItem(R.id.menu_player_search).getActionView();
    assertNotNull(searchView);
    searchView.setQuery("beatles", true);
    Intent search = shadowOf(activity).getNextStartedActivity();
    assertNotNull(search);
    assertEquals(SearchActivity.class.getName(), search.getComponent().getClassName());
  }
}
