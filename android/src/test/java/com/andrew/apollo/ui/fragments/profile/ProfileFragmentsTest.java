/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

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
import android.widget.AdapterView;
import android.widget.ListView;
import androidx.fragment.app.Fragment;
import com.andrew.apollo.Config;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.utils.MusicUtils;
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

/**
 * The song and album lists of the profile screens (the {@code ApolloFragment} family): loading
 * rows, tapping a row, the context menu and every action on it. No playback service runs here: the
 * calls that would reach it are recorded in {@link #calls}.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ProfileFragmentsTest {

  /** The first item sits below the header row of the profile lists. */
  private static final int FIRST = 1;

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
    FakeMediaProvider.addSong(11, "Help", "Help!", "The Beatles");
    FakeMediaProvider.addSong(12, "Yesterday", "Help!", "The Beatles");
    music = ApolloUiTestSupport.mockMusicUtils(calls);
  }

  @After
  public void tearDown() {
    music.close();
  }

  private static Intent profile(String mimeType, String name) {
    return new Intent()
        .putExtra(Config.MIME_TYPE, mimeType)
        .putExtra(Config.NAME, name)
        .putExtra(Config.ARTIST_NAME, "The Beatles")
        .putExtra(Config.ID, 7L)
        .putExtra(Config.TRACKS, new long[] {11, 12});
  }

  private ProfileActivity launch(String mimeType, String name) {
    ProfileActivity activity =
        Robolectric.buildActivity(ProfileActivity.class, profile(mimeType, name)).setup().get();
    ApolloUiTestSupport.settle();
    return activity;
  }

  @SuppressWarnings("unchecked")
  private static <F extends ApolloFragment<?, ?>> F fragment(
      ProfileActivity activity, Class<F> type) {
    return (F) activity.findFragment((Class<? extends Fragment>) type);
  }

  /** The number of items listed, without the header row some of the lists add. */
  private static int size(ApolloFragment<?, ?> fragment) {
    int count = fragment.getAdapter().getCount();
    return count == 0 ? 0 : count - fragment.getAdapter().getOffset();
  }

  private static void rows(ApolloFragment<?, ?> fragment, int count) {
    ApolloUiTestSupport.waitUntil(
        count + " rows", () -> fragment.getAdapter() != null && size(fragment) == count);
  }

  private static ListView listOf(ApolloFragment<?, ?> fragment) {
    return fragment.getView().findViewById(R.id.list_base);
  }

  /** The framework's own context menu, which is not public API. */
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
    ListView list = listOf(fragment);
    Menu menu = newContextMenu(fragment.requireContext());
    View row = list.getAdapter().getView(position, null, list);
    fragment.onCreateContextMenu(
        (ContextMenu) menu, list, new AdapterView.AdapterContextMenuInfo(row, position, position));
    return menu;
  }

  private static boolean select(ApolloFragment<?, ?> fragment, Menu menu, int id) {
    MenuItem item = menu.findItem(id);
    assertNotNull("menu item " + id, item);
    return fragment.onContextItemSelected(item);
  }

  /** An item in the group of the fragment's own context menu items. */
  private static MenuItem ownItem(Menu menu, int id) {
    MenuItem own = menu.findItem(FragmentMenuItems.PLAY_SELECTION);
    return menu.add(own.getGroupId(), id, 0, "item " + id);
  }

  private void waitForCall(String call) {
    ApolloUiTestSupport.waitUntil("the call " + call + " in " + calls, () -> calls.contains(call));
  }

  @Test
  public void aSongPageListsTheSongsAndAnEmptyOneShowsItsMessage() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    assertEquals("Help", songs.getAdapter().getItem(0).mSongName);

    FakeMediaProvider.clear();
    songs.refresh();
    ApolloUiTestSupport.waitUntil("the list empties", () -> size(songs) == 0);
    assertEquals(
        activity.getString(R.string.empty_music), songs.mEmptyTextView.getText().toString());
  }

  @Test
  public void tappingASongPlaysTheListFromThatSongAndOpensThePlayer() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);

    listOf(songs).performItemClick(null, FIRST + 1, 12);

    waitForCall("playFDs[[11, 12]1 false]");
    ApolloUiTestSupport.waitUntil(
        "the player opens", () -> shadowOf(activity).peekNextStartedActivity() != null);
    assertEquals(
        AudioPlayerActivity.class.getName(),
        shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
  }

  @Test
  public void theContextMenuOffersTheSongActions() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);

    Menu menu = contextMenuFor(songs, FIRST);

    assertNotNull(menu.findItem(FragmentMenuItems.PLAY_SELECTION));
    assertNotNull(menu.findItem(FragmentMenuItems.PLAY_NEXT));
    assertNotNull(menu.findItem(FragmentMenuItems.ADD_TO_QUEUE));
    assertNotNull(menu.findItem(FragmentMenuItems.ADD_TO_FAVORITES));
    assertNotNull(menu.findItem(FragmentMenuItems.ADD_TO_PLAYLIST));
    assertNotNull(menu.findItem(FragmentMenuItems.MORE_BY_ARTIST));
    assertNotNull(menu.findItem(FragmentMenuItems.DELETE));
  }

  @Test
  public void playingTheSelectionPlaysJustThatSong() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST + 1);

    assertTrue(select(songs, menu, FragmentMenuItems.PLAY_SELECTION));

    waitForCall("playFDs[[12]0 false]");
  }

  @Test
  public void playNextAndAddToQueueUseTheSelectedSong() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);

    assertTrue(select(songs, menu, FragmentMenuItems.PLAY_NEXT));
    assertTrue(select(songs, menu, FragmentMenuItems.ADD_TO_QUEUE));

    waitForCall("playNext[[11]]");
    waitForCall("addToQueue[[11]]");
  }

  @Test
  public void addingToFavoritesConfirmsOnTheScreen() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);

    assertTrue(select(songs, menu, FragmentMenuItems.ADD_TO_FAVORITES));

    ApolloUiTestSupport.waitUntil(
        "the confirmation shows", () -> activity.findViewById(android.R.id.message) != null);
  }

  @Test
  public void addingToFavoritesAnEmptiedLibraryConfirmsNothing() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);
    FakeMediaProvider.clear();
    int before = FakeMediaProvider.QUERIES.size();

    assertTrue(select(songs, menu, FragmentMenuItems.ADD_TO_FAVORITES));

    ApolloUiTestSupport.waitUntil(
        "the songs are looked up", () -> FakeMediaProvider.QUERIES.size() > before);
    ApolloUiTestSupport.settle();
    assertEquals(null, activity.findViewById(android.R.id.message));
  }

  @Test
  public void aNewPlaylistFromTheSelectionAsksForItsName() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);
    int fragments = songs.getParentFragmentManager().getFragments().size();

    assertTrue(select(songs, menu, FragmentMenuItems.NEW_PLAYLIST));

    ApolloUiTestSupport.waitUntil(
        "the name dialog is added",
        () -> songs.getParentFragmentManager().getFragments().size() > fragments);
  }

  @Test
  public void deletingTheSelectionAsksForConfirmation() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);

    assertTrue(select(songs, menu, FragmentMenuItems.DELETE));

    ApolloUiTestSupport.waitUntil(
        "the confirmation dialog is added",
        () -> songs.getParentFragmentManager().findFragmentByTag("DeleteDialog") != null);
  }

  @Test
  public void moreByArtistOpensTheArtistProfile() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);

    assertTrue(select(songs, menu, FragmentMenuItems.MORE_BY_ARTIST));

    ApolloUiTestSupport.waitUntil(
        "the artist profile opens", () -> shadowOf(activity).peekNextStartedActivity() != null);
    Intent next = shadowOf(activity).getNextStartedActivity();
    assertEquals(ProfileActivity.class.getName(), next.getComponent().getClassName());
    assertEquals("The Beatles", next.getStringExtra(Config.ARTIST_NAME));
  }

  @Test
  public void anItemOfAnotherGroupIsLeftToTheBaseClass() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = newContextMenu(songs.requireContext());
    MenuItem foreign = menu.add(9999, 1, 0, "foreign");

    assertFalse(songs.onContextItemSelected(foreign));
  }

  @Test
  public void removingFromFavoritesTakesTheSongOffTheList() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);
    com.andrew.apollo.model.Song removed = songs.getAdapter().getItem(0);

    assertTrue(songs.onContextItemSelected(ownItem(menu, FragmentMenuItems.REMOVE_FROM_FAVORITES)));

    assertEquals("the song left the adapter", -1, songs.getAdapter().getPosition(removed));
  }

  @Test
  public void anAlbumsSongsAreListedAndPlayedByTapping() {
    ProfileActivity activity = launch(MediaStore.Audio.Albums.CONTENT_TYPE, "Help!");
    AlbumSongFragment songs = fragment(activity, AlbumSongFragment.class);
    rows(songs, 2);

    listOf(songs).performItemClick(null, FIRST, 11);

    waitForCall("playFDs[[11, 12]0 false]");
  }

  @Test
  public void aGenresSongsAreListedAndPlayedByTapping() {
    ProfileActivity activity = launch(MediaStore.Audio.Genres.CONTENT_TYPE, "Rock");
    GenreSongFragment songs = fragment(activity, GenreSongFragment.class);
    rows(songs, 2);

    listOf(songs).performItemClick(null, FIRST, 11);

    waitForCall("playFDs[[11, 12]0 false]");
  }

  @Test
  public void aPlaylistsSongsCanBeReorderedAndRemoved() {
    ProfileActivity activity = launch("vnd.android.cursor.dir/playlist", "Mix");
    PlaylistSongFragment songs = fragment(activity, PlaylistSongFragment.class);
    rows(songs, 2);

    songs.drop(1, 2);
    assertEquals("Yesterday", songs.getAdapter().getItem(0).mSongName);
    songs.drop(0, 2);
    songs.drop(1, 1);
    songs.remove(1);

    assertEquals(1, size(songs));
    assertEquals(0.0f, songs.getSpeed(0f, 0), 0.01f);
    assertTrue(songs.getSpeed(0.9f, 0) > 0f);
    assertTrue(
        calls.toString(), calls.stream().anyMatch(c -> c.startsWith("removePlaylistMember")));
  }

  @Test
  public void aPlaylistsContextMenuCanRemoveASong() {
    ProfileActivity activity = launch("vnd.android.cursor.dir/playlist", "Mix");
    PlaylistSongFragment songs = fragment(activity, PlaylistSongFragment.class);
    rows(songs, 2);
    Menu menu = contextMenuFor(songs, FIRST);
    com.andrew.apollo.model.Song removed = songs.getAdapter().getItem(0);

    assertTrue(select(songs, menu, FragmentMenuItems.REMOVE_FROM_PLAYLIST));

    assertEquals(-1, songs.getAdapter().getPosition(removed));
  }

  @Test
  public void anArtistsAlbumsAreListedAndTappingOneClosesTheArtistScreen() {
    FakeMediaProvider.clear();
    FakeMediaProvider.addRow(
        "_id", 5L, "album", "Help!", "artist", "The Beatles", "numsongs", 14, "minyear", "1965");
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    androidx.viewpager.widget.ViewPager pager =
        activity.findViewById(R.id.activity_profile_base_pager);
    pager.setCurrentItem(1);
    ApolloUiTestSupport.settle();
    ArtistAlbumFragment albums = fragment(activity, ArtistAlbumFragment.class);
    rows(albums, 1);

    listOf(albums).performItemClick(null, FIRST, 5);
    ApolloUiTestSupport.waitUntil("the artist screen closes", activity::isFinishing);
  }

  @Test
  public void theCurrentSongIsScrolledTo() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(12L);

    songs.scrollToCurrentSong();

    assertTrue(listOf(songs).getFirstVisiblePosition() >= 0);
  }

  @Test
  public void aMetaChangeRestartsTheLoaderOnlyOnceInAWhile() {
    ProfileActivity activity = launch(MediaStore.Audio.Artists.CONTENT_TYPE, "The Beatles");
    ArtistSongFragment songs = fragment(activity, ArtistSongFragment.class);
    rows(songs, 2);

    songs.onMetaChanged();
    songs.restartLoader();
    ApolloUiTestSupport.settle();

    assertEquals(2, size(songs));
  }

  @Test
  public void theFavoritesProfileListsTheFavoritesAndCanRemoveOne() {
    com.andrew.apollo.provider.FavoritesStore.getInstance(
            androidx.test.core.app.ApplicationProvider.getApplicationContext())
        .addSongId(11L, "Help", "Help!", "The Beatles");
    ProfileActivity activity =
        launch(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
                .getString(R.string.playlist_favorites),
            "Favorites");
    FavoriteFragment favorites = fragment(activity, FavoriteFragment.class);
    rows(favorites, 1);

    listOf(favorites).performItemClick(null, FIRST, 11);
    waitForCall("playFDs[[11]0 false]");

    Menu menu = contextMenuFor(favorites, FIRST);
    for (int i = 0; i < menu.size(); i++) {
      assertFalse(
          "no top level add to favorites on the favorites list",
          menu.getItem(i).getItemId() == FragmentMenuItems.ADD_TO_FAVORITES);
    }
    assertNotNull(menu.findItem(FragmentMenuItems.REMOVE_FROM_FAVORITES));
    favorites.onMetaChanged();
    assertTrue(
        favorites.onContextItemSelected(menu.findItem(FragmentMenuItems.REMOVE_FROM_FAVORITES)));
  }

  @Test
  public void theLastAddedProfileListsTheNewestSongsOrSaysThereAreNone() {
    ProfileActivity activity =
        launch(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
                .getString(R.string.playlist_last_added),
            "Last added");
    LastAddedFragment lastAdded = fragment(activity, LastAddedFragment.class);
    rows(lastAdded, 2);
    listOf(lastAdded).performItemClick(null, FIRST, 11);
    waitForCall("playFDs[[11, 12]0 false]");

    FakeMediaProvider.clear();
    lastAdded.refresh();
    ApolloUiTestSupport.waitUntil("the list empties", () -> size(lastAdded) == 0);
    ApolloUiTestSupport.waitUntil(
        "the empty message",
        () -> {
          android.widget.TextView empty = lastAdded.getView().findViewById(R.id.empty);
          return empty != null
              && activity.getString(R.string.empty_last_added).contentEquals(empty.getText());
        });
  }
}
