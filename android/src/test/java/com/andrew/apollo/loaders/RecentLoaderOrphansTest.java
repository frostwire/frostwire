/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.loaders;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Loading Recent removed every entry when the MediaStore query failed or returned no cursor, and
 * removed songs MediaStore hadn't read a duration for yet (GitHub #1443). Only songs MediaStore no
 * longer has are removed now.
 */
public class RecentLoaderOrphansTest {

  @Test
  public void aFailedMediaStoreQueryRemovesNothing() {
    assertEquals(List.of(), RecentLoader.orphans(List.of(1L, 2L, 3L), null));
  }

  @Test
  public void onlySongsMediaStoreNoLongerHasAreRemoved() {
    assertEquals(List.of(2L), RecentLoader.orphans(List.of(1L, 2L, 3L), Set.of(1L, 3L)));
    assertEquals(List.of(), RecentLoader.orphans(List.of(1L, 3L), Set.of(1L, 3L)));
  }

  @Test
  public void songsWithoutADurationYetAreKept() throws Exception {
    // The MediaStore query only asks which ids exist; a null or zero duration doesn't matter.
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve("apollo/src/com/andrew/apollo/loaders/RecentLoader.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertEquals(-1, source.indexOf("duration>0"));
  }
}
