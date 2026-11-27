/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What MusicUtils asks MediaStore for and what it does with the answers: song lists, ids by name,
 * playlists and the other small lookups the Apollo screens rely on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsMediaStoreTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    // Nothing is the UI thread, so the code that hops to a background thread runs inline.
    ApolloServiceFixture.installPlatform(false);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
  }

  @After
  public void tearDown() throws Exception {
    FakeConfig.uninstall();
  }

  // --- song lists ---

  @Test
  public void aCursorOfPlaylistMembersReadsAudioIdsAndAPlainOneReadsIds() {
    Cursor members = cursor(new String[] {"audio_id"}, row("audio_id", 7L), row("audio_id", 9L));
    Cursor plain = cursor(new String[] {"_id"}, row("_id", 3L), row("_id", 4L));

    assertArrayEquals(new long[] {7, 9}, MusicUtils.getSongListForCursor(members));
    assertArrayEquals(new long[] {3, 4}, MusicUtils.getSongListForCursor(plain));
    assertEquals(0, MusicUtils.getSongListForCursor(null).length);
    assertTrue("the list helper closes the cursor", members.isClosed());
  }

  @Test
  public void aSongIsReadWithItsMetadataOnlyWhenMediaStoreKnowsExactlyOne() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            "5".equals(a[0])
                ? cursor(
                    p,
                    row(
                        "_id",
                        5L,
                        "title",
                        "Hey",
                        "artist",
                        "Ann",
                        "album",
                        "LP",
                        "duration",
                        1234))
                : cursor(p);

    Song song = MusicUtils.getSong(context, 5);

    assertEquals("Hey", song.mSongName);
    assertEquals("Ann", song.mArtistName);
    assertEquals("LP", song.mAlbumName);
    assertEquals(5, song.mSongId);
    assertNull(MusicUtils.getSong(context, 6));
  }

  @Test
  public void songsOfAnArtistAlbumGenreAndPlaylistComeFromTheirOwnQueries() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          String path = u.getPath();
          if (path.contains("genres")) {
            return cursor(p, row("_id", 31L));
          }
          if (path.contains("members")) {
            return cursor(p, row("audio_id", 41L), row("audio_id", 42L));
          }
          if (s.startsWith("artist_id=")) {
            return cursor(p, row("_id", 11L), row("_id", 12L));
          }
          return cursor(p, row("_id", 21L));
        };

    assertArrayEquals(new long[] {11, 12}, MusicUtils.getSongListForArtist(context, 1));
    assertArrayEquals(new long[] {21}, MusicUtils.getSongListForAlbum(context, 2));
    assertArrayEquals(new long[] {31}, MusicUtils.getSongListForGenre(context, 3));
    assertArrayEquals(new long[] {41, 42}, MusicUtils.getSongListForPlaylist(context, 4));
  }

  @Test
  public void songListsAreEmptyWithoutAContextAnAnswerOrWhenMediaStoreFails() {
    assertEquals(0, MusicUtils.getSongListForArtist(null, 1).length);
    assertEquals(0, MusicUtils.getSongListForAlbum(null, 1).length);
    assertEquals(0, MusicUtils.getSongListForGenre(null, 1).length);
    assertEquals(0, MusicUtils.getSongListForPlaylist(null, 1).length);
    assertEquals(0, MusicUtils.getSongListForFavorites(null).length);
    assertEquals(0, MusicUtils.getSongListForLastAdded(null).length);

    // No cursor at all
    assertEquals(0, MusicUtils.getSongListForArtist(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForAlbum(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForGenre(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForPlaylist(context, 1).length);

    FakeMediaStore.failure = new IllegalStateException("store is down");
    assertEquals(0, MusicUtils.getSongListForArtist(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForAlbum(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForGenre(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForPlaylist(context, 1).length);
  }

  @Test
  public void anArtistWithNoSongsHasAnEmptyList() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    assertEquals(0, MusicUtils.getSongListForArtist(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForAlbum(context, 1).length);
  }

  @Test
  public void songListsAskedFromTheUiThreadAnswerEmptyAndFetchOffIt() throws Exception {
    ApolloServiceFixture.installPlatform(true);
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 1L));

    assertEquals(0, MusicUtils.getSongListForArtist(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForAlbum(context, 1).length);
    assertEquals(0, MusicUtils.getSongListForGenre(context, 1).length);
    assertEquals(-1, MusicUtils.getIdForPlaylist(context, "x"));
    assertEquals(-1, MusicUtils.getIdForArtist(context, "x"));
    assertEquals(-1, MusicUtils.getIdForAlbum(context, "x", "y"));
    assertEquals(0, MusicUtils.createPlaylist(context, "x"));
    assertNull(MusicUtils.getSongCountForAlbum(context, 3));
    assertNull(MusicUtils.getReleaseDateForAlbum(context, 3));
  }

  @Test
  public void favoritesAndLastAddedListsComeFromTheirCursors() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 8L), row("_id", 9L));

    assertArrayEquals(new long[] {8, 9}, MusicUtils.getSongListForLastAdded(context));
    // Nothing is favorited yet; the list comes from the favorites database, not MediaStore.
    assertEquals(0, MusicUtils.getSongListForFavorites(context).length);
  }

  // --- ids and names ---

  @Test
  public void albumIdAndNameOfASongComeFromItsRow() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          if (s.startsWith("_id=77")) {
            return cursor(p, row("album_id", 5L, "album", "Greatest"));
          }
          return cursor(p);
        };

    assertEquals(5, MusicUtils.getAlbumIdForSong(context, 77));
    assertEquals("a song MediaStore doesn't know", -1, MusicUtils.getAlbumIdForSong(context, 1));
    assertEquals("an album with no songs", -1, MusicUtils.getAlbumIdForSong(context, 2));
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("album", "Greatest"));
    assertEquals("Greatest", MusicUtils.getAlbumName(context, 5));
  }

  @Test
  public void albumIdIsMinusOneWhenTheStoreRefusesTheQuery() {
    FakeMediaStore.failure = new android.database.sqlite.SQLiteException("no such column");

    assertEquals(-1, MusicUtils.getAlbumIdForSong(context, 77));
  }

  @Test
  public void idsForPlaylistArtistAndAlbumAreTheFirstMatchOrMinusOne() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          String path = u.getPath();
          if (path.contains("playlists")) {
            return a[a.length - 1].equals("Mix") ? cursor(p, row("_id", 4L)) : cursor(p);
          }
          if (path.contains("artists")) {
            return a[0].equals("Ann") ? cursor(p, row("_id", 6L)) : cursor(p);
          }
          return a[0].equals("LP") && a[1].equals("Ann") ? cursor(p, row("_id", 8L)) : cursor(p);
        };

    assertEquals(4, MusicUtils.getIdForPlaylist(context, "Mix"));
    assertEquals(-1, MusicUtils.getIdForPlaylist(context, "Other"));
    assertEquals(-1, MusicUtils.getIdForPlaylist(null, "Mix"));
    assertEquals(6, MusicUtils.getIdForArtist(context, "Ann"));
    assertEquals(-1, MusicUtils.getIdForArtist(context, "Bob"));
    assertEquals(-1, MusicUtils.getIdForArtist(context, null));
    assertEquals(8, MusicUtils.getIdForAlbum(context, "LP", "Ann"));
    assertEquals(-1, MusicUtils.getIdForAlbum(context, "LP", "Bob"));

    FakeMediaStore.failure = new IllegalStateException("down");
    assertEquals(-1, MusicUtils.getIdForArtist(context, "Ann"));
    assertEquals(-1, MusicUtils.getIdForAlbum(context, "LP", "Ann"));
  }

  @Test
  public void albumSongCountAndReleaseYearAreTheFirstStringOfTheirRow() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("numsongs", "12", "minyear", "1999"));

    assertEquals("12", MusicUtils.getSongCountForAlbum(context, 3));
    assertEquals("1999", MusicUtils.getReleaseDateForAlbum(context, 3));
    assertNull(MusicUtils.getSongCountForAlbum(null, 3));
    assertNull(MusicUtils.getSongCountForAlbum(context, -1));
    assertNull(MusicUtils.getReleaseDateForAlbum(null, 3));
    assertNull(MusicUtils.getReleaseDateForAlbum(context, -1));

    FakeMediaStore.failure = new IllegalStateException("down");
    assertNull(MusicUtils.getSongCountForAlbum(context, 3));
    assertNull(MusicUtils.getReleaseDateForAlbum(context, 3));
  }

  @Test
  public void firstStringResultIsNullForNoRowsAndLeavesTheCursorOpenOnRequest() {
    Cursor empty = cursor(new String[] {"a"});
    Cursor one = cursor(new String[] {"a"}, row("a", "x"));

    assertNull(MusicUtils.getFirstStringResult(null, true));
    assertNull(MusicUtils.getFirstStringResult(empty, true));
    assertTrue(empty.isClosed());
    assertEquals("x", MusicUtils.getFirstStringResult(one, false));
    assertFalse(one.isClosed());
  }

  // --- playlists ---

  @Test
  public void aNewPlaylistIsInsertedAndItsIdReturned() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    FakeMediaStore.insertedId = 55;

    assertEquals(55, MusicUtils.createPlaylist(context, "Road"));

    assertEquals(1, FakeMediaStore.inserts.size());
    ContentValues values = FakeMediaStore.insertedValues.get(0);
    assertEquals("Road", values.getAsString("name"));
    assertNotNull(values.getAsLong("date_added"));
    assertEquals(-1, MusicUtils.createPlaylist(context, ""));
    assertEquals(-1, MusicUtils.createPlaylist(context, null));
    assertEquals(-1, MusicUtils.createPlaylist(null, "x"));
  }

  @Test
  public void anOwnerlessPlaylistIsRecreatedWithATrailingSpaceUnlessAlreadyFixed() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("name", "Road", "owner_package_name", null));
    assertEquals(100, MusicUtils.createPlaylist(context, "Road", true));
    assertEquals("Road ", FakeMediaStore.insertedValues.get(0).getAsString("name"));

    FakeMediaStore.inserts.clear();
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(p, row("name", "Road ", "owner_package_name", context.getPackageName()));
    assertEquals(
        "already ours, nothing to create", -1, MusicUtils.createPlaylist(context, "Road", true));
    assertTrue(FakeMediaStore.inserts.isEmpty());
  }

  @Test
  public void aStoreThatReturnsNoUriForTheNewPlaylistGivesMinusOne() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    FakeMediaStore.insertedId = -1;

    assertEquals(-1, MusicUtils.createPlaylist(context, "Road"));
  }

  @Test
  public void playlistsAreReadWithTheirIdsAndNames() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("_id", 1L, "name", "A"), row("_id", 2L, "name", "B"));

    List<Playlist> playlists = MusicUtils.getPlaylists(context);

    assertEquals(2, playlists.size());
    assertEquals(2, playlists.get(1).mPlaylistId);
    assertEquals("B", playlists.get(1).mPlaylistName);
    assertTrue(
        "only this app's playlists are asked for",
        FakeMediaStore.queries.get(0).contains("owner_package_name"));
  }

  @Test
  public void failingToReadPlaylistsGivesAnEmptyList() {
    FakeMediaStore.failure = new IllegalStateException("down");

    assertTrue(MusicUtils.getPlaylists(context).isEmpty());
  }

  @Test
  public void playlistContentUrisPointAtThePrimaryVolumeAndItsMembers() {
    Uri playlists = MusicUtils.getPlaylistContentUri();

    assertTrue(playlists.getPath().contains("external_primary"));
    assertTrue(MusicUtils.getPlaylistMembersUri(9).getPath().endsWith("/9/members"));
  }

  @Test
  @Config(sdk = 28)
  public void beforeAndroid11PlaylistsLiveInTheExternalVolume() {
    assertTrue(MusicUtils.getPlaylistContentUri().getPath().contains("/external/"));
  }

  @Test
  public void clearingAPlaylistDeletesItsMembers() {
    MusicUtils.clearPlaylist(context, 3);
    MusicUtils.clearPlaylist(null, 3);

    assertEquals(1, FakeMediaStore.deletes.size());
    assertTrue(FakeMediaStore.deletes.get(0).contains("/3/members"));
  }

  @Test
  public void removingASongOrAMemberFromAPlaylistDeletesThatRow() {
    MusicUtils.removeFromPlaylist(context, 12, 3);
    MusicUtils.removePlaylistMember(context, 3, 99);
    MusicUtils.removeFromPlaylist(null, 12, 3);
    MusicUtils.removePlaylistMember(null, 3, 99);

    assertEquals(2, FakeMediaStore.deletes.size());
    assertTrue(FakeMediaStore.deletes.get(0).endsWith("audio_id = ? "));
    assertTrue(FakeMediaStore.deletes.get(1).endsWith("_id=?"));
  }

  @Test
  public void aFailingStoreDoesNotCrashRemovingFromAPlaylist() {
    FakeMediaStore.failure = new IllegalStateException("down");

    MusicUtils.removeFromPlaylist(context, 12, 3);
    MusicUtils.removePlaylistMember(context, 3, 99);

    assertEquals(2, FakeMediaStore.deletes.size());
  }

  @Test
  public void movingAMemberAsksTheStoreToMoveItemsByPosition() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("_id", 10L), row("_id", 11L), row("_id", 12L));

    MusicUtils.movePlaylistMember(context, 3, 10, 12);

    assertEquals(1, FakeMediaStore.updates.size());
    assertTrue(FakeMediaStore.updates.get(0).contains("move=true"));
  }

  @Test
  public void movingAMemberThatIsGoneOrOntoItselfMovesNothing() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 10L), row("_id", 11L));

    MusicUtils.movePlaylistMember(context, 3, 10, 99);
    MusicUtils.movePlaylistMember(context, 3, 10, 10);
    MusicUtils.movePlaylistMember(null, 3, 10, 11);
    FakeMediaStore.failure = new IllegalStateException("down");
    MusicUtils.movePlaylistMember(context, 3, 10, 11);

    assertTrue(FakeMediaStore.updates.isEmpty());
  }

  @Test
  public void playlistInsertValuesNumberTheSongsFromTheFirstPlayOrder() {
    ContentValues[] values = MusicUtils.playlistInsertValues(new long[] {5, 6, 7}, 1, 2, 40);

    assertEquals(2, values.length);
    assertEquals(6, (long) values[0].getAsLong("audio_id"));
    assertEquals(40, (long) values[0].getAsLong("play_order"));
    assertEquals(7, (long) values[1].getAsLong("audio_id"));
    assertEquals(41, (long) values[1].getAsLong("play_order"));
  }

  // --- ringtone ---

  @Test
  public void settingARingtoneMarksTheSongAsRingtoneAndAlarm() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("_id", 5L, "_data", "/x.mp3", "title", "Tune"));

    MusicUtils.setRingtone(context, 5, (byte) 0);
    MusicUtils.setRingtone(null, 5, (byte) 0);

    assertEquals(1, FakeMediaStore.updates.size());
    assertTrue(FakeMediaStore.updates.get(0).contains("/audio/media/5"));
    assertEquals("1", FakeMediaStore.updatedValues.get(0).getAsString("is_ringtone"));
    assertEquals("1", FakeMediaStore.updatedValues.get(0).getAsString("is_alarm"));
  }

  @Test
  public void aRingtoneFileTypeIsSetInTheInternalStore() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> cursor(p, row("_id", 5L, "_data", "/x.mp3", "title", "Tune"));

    MusicUtils.setRingtone(context, 5, com.frostwire.android.core.Constants.FILE_TYPE_RINGTONES);

    assertTrue(FakeMediaStore.updates.get(0).contains("/internal/"));
  }

  // --- file lookups ---

  @Test
  public void aFilesAudioIdIsFoundInTheFirstStoreThatKnowsIt() {
    File file = new File("/storage/emulated/0/Music/a.mp3");
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.toString().contains("external_primary") && a[0].equals(file.getAbsolutePath())
                ? cursor(p, row("_id", 88L))
                : cursor(p);

    assertEquals(88, MusicUtils.getMediaStoreAudioIdForFile(context, file));
    assertEquals(
        "the volume-less store is asked first, then external_primary",
        2,
        FakeMediaStore.queries.size());
  }

  @Test
  public void aFileNoStoreKnowsHasNoAudioId() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    assertEquals(-1, MusicUtils.getMediaStoreAudioIdForFile(context, new File("/x/y.mp3")));
    assertEquals(3, FakeMediaStore.queries.size());
    assertEquals(-1, MusicUtils.getMediaStoreAudioIdForFile(null, new File("/x/y.mp3")));
    assertEquals(-1, MusicUtils.getMediaStoreAudioIdForFile(context, null));
  }

  @Test
  public void downloadsDocumentsPathsAreResolvedToFilePathsAndMediaStoreIds() {
    assertEquals(
        "/storage/emulated/0/a.mp3",
        MusicUtils.getFilePathFromComAndroidProvidersDownloadsDocumentsPath(
            context, "/storage/emulated/0/a.mp3"));
    assertEquals(
        "/root/a.mp3",
        MusicUtils.getFilePathFromComAndroidProvidersDownloadsDocumentsPath(
            context, "/root/a.mp3"));
    assertEquals(
        "/storage/emulated/0/Download/a.mp3",
        MusicUtils.getFilePathFromComAndroidProvidersDownloadsDocumentsPath(
            context,
            "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fa.mp3"));

    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 66L));
    assertEquals(
        66,
        MusicUtils.getFileIdFromComAndroidProvidersDownloadsDocumentsPath(
            context, "/storage/emulated/0/a.mp3"));
    assertEquals(
        "the path the store was asked about",
        "_data = ?",
        FakeMediaStore.queries.get(0).substring(FakeMediaStore.queries.get(0).indexOf('|') + 1));
  }

  @Test
  public void aMediaUriThatIsNotAMediaStoreDocumentHasNoDataPath() {
    assertNull(
        MusicUtils.getDataPathFromMediaStoreContentURI(
            context, Uri.parse("content://other/document/1")));
  }

  // --- labels and playback helpers that need no service ---

  @Test
  public void timeStringsUseMinutesAndHoursFormats() {
    assertEquals("1:05", MusicUtils.makeTimeString(context, 65).trim());
    assertTrue(MusicUtils.makeTimeString(context, 3_725).contains("1"));
  }

  @Test
  public void playbackHelpersDoNothingWithoutAServiceOrAList() {
    MusicUtils.playFDs(null, 0, false);
    MusicUtils.playFDs(new long[0], 0, false);
    MusicUtils.playFDs(new long[] {1}, 0, false);
    MusicUtils.playNext(new long[] {1});
    MusicUtils.playNext(null);
    MusicUtils.addToQueue(context, new long[] {1});
    MusicUtils.playSimple("/x.mp3");
    MusicUtils.stopSimplePlayer();
    MusicUtils.shuffleAll(context);
    assertFalse(MusicUtils.playFile(new File("/x.mp3")));
    assertFalse(MusicUtils.playFileFromUri(null));
    MusicUtils.playFileFromUserItemClick(null, new File("/x.mp3"));
    MusicUtils.playFileFromUserItemClick(context, null);
    assertNull(MusicUtils.getLastAlbumForArtist(context, "nobody"));
    assertFalse(MusicUtils.isPaused());
  }

  /**
   * setRingtone runs on a worker thread; its failure message used to be shown from there, which
   * UIUtils refuses (it crashes off the UI thread). It is posted to the UI thread now.
   */
  @Test
  public void aRingtoneThatCouldNotBeSetIsReportedOnTheUiThread() throws Exception {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    MusicUtils.setRingtone(context, 5, (byte) 0);

    ApolloServiceFixture.installPlatform(true);
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    assertEquals(
        context.getString(com.frostwire.android.R.string.ringtone_not_set),
        org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
  }
}
