/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Playlist, genre, Favorites and Last added headers looked their image up by name in a cache
 * nothing ever wrote, so they always showed the default (GitHub #1461). They now show the album art
 * of their first song that has an album.
 */
public class SongArtworkTest {

  @Test
  public void theFirstSongWithAnAlbumGivesTheArtwork() {
    Map<Long, Long> albums = Map.of(7L, -1L, 8L, 42L, 9L, 43L);
    assertEquals(42L, SongArtwork.firstAlbumId(new long[] {7, 8, 9}, albums::get));
  }

  @Test
  public void noSongsOrNoAlbumsGiveNone() {
    assertEquals(-1L, SongArtwork.firstAlbumId(null, id -> 1L));
    assertEquals(-1L, SongArtwork.firstAlbumId(new long[0], id -> 1L));
    assertEquals(-1L, SongArtwork.firstAlbumId(new long[] {1, 2}, id -> -1L));
  }

  @Test
  public void aLongPlaylistWithoutAlbumsCostsAFewLookupsOnly() {
    List<Long> asked = new ArrayList<>();
    long[] songs = new long[500];
    for (int i = 0; i < songs.length; i++) {
      songs[i] = i;
    }

    SongArtwork.firstAlbumId(
        songs,
        id -> {
          asked.add(id);
          return -1L;
        });

    assertEquals(SongArtwork.MAX_SONGS_TRIED, asked.size());
  }
}
