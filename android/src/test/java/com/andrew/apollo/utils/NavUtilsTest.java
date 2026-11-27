/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.SearchManager;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Looper;
import android.provider.MediaStore;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.Config;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.HomeActivity;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.ui.activities.SearchActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/** Where the Apollo screens send the user: profiles, the player, search, effects and home. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class NavUtilsTest {

  private ActivityController<Activity> controller;
  private Activity activity;

  @Before
  public void setUp() throws Exception {
    ApolloServiceFixture.installPlatform(true);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
    controller = Robolectric.buildActivity(Activity.class).setup();
    activity = controller.get();
  }

  @After
  public void tearDown() throws Exception {
    FakeConfig.uninstall();
  }

  /** The profile is built on a background thread and started from the main looper. */
  private Intent awaitStartedActivity() {
    long deadline = System.nanoTime() + 10_000_000_000L;
    Intent intent = shadowOf(activity).getNextStartedActivity();
    while (intent == null && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      intent = shadowOf(activity).getNextStartedActivity();
      Thread.onSpinWait();
    }
    return intent;
  }

  @Test
  public void anArtistProfileOpensWithTheArtistsIdNameAndSongs() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 14L));

    NavUtils.openArtistProfile(activity, "Ann", new long[] {1, 2});
    Intent intent = awaitStartedActivity();

    assertNotNull("the profile opened", intent);
    assertEquals(ProfileActivity.class.getName(), intent.getComponent().getClassName());
    assertEquals(14, intent.getLongExtra(Config.ID, -1));
    assertEquals("Ann", intent.getStringExtra(Config.ARTIST_NAME));
    assertEquals(MediaStore.Audio.Artists.CONTENT_TYPE, intent.getStringExtra(Config.MIME_TYPE));
    assertEquals(2, intent.getLongArrayExtra(Config.TRACKS).length);
  }

  @Test
  public void anArtistProfileWithoutSongsCarriesNoTrackList() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 14L));

    NavUtils.openArtistProfile(activity, "Ann", null);
    Intent intent = awaitStartedActivity();

    assertNotNull(intent);
    assertNull(intent.getLongArrayExtra(Config.TRACKS));
  }

  @Test
  public void aProfileWithoutAnArtistNameOrForAFinishedActivityDoesNotOpen() {
    NavUtils.openArtistProfile(activity, "", null);
    NavUtils.openArtistProfile(null, "Ann", null);
    NavUtils.openAlbumProfile(null, "LP", "Ann", 1, null);
    controller.pause().stop().destroy();
    NavUtils.openArtistProfile(activity, "Ann", null);
    NavUtils.openAlbumProfile(activity, "LP", "Ann", 1, null);
    shadowOf(Looper.getMainLooper()).idle();

    assertNull(shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void anAlbumProfileOpensWithTheAlbumsYearArtistAndSongs() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("minyear", "1999"));

    NavUtils.openAlbumProfile(activity, "LP", "Ann", 5, new long[] {9});
    Intent intent = awaitStartedActivity();

    assertNotNull(intent);
    assertEquals("1999", intent.getStringExtra(Config.ALBUM_YEAR));
    assertEquals("Ann", intent.getStringExtra(Config.ARTIST_NAME));
    assertEquals("LP", intent.getStringExtra(Config.NAME));
    assertEquals(5, intent.getLongExtra(Config.ID, -1));
    assertEquals(MediaStore.Audio.Albums.CONTENT_TYPE, intent.getStringExtra(Config.MIME_TYPE));
    assertEquals(1, intent.getLongArrayExtra(Config.TRACKS).length);
  }

  @Test
  public void anAlbumProfileWithoutSongsCarriesNoTrackList() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    NavUtils.openAlbumProfile(activity, "LP", "Ann", 5, new long[0]);
    Intent intent = awaitStartedActivity();

    assertNotNull(intent);
    assertNull(intent.getLongArrayExtra(Config.TRACKS));
  }

  @Test
  public void thePlayerOpensSingleTopInANewTask() {
    NavUtils.openAudioPlayer(activity);

    Intent intent = shadowOf(activity).getNextStartedActivity();
    assertEquals(AudioPlayerActivity.class.getName(), intent.getComponent().getClassName());
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  public void searchOpensWithTheQuery() {
    NavUtils.openSearch(activity, "queen");

    Intent intent = shadowOf(activity).getNextStartedActivity();
    assertEquals(SearchActivity.class.getName(), intent.getComponent().getClassName());
    assertEquals("queen", intent.getStringExtra(SearchManager.QUERY));
  }

  @Test
  public void goingHomeClearsTheTaskAndFinishesTheActivity() {
    NavUtils.goHome(activity);

    Intent intent = shadowOf(activity).getNextStartedActivity();
    assertEquals(HomeActivity.class.getName(), intent.getComponent().getClassName());
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    assertTrue(activity.isFinishing());
  }

  @Test
  public void theEffectsPanelOpensForTheAudioSession() {
    NavUtils.openEffectsPanel(activity);

    Intent intent = shadowOf(activity).getNextStartedActivity();
    assertEquals(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL, intent.getAction());
    assertEquals(-1, intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0));
  }
}
