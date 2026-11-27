/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.loaders;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The loaders turn MediaStore rows into the models the Apollo lists show, and each asks the store
 * for its own slice (an artist's songs, an album's, a genre's, a playlist's, the last added).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MediaStoreLoadersTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.installPlatform(false);
    FakeMediaStore.install();
    FakeConfig.install();
  }

  @After
  public void tearDown() throws Exception {
    FakeConfig.uninstall();
  }

  private static java.util.Map<String, Object> songRow(long id, String title) {
    return row("_id", id, "title", title, "artist", "Ann", "album", "LP", "duration", 125_000);
  }

  private void songs(long... ids) {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
          for (long id : ids) {
            rows.add(songRow(id, "song " + id));
          }
          return cursor(p, rows);
        };
  }

  private static void assertSong(Song song, long id) {
    assertEquals(id, song.mSongId);
    assertEquals("song " + id, song.mSongName);
    assertEquals("Ann", song.mArtistName);
    assertEquals("LP", song.mAlbumName);
    assertEquals(125, song.mDuration);
  }

  @Test
  public void theSongLoaderReadsEveryMusicSongWithDurationInSeconds() {
    songs(1, 2);

    List<Song> result = new SongLoader(context).loadInBackground();

    assertEquals(2, result.size());
    assertSong(result.get(0), 1);
    assertSong(result.get(1), 2);
    assertTrue(FakeMediaStore.queries.get(0).contains("is_music=1"));
  }

  @Test
  public void theSongLoaderGivesNothingWhenTheStoreHasNoCursorOrFails() {
    assertTrue(new SongLoader(context).loadInBackground().isEmpty());

    FakeMediaStore.failure = new IllegalStateException("store is down");
    assertTrue(new SongLoader(context).loadInBackground().isEmpty());
  }

  @Test
  public void theSongLoaderAcceptsAnApplicationContextOrFallsBackToTheApp() {
    assertNotNull(new SongLoader(context).getContext());
    assertNotNull(new SongLoader(new android.content.ContextWrapper(context)).getContext());
  }

  @Test
  public void anArtistsSongsAreAskedForByArtistId() {
    songs(5);

    List<Song> result = new ArtistSongLoader(context, 42L).loadInBackground();

    assertEquals(1, result.size());
    assertTrue(FakeMediaStore.queries.get(0).contains("artist_id=42"));
  }

  @Test
  public void anAlbumsSongsAreAskedForByAlbumId() {
    songs(5, 6);

    List<Song> result = new AlbumSongLoader(context, 7L).loadInBackground();

    assertEquals(2, result.size());
    assertTrue(FakeMediaStore.queries.get(0).contains("album_id=7"));
  }

  @Test
  public void theLastAddedLoaderOnlyAsksForSongsFromTheLastFourWeeks() {
    songs(5);

    List<Song> result = new LastAddedLoader(context).loadInBackground();

    assertEquals(1, result.size());
    assertTrue(FakeMediaStore.queries.get(0).contains("date_added>"));
    assertNotNull(LastAddedLoader.makeLastAddedCursor(context));
  }

  @Test
  public void aGenresSongsComeFromTheGenreMembersUri() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 3L, "title", "Riff"));

    List<Song> result = new GenreSongLoader(context, 9L).loadInBackground();

    assertEquals(1, result.size());
    assertEquals(3, result.get(0).mSongId);
    assertEquals("Riff", result.get(0).mSongName);
    assertTrue(FakeMediaStore.queries.get(0).contains("genres/9/members"));
  }

  @Test
  public void aPlaylistsSongsKeepTheirMemberIdAndAudioId() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(
                p,
                row(
                    "_id",
                    70L,
                    "audio_id",
                    8L,
                    "title",
                    "T",
                    "artist",
                    "Ann",
                    "album",
                    "LP",
                    "duration",
                    61_000));

    List<Song> result = new PlaylistSongLoader(context, 4L).loadInBackground();

    assertEquals(1, result.size());
    assertEquals(8, result.get(0).mSongId);
    assertEquals(70, result.get(0).mPlaylistMemberId);
    assertEquals(61, result.get(0).mDuration);
    assertTrue(FakeMediaStore.queries.get(0).contains("/4/members"));
  }

  @Test
  public void aPlaylistWithNoCursorHasNoSongs() {
    assertTrue(new PlaylistSongLoader(context, 4L).loadInBackground().isEmpty());
  }

  @Test
  public void artistsAreReadWithTheirAlbumAndSongCounts() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(
                p, row("_id", 1L, "artist", "Ann", "number_of_albums", 2, "number_of_tracks", 14));

    List<Artist> result = new ArtistLoader(context).loadInBackground();

    assertEquals(1, result.size());
    assertEquals("Ann", result.get(0).mArtistName);
    assertEquals(2, result.get(0).mAlbumNumber);
    assertEquals(14, result.get(0).mSongNumber);
  }

  @Test
  public void artistsAreEmptyWhenTheStoreFails() {
    FakeMediaStore.failure = new IllegalStateException("down");

    assertTrue(new ArtistLoader(context).loadInBackground().isEmpty());
  }

  @Test
  public void albumsAreReadWithArtistSongCountAndYear() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(
                p,
                row("_id", 5L, "album", "LP", "artist", "Ann", "numsongs", 11, "minyear", "1999"));

    List<Album> result = new AlbumLoader(context).loadInBackground();

    assertEquals(1, result.size());
    assertEquals("LP", result.get(0).mAlbumName);
    assertEquals("Ann", result.get(0).mArtistName);
    assertEquals(11, result.get(0).mSongNumber);
    assertEquals("1999", result.get(0).mYear);
  }

  @Test
  public void albumsAreEmptyWhenTheStoreFails() {
    FakeMediaStore.failure = new IllegalStateException("down");

    assertTrue(new AlbumLoader(context).loadInBackground().isEmpty());
  }

  @Test
  public void anArtistsAlbumsComeFromTheArtistAlbumsUriAndNoArtistMeansNone() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("_id", 5L, "album", "LP", "artist", "Ann", "numsongs", 3));

    List<Album> result = new ArtistAlbumLoader(context, 8L).loadInBackground();

    assertEquals(1, result.size());
    assertTrue(FakeMediaStore.queries.get(0).contains("artists/8/albums"));
    assertTrue(new ArtistAlbumLoader(context, -1L).loadInBackground().isEmpty());
    assertNull(new ArtistAlbumLoader(context, -1L).makeCursor(context));

    FakeMediaStore.failure = new IllegalStateException("down");
    assertTrue(new ArtistAlbumLoader(context, 8L).loadInBackground().isEmpty());
  }

  @Test
  public void playlistsStartWithTheThreeBuiltInsThenTheUsersOwn() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(p, row("_id", 30L, "name", "Road"), row("_id", 31L, "name", "Gym"));

    List<Playlist> result = new PlaylistLoader(context).loadInBackground();

    assertEquals(5, result.size());
    assertEquals(PlaylistLoader.NEW_PLAYLIST_ID, result.get(0).mPlaylistId);
    assertEquals(PlaylistLoader.FAVORITE_PLAYLIST_ID, result.get(1).mPlaylistId);
    assertEquals(PlaylistLoader.LAST_ADDED_PLAYLIST_ID, result.get(2).mPlaylistId);
    assertEquals("Road", result.get(3).mPlaylistName);
    assertEquals("Gym", result.get(4).mPlaylistName);
  }

  @Test
  public void withNoUserPlaylistsOnlyTheBuiltInsAreListed() {
    List<Playlist> result = new PlaylistLoader(context).loadInBackground();

    assertEquals(3, result.size());
  }

  @Test
  public void thePlaylistCursorIsNullWhenTheStoreFails() {
    FakeMediaStore.failure = new IllegalStateException("down");

    assertNull(PlaylistLoader.makePlaylistCursor(context));
  }

  @Test
  public void theQueueLoaderReadsTheNowPlayingCursor() {
    Cursor cursor = QueueLoader.makeQueueCursor(context);

    assertNotNull(cursor);
    cursor.close();
  }

  /** The genre projection listed album before artist, so each song showed its album as artist. */
  @Test
  public void aGenresSongKeepsItsArtistAndAlbumApart() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(
                p,
                row(
                    "_id",
                    3L,
                    "title",
                    "Riff",
                    "artist",
                    "Ann",
                    "album",
                    "LP",
                    "duration",
                    65000L));

    Song song = new GenreSongLoader(context, 9L).loadInBackground().get(0);

    assertEquals("Ann", song.mArtistName);
    assertEquals("LP", song.mAlbumName);
  }
}
