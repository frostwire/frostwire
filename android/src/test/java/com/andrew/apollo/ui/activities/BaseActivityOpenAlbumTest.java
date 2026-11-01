/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Tapping the bottom bar's album art opened the playing album with the songs of whatever album had
 * the playing song's id: the song's id was passed where the album's was needed (GitHub #1454).
 *
 * <p>Source check only: the click handler needs the playback service.
 */
public class BaseActivityOpenAlbumTest {

  @Test
  public void theAlbumsSongsAreLoadedByTheAlbumsId() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve("apollo/src/com/andrew/apollo/ui/activities/BaseActivity.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(source.contains("getSongListForAlbum(BaseActivity.this,albumId)"));
    assertFalse(source.contains("currentAlbumId=MusicUtils.getCurrentAudioId()"));
  }
}
