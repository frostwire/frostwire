/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.adapters;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicViewHolder;
import com.frostwire.android.R;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Apollo list adapters: what each row shows, the cache the rows are built from, and how the
 * rows ask for their artwork.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ApolloListAdaptersTest {

  private Activity activity;
  private ImageFetcher fetcher;
  private FrameLayout parent;

  @Before
  public void setUp() throws Exception {
    ApolloServiceFixture.installPlatform(false);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
    fetcher = mock(ImageFetcher.class);
    setFetcher(fetcher);
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    parent = new FrameLayout(activity);
  }

  @After
  public void tearDown() throws Exception {
    setFetcher(null);
    FakeConfig.uninstall();
  }

  private static void setFetcher(ImageFetcher value) throws Exception {
    Field field = ImageFetcher.class.getDeclaredField("sInstance");
    field.setAccessible(true);
    field.set(null, value);
  }

  private static TextView text(View row, int id) {
    return row.findViewById(id);
  }

  // --- SongAdapter ---

  private SongAdapter songs(Song... songs) {
    SongAdapter adapter = new SongAdapter(activity, R.layout.list_item_simple_image);
    adapter.setDataList(List.of(songs));
    adapter.buildCache();
    return adapter;
  }

  @Test
  public void songsWithoutADurationAreLeftOutOfTheList() {
    SongAdapter adapter =
        songs(new Song(1, "Short", "Ann", "LP", 0), new Song(2, "Fine", "Ann", "LP", 65));

    assertEquals(1, adapter.getCount());
    assertEquals(2, adapter.getItemId(0));
    assertEquals(-1, adapter.getItemId(5));
    assertEquals(1, adapter.getViewTypeCount());
    assertEquals(0, adapter.getOffset());
    adapter.setDataList(null);
    assertEquals(0, adapter.getCount());
  }

  @Test
  public void aSongRowShowsTitleArtistAndDurationAndAsksForItsAlbumArt() {
    Song song = new Song(2, "Fine", "Ann", "LP", 65);
    SongAdapter adapter = songs(song);
    adapter.mData[0].mParentId = 9;

    View row = adapter.getView(0, null, parent);

    assertEquals("Fine", text(row, R.id.line_one).getText().toString());
    assertEquals("Ann", text(row, R.id.line_two).getText().toString());
    assertEquals("1:05", text(row, R.id.line_one_right).getText().toString().trim());
    assertEquals(View.GONE, text(row, R.id.line_three).getVisibility());
    verify(fetcher).loadAlbumImage(eq("Ann"), eq("Fine"), eq(9L), any(ImageView.class));
    assertSame("a recycled row is reused", row, adapter.getView(0, row, parent));
  }

  @Test
  public void aSongWhoseAlbumIsUnknownLooksItUpOffTheMainThreadAndReloadsTheArt() {
    SongAdapter adapter = songs(new Song(2, "Fine", "Ann", "LP", 65));
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("album_id", 77L));

    adapter.getView(0, null, parent);
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (adapter.mData[0].mParentId == -1 && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.onSpinWait();
    }
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(77, adapter.mData[0].mParentId);
    verify(fetcher).loadAlbumImage(eq("Ann"), eq("Fine"), eq(-1L), any(ImageView.class));
    long until = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < until) {
      shadowOf(Looper.getMainLooper()).idle();
      try {
        verify(fetcher).loadAlbumImage(eq("Ann"), eq("Fine"), eq(77L), any(ImageView.class));
        break;
      } catch (AssertionError notYet) {
        Thread.onSpinWait();
      }
    }
    verify(fetcher).loadAlbumImage(eq("Ann"), eq("Fine"), eq(77L), any(ImageView.class));
  }

  @Test
  public void theSongThatIsPlayingHasItsRowHighlighted() throws Exception {
    ApolloServiceFixture fixture = ApolloServiceFixture.start();
    ApolloServiceFixture.installPlatform(false);
    try {
      fixture.service.open(new long[] {2, 3}, 0);
      shadowOf(Looper.getMainLooper()).idle();
      SongAdapter adapter =
          songs(new Song(2, "Playing", "Ann", "LP", 65), new Song(3, "Next", "Ann", "LP", 65));
      adapter.mData[0].mParentId = 1;
      adapter.mData[1].mParentId = 1;

      View playing = adapter.getView(0, null, parent);
      View other = adapter.getView(1, null, parent);

      assertTrue(
          "the playing row uses another color",
          text(playing, R.id.line_one).getCurrentTextColor()
              != text(other, R.id.line_one).getCurrentTextColor());
    } finally {
      fixture.stop();
    }
  }

  @Test
  public void aSongRowWithoutADataHolderIsStillBuilt() {
    SongAdapter adapter = songs(new Song(2, "Fine", "Ann", "LP", 65));
    adapter.mData[0] = null;

    assertNotNull(adapter.getView(0, null, parent));
  }

  @Test
  public void songsCanBeInsertedAndRemovedKeepingTheDataList() {
    SongAdapter adapter = songs(new Song(1, "A", "x", "y", 10));
    Song extra = new Song(2, "B", "x", "y", 10);

    adapter.insert(extra, 0);
    assertEquals(2, adapter.getCount());
    adapter.remove(extra);
    assertEquals(1, adapter.getCount());
  }

  @Test
  public void aSongAdapterOverAnApplicationContextHasNoImageFetcher() {
    SongAdapter adapter =
        new SongAdapter(activity.getApplicationContext(), R.layout.list_item_simple_image, 3);
    adapter.setDataList(List.of(new Song(1, "A", "x", "y", 10)));
    adapter.buildCache();
    adapter.mData[0].mParentId = 1;

    assertNull(adapter.mImageFetcher);
    assertEquals(3, adapter.getOffset());
    assertNotNull(adapter.getView(0, null, parent));
    verify(fetcher, never()).loadAlbumImage(any(), any(), anyLong(), any());
  }

  // --- ApolloFragmentAdapter (through the song adapter) ---

  @Test
  public void itemsAreRemovedByIdFromEveryKindOfList() {
    SongAdapter songs = songs(new Song(1, "A", "x", "y", 10), new Song(2, "B", "x", "y", 10));
    songs.removeItemsById(new long[] {1});
    assertEquals(1, songs.getCount());
    assertEquals(2, songs.getItemId(0));
    songs.removeItemsById(null);
    songs.removeItemsById(new long[0]);
    songs.removeItemsById(new long[] {99});
    assertEquals(1, songs.getCount());

    AlbumAdapter albums = new AlbumAdapter(activity, R.layout.list_item_normal);
    albums.setDataList(
        List.of(new Album(1, "LP", "Ann", 3, "1999"), new Album(2, "EP", "Ann", 1, "2000")));
    albums.buildCache();
    albums.removeItemsById(new long[] {2});
    assertEquals(1, albums.getCount());

    ArtistAdapter artists = new ArtistAdapter(activity, R.layout.list_item_normal);
    artists.setDataList(List.of(new Artist(1, "Ann", 3, 1)));
    artists.buildCache();
    artists.removeItemsById(new long[] {1});
    assertEquals(0, artists.getCount());

    PlaylistAdapter playlists = new PlaylistAdapter(activity, R.layout.list_item_simple);
    playlists.setDataList(List.of(new Playlist(1, "Road"), new Playlist(2, "Gym")));
    playlists.buildCache();
    playlists.removeItemsById(new long[] {1});
    assertEquals(1, playlists.getCount());
  }

  @Test
  public void unloadingEmptiesTheAdapterAndItsCache() {
    SongAdapter adapter = songs(new Song(1, "A", "x", "y", 10));

    adapter.unload();

    assertEquals(0, adapter.getCount());
    assertNull(adapter.mData);
  }

  @Test
  public void aGenreIsRemovedByItsIdToo() {
    SongAdapter adapter = songs(new Song(1, "A", "x", "y", 10));
    @SuppressWarnings({"rawtypes", "unchecked"})
    ApolloFragmentAdapter<Genre> genres =
        new ApolloFragmentAdapter<Genre>(activity, R.layout.list_item_simple, 0) {};
    genres.setDataList(List.of(new Genre(4, "Rock"), new Genre(5, "Jazz")));
    assertEquals(4, genres.getItemId(0));

    genres.removeItemsById(new long[] {4});

    assertEquals(1, genres.mDataList.size());
    assertEquals(5, genres.getItemId(0));
    assertEquals(1, adapter.getCount());
  }

  @Test
  public void itemsOutsideTheListHaveNoIdAndNoItem() {
    ApolloFragmentAdapter<Genre> genres =
        new ApolloFragmentAdapter<Genre>(activity, R.layout.list_item_simple, 0) {};

    assertEquals(-1, genres.getItemId(-1));
    assertEquals(-1, genres.getItemId(0));
    assertNull(genres.getItem(-1));
    assertNull(genres.getItem(3));
    assertEquals("an adapter with no rows reserves a header", 1, genres.getOffset());
    assertTrue(genres.hasStableIds());
    genres.setLoadExtraData(true);
    genres.setDataList(null);
    assertTrue(genres.mDataList.isEmpty());
  }

  // --- AlbumAdapter ---

  private AlbumAdapter albums(int layout) {
    AlbumAdapter adapter = new AlbumAdapter(activity, layout);
    adapter.setDataList(List.of(new Album(7, "LP", "Ann", 11, "1999")));
    adapter.buildCache();
    return adapter;
  }

  @Test
  public void anAlbumRowShowsNameArtistAndLoadsItsArt() {
    AlbumAdapter adapter = albums(R.layout.list_item_normal);

    View row = adapter.getView(0, null, parent);

    assertEquals("LP", text(row, R.id.line_one).getText().toString());
    assertEquals("Ann", text(row, R.id.line_two).getText().toString());
    verify(fetcher).loadAlbumImage(eq("Ann"), eq("LP"), eq(7L), any(ImageView.class));
    assertEquals(7, adapter.getItemId(0));
    assertEquals(-1, adapter.getItemId(4));
    assertEquals(2, adapter.getViewTypeCount());
    assertTrue(adapter.hasStableIds());
    assertEquals(0, adapter.getOffset());
  }

  @Test
  public void anAlbumRowWithExtraDataShowsTheSongCountTheArtistArtAndPlaysOnClick() {
    AlbumAdapter adapter = albums(R.layout.list_item_detailed_no_background);
    adapter.setLoadExtraData(true);

    View row = adapter.getView(0, null, parent);

    assertEquals("11 songs", text(row, R.id.line_three).getText().toString());
    assertTrue(row.findViewById(R.id.list_item_image).hasOnClickListeners());
  }

  @Test
  public void aDetailedAlbumRowWithoutABackgroundDrawsNoArtistImage() {
    AlbumAdapter adapter = albums(R.layout.list_item_detailed_no_background);
    adapter.setLoadExtraData(true);

    adapter.getView(0, null, parent);

    verify(fetcher, never()).loadArtistImage(any(), any());
  }

  // --- ArtistAdapter ---

  @Test
  public void anArtistRowShowsTheNameAndCountsAndLoadsTheArtistImage() {
    ArtistAdapter adapter = new ArtistAdapter(activity, R.layout.list_item_detailed_no_background);
    adapter.setDataList(List.of(new Artist(3, "Ann", 14, 2)));
    adapter.buildCache();
    adapter.setLoadExtraData(true);

    View row = adapter.getView(0, null, parent);

    assertEquals("Ann", text(row, R.id.line_one).getText().toString());
    assertEquals("2 albums", text(row, R.id.line_two).getText().toString());
    assertEquals("14 songs", text(row, R.id.line_three).getText().toString());
    verify(fetcher).loadArtistImage(eq("Ann"), any(ImageView.class));
    assertEquals(3, adapter.getItemId(0));
    assertEquals(-1, adapter.getItemId(9));
    assertEquals(2, adapter.getViewTypeCount());
    assertEquals(0, adapter.getOffset());
    assertTrue(adapter.hasStableIds());
    assertTrue(row.findViewById(R.id.list_item_image).hasOnClickListeners());
  }

  @Test
  public void aDetailedArtistRowWithoutABackgroundSkipsTheBackgroundImage() {
    ArtistAdapter adapter = new ArtistAdapter(activity, R.layout.list_item_detailed_no_background);
    adapter.setDataList(List.of(new Artist(3, "Ann", 14, 2)));
    adapter.buildCache();
    adapter.setLoadExtraData(true);

    adapter.getView(0, null, parent);

    verify(fetcher).loadArtistImage(eq("Ann"), any(ImageView.class));
  }

  // --- PlaylistAdapter ---

  @Test
  public void thePlaylistListStartsWithTheNewPlaylistRowThenTheNames() {
    PlaylistAdapter adapter = new PlaylistAdapter(activity, R.layout.list_item_simple);
    adapter.setDataList(List.of(new Playlist(-3, "New"), new Playlist(5, "Road")));
    adapter.buildCache();

    View newRow = adapter.getView(0, null, parent);
    View road = adapter.getView(1, null, parent);

    assertNotNull(newRow);
    assertEquals("Road", text(road, R.id.line_one).getText().toString());
    assertEquals(View.GONE, text(road, R.id.line_two).getVisibility());
    assertEquals(View.GONE, text(road, R.id.line_three).getVisibility());
    assertSame(road, adapter.getView(1, road, parent));
    assertSame(newRow, adapter.getView(0, newRow, parent));
    assertEquals(5, adapter.getItemId(1));
    assertEquals(-1, adapter.getItemId(7));
    assertEquals(0, adapter.getItemViewType(0));
    assertEquals(1, adapter.getItemViewType(1));
    assertEquals(2, adapter.getViewTypeCount());
    assertTrue(adapter.hasStableIds());
    assertEquals(0, adapter.getOffset());
  }

  // --- ArtistAlbumAdapter ---

  @Test
  public void theArtistProfilesAlbumListHasAHeaderAndRowsWithSongCountAndYear() {
    ArtistAlbumAdapter adapter =
        new ArtistAlbumAdapter(activity, R.layout.list_item_detailed_no_background);
    adapter.setDataList(List.of(new Album(7, "LP", "Ann", 11, "1999")));

    View header = adapter.getView(0, null, parent);
    View row = adapter.getView(1, null, parent);

    assertNotNull(header);
    assertEquals("LP", text(row, R.id.line_one).getText().toString());
    assertEquals("11 songs", text(row, R.id.line_two).getText().toString());
    assertEquals("1999", text(row, R.id.line_three).getText().toString());
    verify(fetcher).loadAlbumImage(eq("Ann"), eq("LP"), eq(7L), any(ImageView.class));
    assertEquals(3, adapter.getViewTypeCount());
    assertEquals(ApolloFragmentAdapter.ITEM_VIEW_TYPE_HEADER, adapter.getItemViewType(0));
    assertEquals(ApolloFragmentAdapter.ITEM_VIEW_TYPE_MUSIC, adapter.getItemViewType(1));
    assertTrue(row.findViewById(R.id.list_item_image).hasOnClickListeners());
  }

  // --- ProfileSongAdapter ---

  private ProfileSongAdapter profile(int setting, Song song) {
    ProfileSongAdapter adapter =
        new ProfileSongAdapter(activity, R.layout.list_item_simple, setting);
    adapter.setDataList(List.of(song));
    return adapter;
  }

  @Test
  public void anAlbumProfileSongRowShowsTheTitleAndDurationAsASecondLine() {
    ProfileSongAdapter adapter =
        profile(ProfileSongAdapter.DISPLAY_ALBUM_SETTING, new Song(1, "Hey", "Ann", "LP", 65));

    View header = adapter.getView(0, null, parent);
    View row = adapter.getView(1, null, parent);

    assertNotNull(header);
    assertEquals("Hey", text(row, R.id.line_one).getText().toString());
    assertEquals("1:05", text(row, R.id.line_two).getText().toString().trim());
    assertEquals(View.GONE, text(row, R.id.line_one_right).getVisibility());
    assertEquals(View.GONE, text(row, R.id.line_three).getVisibility());
    assertSame(row, adapter.getView(1, row, parent));
  }

  @Test
  public void aPlaylistProfileSongRowShowsArtistAndAlbumAndHidesAnUnknownDuration() {
    ProfileSongAdapter withDuration =
        profile(ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING, new Song(1, "Hey", "Ann", "LP", 65));
    ProfileSongAdapter withoutDuration =
        profile(ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING, new Song(1, "Hey", "Ann", "LP", -1));

    View shown = withDuration.getView(1, null, parent);
    View hidden = withoutDuration.getView(1, null, parent);

    assertEquals("Ann - LP", text(shown, R.id.line_two).getText().toString());
    assertEquals(View.VISIBLE, text(shown, R.id.line_one_right).getVisibility());
    assertEquals("1:05", text(shown, R.id.line_one_right).getText().toString().trim());
    assertEquals(View.GONE, text(hidden, R.id.line_one_right).getVisibility());
  }

  @Test
  public void aDefaultProfileSongRowShowsTheAlbumAndTheDuration() {
    ProfileSongAdapter adapter =
        profile(ProfileSongAdapter.DISPLAY_DEFAULT_SETTING, new Song(1, "Hey", "Ann", "LP", 65));

    View row = adapter.getView(1, null, parent);

    assertEquals("LP", text(row, R.id.line_two).getText().toString());
    assertEquals(View.VISIBLE, text(row, R.id.line_one_right).getVisibility());
    assertEquals("1:05", text(row, R.id.line_one_right).getText().toString().trim());
    assertEquals(3, adapter.getViewTypeCount());
    assertEquals(ApolloFragmentAdapter.ITEM_VIEW_TYPE_HEADER, adapter.getItemViewType(0));
    assertEquals(ApolloFragmentAdapter.ITEM_VIEW_TYPE_MUSIC, adapter.getItemViewType(1));
    assertEquals(0, adapter.getItemId(1));
    assertEquals(1, new ProfileSongAdapter(activity, R.layout.list_item_simple).getOffset());
  }

  @Test
  public void profileSongsCanBeInsertedAndRemoved() {
    ProfileSongAdapter adapter =
        profile(ProfileSongAdapter.DISPLAY_DEFAULT_SETTING, new Song(1, "Hey", "Ann", "LP", 65));
    Song extra = new Song(2, "Ho", "Ann", "LP", 65);

    adapter.insert(extra, 0);
    assertEquals(2, adapter.getCount() - adapter.getOffset());
    adapter.remove(extra);
    assertEquals(1, adapter.getCount() - adapter.getOffset());
    assertFalse(adapter.isEmpty());
  }

  @Test
  public void aViewHolderIsTheTagOfEveryBuiltRow() {
    SongAdapter adapter = songs(new Song(2, "Fine", "Ann", "LP", 65));
    adapter.mData[0].mParentId = 1;

    View row = adapter.getView(0, null, parent);

    assertTrue(row.getTag() instanceof MusicViewHolder);
  }
}
