/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * The context menu prefetched the selection's songs into a field when it opened. An action picked
 * before that finished used the previous selection's songs, or an album or artist id as a song id,
 * so "Delete" on an album could delete songs nobody chose (GitHub #1442). The songs are now loaded
 * for the selection when the action runs, and an item without songs does nothing.
 */
public class ApolloFragmentSongListTest {

  /** Each kind of list answers with a distinct marker id, so the test sees which was asked. */
  private static final ApolloFragment.SongLists LISTS =
      new ApolloFragment.SongLists() {
        @Override
        public long[] album(long id) {
          return new long[] {1000 + id};
        }

        @Override
        public long[] artist(long id) {
          return new long[] {2000 + id};
        }

        @Override
        public long[] genre(long id) {
          return new long[] {3000 + id};
        }

        @Override
        public long[] playlist(long id) {
          return new long[] {4000 + id};
        }

        @Override
        public long[] favorites() {
          return new long[] {5000};
        }

        @Override
        public long[] lastAdded() {
          return new long[] {6000};
        }
      };

  private static final ApolloFragment.SongLists EMPTY =
      new ApolloFragment.SongLists() {
        @Override
        public long[] album(long id) {
          return new long[0];
        }

        @Override
        public long[] artist(long id) {
          return null;
        }

        @Override
        public long[] genre(long id) {
          return new long[0];
        }

        @Override
        public long[] playlist(long id) {
          return null;
        }

        @Override
        public long[] favorites() {
          return new long[0];
        }

        @Override
        public long[] lastAdded() {
          return null;
        }
      };

  @Test
  public void eachSelectionGetsItsOwnSongs() {
    assertArrayEquals(
        new long[] {7}, ApolloFragment.songListFor(new Song(7, "s", "a", "b", 2000), 7, LISTS));
    assertArrayEquals(
        new long[] {1005},
        ApolloFragment.songListFor(new Album(5, "album", "artist", 1, "2020"), 5, LISTS));
    assertArrayEquals(
        new long[] {2009}, ApolloFragment.songListFor(new Artist(9, "artist", 1, 1), 9, LISTS));
    assertArrayEquals(
        new long[] {3003}, ApolloFragment.songListFor(new Genre(3, "genre"), 3, LISTS));
    assertArrayEquals(
        new long[] {4011}, ApolloFragment.songListFor(new Playlist(11, "mine"), 11, LISTS));
    assertArrayEquals(
        new long[] {5000},
        ApolloFragment.songListFor(
            new Playlist(PlaylistLoader.FAVORITE_PLAYLIST_ID, "fav"),
            PlaylistLoader.FAVORITE_PLAYLIST_ID,
            LISTS));
    assertArrayEquals(
        new long[] {6000},
        ApolloFragment.songListFor(
            new Playlist(PlaylistLoader.LAST_ADDED_PLAYLIST_ID, "last"),
            PlaylistLoader.LAST_ADDED_PLAYLIST_ID,
            LISTS));
  }

  @Test
  public void anItemWithoutSongsNeverActsOnItsOwnIdAsASong() {
    long[] none = new long[0];
    assertArrayEquals(
        none, ApolloFragment.songListFor(new Album(5, "album", "artist", 1, "2020"), 5, EMPTY));
    assertArrayEquals(none, ApolloFragment.songListFor(new Artist(9, "artist", 1, 1), 9, EMPTY));
    assertArrayEquals(none, ApolloFragment.songListFor(new Genre(3, "genre"), 3, EMPTY));
    assertArrayEquals(none, ApolloFragment.songListFor(new Playlist(11, "mine"), 11, EMPTY));
    assertArrayEquals(none, ApolloFragment.songListFor(null, 11, EMPTY));
  }

  @Test
  public void theMenuNoLongerPrefetchesIntoASharedField() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve(
                            "apollo/src/com/andrew/apollo/ui/fragments/profile/ApolloFragment.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("newlong[]{mSelectedId}"));
    assertFalse(source.contains("prefetchSongListForSelectedItem"));
  }
}
