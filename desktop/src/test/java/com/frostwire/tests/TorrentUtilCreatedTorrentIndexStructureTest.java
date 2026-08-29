/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TorrentUtilCreatedTorrentIndexStructureTest {

  @Test
  void createdTorrentIsIndexedBeforeOpenForSeed() throws Exception {
    String source =
        Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/frostwire/gui/bittorrent/TorrentUtil.java"),
            StandardCharsets.UTF_8);
    String compact = source.replaceAll("\\s+", "");
    assertTrue(compact.contains("indexCreatedTorrent(torrent,file.getName());"));
    int indexAt = compact.indexOf("indexCreatedTorrent(torrent,file.getName());");
    int openAt = compact.indexOf("openTorrentForSeed(torrentFile,file.getParentFile())");
    assertTrue(indexAt >= 0 && openAt > indexAt);
    assertTrue(compact.contains("indexer.indexTorrentInfo(torrent,name)"));
  }
}
