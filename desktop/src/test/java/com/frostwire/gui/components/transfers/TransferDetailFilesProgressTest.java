/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structural regression test: the Files tab must not freeze per-file progress. Holders are cached
 * for instant checkbox filtering, but while the torrent is still moving (checking/downloading) the
 * refresh loop must rebuild them when torrent progress or state drifts, otherwise the tab shows
 * stale percents (e.g. 11%) long after the transfer itself reached 100%.
 */
class TransferDetailFilesProgressTest {

  @Test
  void filesRefreshRebuildsHoldersOnProgressDrift() throws Exception {
    String files =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailFiles.java");
    assertTrue(
        files.contains("refreshStaleProgress") || files.contains("lastBuiltTorrentProgress"),
        "TransferDetailFiles must track built progress and refresh stale holders");
    assertTrue(
        files.contains("applyFilterOnEdt"), "refresh must still re-apply the skipped-files filter");
  }

  private static String readSource(String moduleRelative) throws Exception {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return Files.readString(file);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
