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

class BTDownloadFinishedStateStructureTest {

  @Test
  void finishedProgressMapsToSeedingOrFinishedNotDownloading() throws Exception {
    String source =
        read("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    String compact = source.replaceAll("\\s+", "");
    assertTrue(compact.contains("status.isFinished()"));
    assertTrue(compact.contains("Float.compare(status.progress(),1f)>=0"));
    assertTrue(compact.contains("totalWantedDone()>=status.totalWanted()"));
    assertTrue(compact.contains("returnisPaused?TransferState.FINISHED:TransferState.SEEDING;"));
    assertTrue(
        compact.contains("caseTORRENT_FINISHED:try{if(th.isValid()){cachedStatus=th.status();"));
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
