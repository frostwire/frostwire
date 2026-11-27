/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.viewpager.widget.ViewPager;
import com.andrew.apollo.Config;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.HomeActivity;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config.*;

/**
 * The six tabs of the music browser (last added, playlists, recent, artists, songs, albums) inside
 * the home screen, and the menu the home screen offers for each.
 */
@SuppressWarnings("deprecation") // MediaStore.Audio.Playlists, how a playlist is described
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class MusicBrowserFragmentsTest {

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
    FakeMediaProvider.addLibraryRow();
    music = ApolloUiTestSupport.mockMusicUtils(calls);
  }

  @After
  public void tearDown() {
    music.close();
    PreferenceUtils prefs = PreferenceUtils.getInstance();
    prefs.setArtistLayout("simple");
    prefs.setAlbumLayout("simple");
    prefs.setRecentLayout("simple");
  }

  private HomeActivity launch() {
    HomeActivity activity = Robolectric.buildActivity(HomeActivity.class).setup().get();
    ApolloUiTestSupport.settle();
    while (shadowOf(activity).getNextStartedActivity() != null) {
      // intents started while the screen came up are not what the tests look for
    }
    return activity;
  }

  private static <F extends ApolloFragment<?, ?>> F page(HomeActivity activity, Class<F> type) {
    F fragment = type.cast(activity.findFragment(type));
    assertNotNull(type.getSimpleName() + " is on a page", fragment);
    return fragment;
  }

  private static void show(HomeActivity activity, int position) {
    ViewPager pager = activity.findViewById(R.id.fragment_home_phone_pager);
    pager.setCurrentItem(position);
    ApolloUiTestSupport.settle();
  }

  private static void rows(ApolloFragment<?, ?> fragment, int count) {
    ApolloUiTestSupport.waitUntil(
        count + " rows in " + fragment.getClass().getSimpleName(),
        () -> fragment.getAdapter() != null && fragment.getAdapter().getCount() >= count);
  }

  private static AbsListView listOf(ApolloFragment<?, ?> fragment) {
    View root = fragment.getView();
    AbsListView list = root.findViewById(R.id.list_base);
    return list != null ? list : root.findViewById(R.id.grid_base);
  }

  private static Menu newContextMenu(Context context) {
    try {
      return (Menu)
          Class.forName("com.android.internal.view.menu.ContextMenuBuilder")
              .getConstructor(Context.class)
              .newInstance(context);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static Menu contextMenuFor(ApolloFragment<?, ?> fragment, int position) {
    AbsListView list = listOf(fragment);
    Menu menu = newContextMenu(fragment.requireContext());
    View row = list.getAdapter().getView(position, null, list);
    fragment.onCreateContextMenu(
        (ContextMenu) menu, list, new AdapterView.AdapterContextMenuInfo(row, position, position));
    return menu;
  }

  private static Intent nextStarted(HomeActivity activity) {
    ApolloUiTestSupport.waitUntil(
        "an activity starts", () -> shadowOf(activity).peekNextStartedActivity() != null);
    return shadowOf(activity).getNextStartedActivity();
  }

  @Test
  public void allSixTabsAreOnTheBrowser() {
    HomeActivity activity = launch();

    assertNotNull(page(activity, LastAddedFragment.class));
    assertNotNull(page(activity, PlaylistFragment.class));
    assertNotNull(page(activity, RecentFragment.class));
    assertNotNull(page(activity, ArtistFragment.class));
    assertNotNull(page(activity, SongFragment.class));
    assertNotNull(page(activity, AlbumFragment.class));
  }

  @Test
  public void tappingASongPlaysItAndOpensThePlayer() {
    HomeActivity activity = launch();
    SongFragment songs = page(activity, SongFragment.class);
    rows(songs, 1);

    listOf(songs).performItemClick(null, 0, 1);

    ApolloUiTestSupport.waitUntil(
        "the song plays " + calls, () -> calls.contains("playFDs[[1]0 false]"));
    assertEquals(
        AudioPlayerActivity.class.getName(), nextStarted(activity).getComponent().getClassName());
  }

  @Test
  public void tappingAnArtistOpensItsProfile() {
    HomeActivity activity = launch();
    ArtistFragment artists = page(activity, ArtistFragment.class);
    rows(artists, 1);

    listOf(artists).performItemClick(null, 0, 1);

    Intent profile = nextStarted(activity);
    assertEquals(ProfileActivity.class.getName(), profile.getComponent().getClassName());
    assertEquals("The Beatles", profile.getStringExtra(Config.ARTIST_NAME));
  }

  @Test
  public void tappingAnAlbumOpensItsProfile() {
    HomeActivity activity = launch();
    AlbumFragment albums = page(activity, AlbumFragment.class);
    rows(albums, 1);

    listOf(albums).performItemClick(null, 0, 5);

    Intent profile = nextStarted(activity);
    assertEquals(ProfileActivity.class.getName(), profile.getComponent().getClassName());
    assertEquals("Help!", profile.getStringExtra(Config.NAME));
  }

  @Test
  public void theLastAddedTabPlaysASongWhenTapped() {
    HomeActivity activity = launch();
    LastAddedFragment lastAdded = page(activity, LastAddedFragment.class);
    rows(lastAdded, 1);

    listOf(lastAdded).performItemClick(null, 0, 1);

    ApolloUiTestSupport.waitUntil(
        "the song plays " + calls, () -> calls.contains("playFDs[[1]0 false]"));
  }

  @Test
  public void anEmptyRecentTabShowsItsMessage() {
    HomeActivity activity = launch();
    RecentFragment recent = page(activity, RecentFragment.class);

    ApolloUiTestSupport.waitUntil(
        "the empty message",
        () -> {
          android.widget.TextView empty = recent.getView().findViewById(R.id.empty);
          return empty != null
              && activity.getString(R.string.empty_recent).contentEquals(empty.getText());
        });
  }

  @Test
  public void theRecentTabsContextMenuCanRemoveASong() {
    HomeActivity activity = launch();
    RecentFragment recent = page(activity, RecentFragment.class);
    recent
        .getAdapter()
        .add(new com.andrew.apollo.model.Song(1, "Help", "The Beatles", "Help!", 180));
    recent
        .getAdapter()
        .setDataList(
            List.of(new com.andrew.apollo.model.Song(1, "Help", "The Beatles", "Help!", 180)));
    recent.getAdapter().notifyDataSetChanged();
    Menu menu = contextMenuFor(recent, 0);
    MenuItem remove = menu.findItem(FragmentMenuItems.REMOVE_FROM_RECENT);

    assertNotNull(remove);
    assertTrue(recent.onContextItemSelected(remove));
    ApolloUiTestSupport.settle();
  }

  @Test
  public void thePlaylistTabStartsWithTheThreeFixedEntries() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);

    assertEquals(
        activity.getString(R.string.new_empty_playlist),
        playlists.getAdapter().getItem(0).mPlaylistName);
    assertEquals(
        activity.getString(R.string.playlist_favorites),
        playlists.getAdapter().getItem(1).mPlaylistName);
    assertEquals(
        activity.getString(R.string.playlist_last_added),
        playlists.getAdapter().getItem(2).mPlaylistName);
    assertEquals("Mix", playlists.getAdapter().getItem(3).mPlaylistName);
  }

  @Test
  public void tappingAPlaylistEntryOpensItsProfile() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);

    listOf(playlists).performItemClick(null, 0, 0);
    Intent create = nextStarted(activity);
    assertEquals(
        activity.getString(R.string.new_empty_playlist), create.getStringExtra(Config.NAME));

    listOf(playlists).performItemClick(null, 1, 0);
    Intent favorites = nextStarted(activity);
    assertEquals(
        activity.getString(R.string.playlist_favorites),
        favorites.getStringExtra(Config.MIME_TYPE));

    listOf(playlists).performItemClick(null, 2, 0);
    Intent lastAdded = nextStarted(activity);
    assertEquals(
        activity.getString(R.string.playlist_last_added),
        lastAdded.getStringExtra(Config.MIME_TYPE));

    listOf(playlists).performItemClick(null, 3, 0);
    Intent mix = nextStarted(activity);
    assertEquals("Mix", mix.getStringExtra(Config.NAME));
    assertEquals(MediaStore.Audio.Playlists.CONTENT_TYPE, mix.getStringExtra(Config.MIME_TYPE));
  }

  @Test
  public void aUserPlaylistsContextMenuCanPlayQueueRenameAndDelete() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);
    Menu menu = contextMenuFor(playlists, 3);

    assertNotNull(menu.findItem(FragmentMenuItems.PLAY_SELECTION));
    assertNotNull(menu.findItem(FragmentMenuItems.ADD_TO_QUEUE));
    assertNotNull(menu.findItem(FragmentMenuItems.RENAME_PLAYLIST));
    assertNotNull(menu.findItem(FragmentMenuItems.DELETE));
    assertTrue(playlists.onContextItemSelected(menu.findItem(FragmentMenuItems.PLAY_SELECTION)));
    assertTrue(playlists.onContextItemSelected(menu.findItem(FragmentMenuItems.ADD_TO_QUEUE)));
    assertTrue(playlists.onContextItemSelected(menu.findItem(FragmentMenuItems.RENAME_PLAYLIST)));
    assertTrue(playlists.onContextItemSelected(menu.findItem(FragmentMenuItems.DELETE)));
    ApolloUiTestSupport.settle();
  }

  @Test
  public void theFixedPlaylistsOnlyOfferWhatMakesSense() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);

    Menu create = contextMenuFor(playlists, 0);
    assertTrue(create.findItem(FragmentMenuItems.PLAY_SELECTION) == null);

    Menu favorites = contextMenuFor(playlists, 1);
    assertNotNull(favorites.findItem(FragmentMenuItems.PLAY_SELECTION));
    assertTrue(favorites.findItem(FragmentMenuItems.RENAME_PLAYLIST) == null);
    assertTrue(favorites.findItem(FragmentMenuItems.DELETE) == null);
    assertTrue(
        playlists.onContextItemSelected(favorites.findItem(FragmentMenuItems.PLAY_SELECTION)));
    assertTrue(playlists.onContextItemSelected(favorites.findItem(FragmentMenuItems.ADD_TO_QUEUE)));

    Menu lastAdded = contextMenuFor(playlists, 2);
    assertTrue(
        playlists.onContextItemSelected(lastAdded.findItem(FragmentMenuItems.PLAY_SELECTION)));
    assertTrue(playlists.onContextItemSelected(lastAdded.findItem(FragmentMenuItems.ADD_TO_QUEUE)));
  }

  @Test
  public void anItemOfAnotherGroupIsLeftToTheBaseClass() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);
    MenuItem foreign = newContextMenu(activity).add(9999, 1, 0, "foreign");

    assertFalse(playlists.onContextItemSelected(foreign));
  }

  @Test
  public void aPlaylistCanBeDeletedFromItsDialog() {
    HomeActivity activity = launch();
    PlaylistFragment playlists = page(activity, PlaylistFragment.class);
    rows(playlists, 4);
    Menu menu = contextMenuFor(playlists, 3);
    playlists.onContextItemSelected(menu.findItem(FragmentMenuItems.DELETE));
    ApolloUiTestSupport.settle();

    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();

    assertNotNull(dialog);
    dialog.findViewById(R.id.dialog_default_button_no).performClick();
    ApolloUiTestSupport.settle();
  }

  @Test
  public void theGridAndDetailedLayoutsListTheSameArtistsAndAlbums() {
    for (String layout : new String[] {"grid", "detailed"}) {
      PreferenceUtils.getInstance().setArtistLayout(layout);
      PreferenceUtils.getInstance().setAlbumLayout(layout);
      HomeActivity activity = launch();
      ArtistFragment artists = page(activity, ArtistFragment.class);
      AlbumFragment albums = page(activity, AlbumFragment.class);
      rows(artists, 1);
      rows(albums, 1);
      assertEquals("The Beatles", artists.getAdapter().getItem(0).mArtistName);
      assertEquals("Help!", albums.getAdapter().getItem(0).mAlbumName);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The menu the home screen offers for each tab

  private static Menu menuFor(HomeActivity activity, int position) {
    show(activity, position);
    activity.invalidateOptionsMenu();
    Menu menu = new MenuBuilder(activity);
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);
    activity.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu);
    return menu;
  }

  private static boolean choose(HomeActivity activity, Menu menu, int id) {
    MenuItem item = menu.findItem(id);
    assertNotNull("menu item " + id, item);
    return activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
  }

  @Test
  public void thePlaylistTabOffersANewPlaylistAndShuffle() {
    HomeActivity activity = launch();

    Menu menu = menuFor(activity, TabFragmentOrder.PLAYLISTS_POSITION);

    assertNotNull(menu.findItem(R.id.menu_player_new_playlist));
    assertNotNull(menu.findItem(R.id.menu_player_shuffle));
    assertTrue(choose(activity, menu, R.id.menu_player_shuffle));
    assertTrue(calls.toString(), calls.contains("shuffleAll[]"));
  }

  @Test
  public void theSongTabOffersFavoritesAndEverySortOrder() {
    HomeActivity activity = launch();

    Menu menu = menuFor(activity, TabFragmentOrder.SONGS_POSITION);

    assertNotNull(menu.findItem(R.id.menu_player_favorite));
    assertTrue(choose(activity, menu, R.id.menu_player_favorite));
    assertTrue(calls.toString(), calls.contains("toggleFavorite[]"));
    for (int id :
        new int[] {
          R.id.menu_player_sort_by_az,
          R.id.menu_player_sort_by_za,
          R.id.menu_player_sort_by_artist,
          R.id.menu_player_sort_by_album,
          R.id.menu_player_sort_by_year,
          R.id.menu_player_sort_by_duration,
          R.id.menu_player_sort_by_filename
        }) {
      assertTrue("songs sort " + id, choose(activity, menu, id));
    }
  }

  @Test
  public void theArtistTabOffersItsSortOrdersAndViewAs() {
    HomeActivity activity = launch();

    Menu menu = menuFor(activity, TabFragmentOrder.ARTISTS_POSITION);

    for (int id :
        new int[] {
          R.id.menu_player_sort_by_az,
          R.id.menu_player_sort_by_za,
          R.id.menu_player_sort_by_number_of_songs,
          R.id.menu_player_sort_by_number_of_albums
        }) {
      assertTrue("artists sort " + id, choose(activity, menu, id));
    }
  }

  @Test
  public void theAlbumTabOffersItsSortOrders() {
    HomeActivity activity = launch();

    Menu menu = menuFor(activity, TabFragmentOrder.ALBUMS_POSITION);

    for (int id :
        new int[] {
          R.id.menu_player_sort_by_az,
          R.id.menu_player_sort_by_za,
          R.id.menu_player_sort_by_artist,
          R.id.menu_player_sort_by_year,
          R.id.menu_player_sort_by_number_of_songs
        }) {
      assertTrue("albums sort " + id, choose(activity, menu, id));
    }
  }

  @Test
  public void changingTheViewAsOfATabReloadsTheHomeScreen() {
    for (int page :
        new int[] {
          TabFragmentOrder.RECENT_POSITION,
          TabFragmentOrder.ARTISTS_POSITION,
          TabFragmentOrder.ALBUMS_POSITION
        }) {
      HomeActivity activity = launch();
      Menu menu = menuFor(activity, page);
      if (menu.findItem(R.id.menu_player_view_as_simple) == null) {
        continue;
      }
      assertTrue(choose(activity, menu, R.id.menu_player_view_as_simple));
      assertTrue(choose(activity, menu, R.id.menu_player_view_as_detailed));
      assertTrue(choose(activity, menu, R.id.menu_player_view_as_grid));
    }
  }

  @Test
  public void anItemNobodyOwnsIsNotHandled() {
    HomeActivity activity = launch();
    Menu menu = menuFor(activity, TabFragmentOrder.SONGS_POSITION);
    MenuItem other = menu.add(0, 12345, 0, "other");

    assertFalse(activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, other));
  }

  @Test
  public void theBrowserRemembersTheTabItWasLeftOn() {
    org.robolectric.android.controller.ActivityController<HomeActivity> controller =
        Robolectric.buildActivity(HomeActivity.class).setup();
    ApolloUiTestSupport.settle();
    show(controller.get(), TabFragmentOrder.ALBUMS_POSITION);

    controller.pause();

    assertEquals(TabFragmentOrder.ALBUMS_POSITION, PreferenceUtils.getInstance().getStartPage());
    PreferenceUtils.getInstance().setStartPage(TabFragmentOrder.SONGS_POSITION);
  }
}
