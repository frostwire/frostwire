/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * The Playlists tab's Play and Add to queue picked Favorites and Last added by row (0 and 1), but
 * the rows are New playlist, Favorites, Last added, then the user's playlists: Play on Favorites
 * played Last added (GitHub #1446). They now go by the playlist's id.
 *
 * <p>Source checks only: the handlers call MusicUtils, which needs the playback service.
 */
public class PlaylistFragmentMenuStructureTest {

  private static String source() throws Exception {
    return new String(
            Files.readAllBytes(
                Path.of(System.getProperty("user.dir"))
                    .resolve("apollo/src/com/andrew/apollo/ui/fragments/PlaylistFragment.java")),
            StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }

  @Test
  public void playAndQueueGoByThePlaylistsIdNotItsRow() throws Exception {
    String source = source();
    assertFalse(source.contains("info.position==0"));
    assertFalse(source.contains("info.position==1"));
    assertTrue(source.contains("playlistId==FAVORITE_PLAYLIST_ID"));
    assertTrue(source.contains("playlistId==LAST_ADDED_PLAYLIST_ID"));
  }

  @Test
  public void theNewPlaylistRowOffersNoPlayOrQueue() throws Exception {
    assertTrue(source().contains("if(mItem.mPlaylistId!=NEW_PLAYLIST_ID){"));
  }
}
