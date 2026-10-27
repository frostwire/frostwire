/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Sizing a folder for the piece-size hint must terminate on a symlink loop. */
class CreateTorrentDialogTotalSizeTest {

  @TempDir Path tmp;

  @Test
  void totalSizeCountsEachFileOnceDespiteSymlinkLoops() throws Exception {
    Path dir = Files.createDirectories(tmp.resolve("content"));
    Files.write(dir.resolve("a.bin"), new byte[100]);
    Files.write(dir.resolve("b.bin"), new byte[23]);
    try {
      Files.createSymbolicLink(dir.resolve("back"), tmp);
      Files.createSymbolicLink(dir.resolve("self"), dir);
    } catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "symbolic links not supported: " + e);
    }

    long size =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> CreateTorrentDialog.calculateTotalSize(tmp.toFile()));

    assertEquals(123, size);
    assertEquals(100, CreateTorrentDialog.calculateTotalSize(dir.resolve("a.bin").toFile()));
  }
}
