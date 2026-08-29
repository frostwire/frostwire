/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structure regression for the metadata-less zombie rescue: when a full
 * .torrent arrives (e.g. via TORRENT_FETCH over the mesh) but the session
 * already holds a metadata-less handle for the same infohash (added by an
 * earlier unreachable-seeder magnet), the zombie must be removed and the
 * torrent re-added — merging into it never fires torrent_added and the
 * download stays invisible in the UI.
 */
class BTEngineZombieRescueStructureTest {

  @Test
  void downloadReplacesMetadataLessHandles() throws Exception {
    String source = read("../common/src/main/java/com/frostwire/bittorrent/BTEngine.java");
    String compact = source.replaceAll("\\s+", "");

    assertTrue(
        compact.contains("TorrentInfoexistingInfo=null;"),
        "must inspect the existing handle's metadata before merging");
    assertTrue(
        compact.contains("if(existingInfo==null){"),
        "metadata-less handle must take the replacement branch");
    assertTrue(
        compact.contains("remove(th);"),
        "the zombie handle must be removed before re-adding");
    assertTrue(
        compact.contains("torrentHandleExists=false;"),
        "replacement must fall through to a fresh add");
  }

  private static String read(String relativePath) throws IOException {
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path file = cwd.resolve(relativePath);
    if (!Files.isRegularFile(file)) {
      file = cwd.resolve(relativePath.substring("../".length()));
    }
    return Files.readString(file, StandardCharsets.UTF_8);
  }
}