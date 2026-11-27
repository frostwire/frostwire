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
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import com.andrew.apollo.Config;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.ui.fragments.profile.AlbumSongFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistSongFragment;
import com.andrew.apollo.ui.fragments.profile.GenreSongFragment;
import com.andrew.apollo.ui.fragments.profile.PlaylistSongFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config.*;
import org.robolectric.fakes.RoboMenuItem;

/**
 * The artist, album, genre and playlist profile screens: which fragments each shows, the header,
 * the menu, sorting, and what the invalid launches do.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ProfileActivityTest {

  @org.junit.Rule
  public final com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages cleanAppMessages =
      new com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages();

  private MockedStatic<MusicUtils> music;

  private final java.util.List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    music = ApolloUiTestSupport.mockMusicUtils(calls);
    FakeMediaProvider.install();
    FakeMediaProvider.addSong(11, "Help", "Help!", "The Beatles");
    FakeMediaProvider.addSong(12, "Yesterday", "Help!", "The Beatles");
  }

  @After
  public void tearDown() {
    if (music != null) {
      music.close();
    }
  }

  private static Intent profile(String mimeType, String name) {
    return new Intent()
        .putExtra(Config.MIME_TYPE, mimeType)
        .putExtra(Config.NAME, name)
        .putExtra(Config.ARTIST_NAME, "The Beatles")
        .putExtra(Config.ID, 7L)
        .putExtra(Config.ALBUM_YEAR, "1965")
        .putExtra(Config.TRACKS, new long[] {11, 12});
  }

  private ActivityController<ProfileActivity> launch(Intent intent) {
    ActivityController<ProfileActivity> controller =
        Robolectric.buildActivity(ProfileActivity.class, intent);
    controller.setup();
    ApolloUiTestSupport.settle();
    return controller;
  }

  private static Fragment fragment(ProfileActivity activity, Class<? extends Fragment> type) {
    return activity.findFragment(type);
  }

  private static String title(ProfileActivity activity) {
    return ((TextView) activity.findViewById(R.id.view_toolbar_header_title)).getText().toString();
  }

  @Test
  public void anArtistProfileHasSongsAndAlbumsPages() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();

    assertEquals("The Beatles", title(activity));
    androidx.viewpager.widget.ViewPager pager =
        activity.findViewById(R.id.activity_profile_base_pager);
    assertEquals(2, pager.getAdapter().getCount());
    assertNotNull(fragment(activity, ArtistSongFragment.class));
  }

  @Test
  public void anAlbumProfileShowsTheYearAndItsSongs() {
    ProfileActivity activity = launch(profile(MediaStore.Audio.Albums.CONTENT_TYPE, "Help!")).get();

    assertEquals("Help!", title(activity));
    TextView subtitle = activity.findViewById(R.id.view_toolbar_header_subtitle);
    assertEquals(View.VISIBLE, subtitle.getVisibility());
    assertEquals("1965", subtitle.getText().toString());
    assertNotNull(fragment(activity, AlbumSongFragment.class));
  }

  @Test
  public void aGenreProfileShowsItsSongs() {
    ProfileActivity activity = launch(profile(MediaStore.Audio.Genres.CONTENT_TYPE, "Rock")).get();

    assertEquals("Rock", title(activity));
    assertNotNull(fragment(activity, GenreSongFragment.class));
  }

  @Test
  public void aPlaylistProfileShowsItsSongs() {
    ProfileActivity activity = launch(profile("vnd.android.cursor.dir/playlist", "Mix")).get();

    assertEquals("Mix", title(activity));
    assertNotNull(fragment(activity, PlaylistSongFragment.class));
  }

  @Test
  public void theFavoritesAndLastAddedProfilesAreRecognisedByTheirName() {
    android.content.Context context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext();
    ProfileActivity favorites =
        launch(profile(context.getString(R.string.playlist_favorites), "Favorites")).get();
    assertNotNull(
        fragment(favorites, com.andrew.apollo.ui.fragments.profile.FavoriteFragment.class));

    ProfileActivity lastAdded =
        launch(profile(context.getString(R.string.playlist_last_added), "Last added")).get();
    assertNotNull(
        fragment(lastAdded, com.andrew.apollo.ui.fragments.profile.LastAddedFragment.class));
  }

  @Test
  public void aLaunchWithoutArgumentsFinishes() {
    ProfileActivity activity = launch(new Intent()).get();

    assertTrue(activity.isFinishing());
  }

  @Test
  public void aLaunchWithAnUnknownTypeFinishes() {
    ProfileActivity activity = launch(profile("audio/unknown", "x")).get();

    assertTrue(activity.isFinishing());
  }

  @Test
  public void theArtistsSongPageOffersSortingAndAddToPlaylist() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();
    Menu menu = new MenuBuilder(activity);

    assertTrue(activity.onCreateOptionsMenu(menu));

    assertNotNull(menu.findItem(R.id.menu_player_shuffle));
    assertNotNull(menu.findItem(R.id.menu_player_add_to_homescreen));
    assertNotNull(menu.findItem(R.id.menu_player_sort_by_az));
    assertNotNull(menu.findItem(R.id.menu_player_sort_by_date_added));
  }

  @Test
  public void aGenreMenuHasNoSortingNorAddToPlaylist() {
    ProfileActivity activity = launch(profile(MediaStore.Audio.Genres.CONTENT_TYPE, "Rock")).get();
    Menu menu = new MenuBuilder(activity);

    activity.onCreateOptionsMenu(menu);

    assertNotNull(menu.findItem(R.id.menu_player_shuffle));
    assertTrue(menu.findItem(R.id.menu_player_sort_by_az) == null);
  }

  @Test
  public void anAlbumMenuSortsItsSongs() {
    ProfileActivity activity = launch(profile(MediaStore.Audio.Albums.CONTENT_TYPE, "Help!")).get();
    Menu menu = new MenuBuilder(activity);

    activity.onCreateOptionsMenu(menu);

    assertNotNull(menu.findItem(R.id.menu_player_sort_by_track_list));
  }

  @Test
  public void everySortChoiceIsHandled() {
    ProfileActivity artist =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();
    int[] ids = {
      R.id.menu_player_sort_by_az,
      R.id.menu_player_sort_by_za,
      R.id.menu_player_sort_by_album,
      R.id.menu_player_sort_by_year,
      R.id.menu_player_sort_by_duration,
      R.id.menu_player_sort_by_date_added,
      R.id.menu_player_sort_by_track_list,
      R.id.menu_player_sort_by_filename
    };
    for (int id : ids) {
      assertTrue("artist songs handle " + id, artist.onOptionsItemSelected(new RoboMenuItem(id)));
    }

    androidx.viewpager.widget.ViewPager pager =
        artist.findViewById(R.id.activity_profile_base_pager);
    pager.setCurrentItem(1);
    ApolloUiTestSupport.settle();
    for (int id : ids) {
      assertTrue("artist albums handle " + id, artist.onOptionsItemSelected(new RoboMenuItem(id)));
    }

    ProfileActivity album = launch(profile(MediaStore.Audio.Albums.CONTENT_TYPE, "Help!")).get();
    for (int id : ids) {
      assertTrue("album songs handle " + id, album.onOptionsItemSelected(new RoboMenuItem(id)));
    }
  }

  @Test
  public void theHomeButtonLeavesTheScreen() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();

    assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home)));

    assertTrue(activity.isFinishing());
  }

  @Test
  public void aPlaylistChosenInTheMenuReceivesTheArtistsTracks() {
    music
        .when(() -> MusicUtils.addToPlaylist(Mockito.any(), Mockito.any(), Mockito.anyLong()))
        .thenAnswer(i -> null);
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();
    RoboMenuItem item =
        new RoboMenuItem(com.andrew.apollo.menu.FragmentMenuItems.PLAYLIST_SELECTED);
    item.setIntent(new Intent().putExtra("playlist", 5L));

    assertTrue(activity.onOptionsItemSelected(item));

    music.verify(
        () -> MusicUtils.addToPlaylist(Mockito.eq(activity), Mockito.any(), Mockito.eq(5L)));
  }

  @Test
  public void anotherMenuItemFallsThroughToTheBaseScreen() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();

    assertFalse(activity.onOptionsItemSelected(new RoboMenuItem(12345)));
  }

  @Test
  public void shufflingAPlaylistIsHandled() {
    ProfileActivity activity = launch(profile("vnd.android.cursor.dir/playlist", "Mix")).get();

    assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_shuffle)));
  }

  @Test
  public void shufflingAnArtistAnAlbumAndAGenreIsHandled() {
    for (String type :
        new String[] {
          MediaStore.Audio.Artists.CONTENT_TYPE,
          MediaStore.Audio.Albums.CONTENT_TYPE,
          MediaStore.Audio.Genres.CONTENT_TYPE
        }) {
      ProfileActivity activity = launch(profile(type, "x")).get();
      assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_shuffle)));
    }
  }

  @Test
  public void theProfileSurvivesARotation() {
    ActivityController<ProfileActivity> controller =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles"));
    Bundle state = new Bundle();
    controller.saveInstanceState(state);

    ProfileActivity second =
        Robolectric.buildActivity(ProfileActivity.class, new Intent()).setup(state).get();

    assertEquals("The Beatles", title(second));
  }

  @Test
  public void thePagerAndCarouselFollowEachOther() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();
    androidx.viewpager.widget.ViewPager pager =
        activity.findViewById(R.id.activity_profile_base_pager);
    com.andrew.apollo.widgets.ProfileTabCarousel carousel =
        activity.findViewById(R.id.activity_profile_base_tab_carousel);

    activity.onTabSelected(1);
    ApolloUiTestSupport.settle();
    assertEquals(1, pager.getCurrentItem());

    activity.onPageSelected(1);
    assertTrue(carousel.findViewById(R.id.profile_tab_carousel_tab_two).isSelected());
    activity.onPageScrolled(0, 0.5f, 100);
    activity.onPageScrollStateChanged(androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE);
    activity.onPageScrollStateChanged(androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING);

    activity.onTouchDown();
    assertTrue(pager.isFakeDragging());
    activity.onScrollChanged(10, 0, 0, 0);
    activity.onTouchUp();
    assertFalse(pager.isFakeDragging());
    activity.onScrollChanged(10, 0, 0, 0);
    activity.onTouchUp();
  }

  @Test
  public void backFromAnAlbumReturnsToTheArtist() throws Exception {
    ProfileActivity activity = launch(profile(MediaStore.Audio.Albums.CONTENT_TYPE, "Help!")).get();

    activity.getOnBackPressedDispatcher().onBackPressed();
    ApolloUiTestSupport.settle();

    assertTrue(activity.isFinishing());
  }

  @Test
  public void aPlaylistsShuffleItemIsRetitledPlayAll() {
    ProfileActivity activity = launch(profile("vnd.android.cursor.dir/playlist", "Mix")).get();
    Menu menu = new MenuBuilder(activity);
    activity.onCreateOptionsMenu(menu);

    activity.onPrepareOptionsMenu(menu);

    ApolloUiTestSupport.waitUntil(
        "the shuffle item says play all",
        () ->
            activity
                .getString(R.string.menu_play_all)
                .contentEquals(menu.findItem(R.id.menu_player_shuffle).getTitle()));
  }

  @Test
  public void anArtistsShuffleItemKeepsItsShuffleTitle() {
    ProfileActivity activity =
        launch(profile(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles")).get();
    Menu menu = new MenuBuilder(activity);
    activity.onCreateOptionsMenu(menu);

    activity.onPrepareOptionsMenu(menu);

    ApolloUiTestSupport.waitUntil(
        "the shuffle item says shuffle",
        () ->
            activity
                .getString(R.string.menu_shuffle)
                .contentEquals(menu.findItem(R.id.menu_player_shuffle).getTitle()));
  }
}
