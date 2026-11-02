/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import java.util.function.LongUnaryOperator;

/**
 * Picks the artwork for a list of songs with no image of its own (a playlist, a genre, Favorites,
 * Last added): the album art of its first song on an album (frostwire/frostwire#1461).
 */
public final class SongArtwork {

  /** How many songs are tried, so a long list without albums costs a few lookups only. */
  static final int MAX_SONGS_TRIED = 10;

  private SongArtwork() {}

  /**
   * The album id of the first of {@code songs} that {@code albumIdForSong} puts on an album (an id
   * of 0 or more), among the first {@link #MAX_SONGS_TRIED}; -1 if none. {@code albumIdForSong}
   * queries MediaStore, so call this off the main thread.
   */
  public static long firstAlbumId(long[] songs, LongUnaryOperator albumIdForSong) {
    if (songs == null) {
      return -1;
    }
    for (int i = 0; i < songs.length && i < MAX_SONGS_TRIED; i++) {
      long albumId = albumIdForSong.applyAsLong(songs[i]);
      if (albumId >= 0) {
        return albumId;
      }
    }
    return -1;
  }
}
