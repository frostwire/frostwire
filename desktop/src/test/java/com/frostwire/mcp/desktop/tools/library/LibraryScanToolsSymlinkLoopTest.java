/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The MCP library list/scan tools must terminate on a symlink loop and count each file once. */
class LibraryScanToolsSymlinkLoopTest {

  @TempDir Path tmp;

  private Path loopingTree() throws IOException {
    Path album = Files.createDirectories(tmp.resolve("album"));
    Files.createFile(album.resolve("song.mp3"));
    try {
      Files.createSymbolicLink(album.resolve("back"), tmp);
      Files.createSymbolicLink(album.resolve("self"), album);
    } catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "symbolic links not supported: " + e);
    }
    return album.resolve("song.mp3");
  }

  @Test
  void listToolTerminatesOnSymlinkLoop() throws Exception {
    Path song = loopingTree();

    List<File> files =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> LibraryListTool.collectFiles(tmp.toFile(), "all"));

    assertEquals(List.of(song.toFile()), files);
  }

  @Test
  void scanToolTerminatesOnSymlinkLoop() throws Exception {
    loopingTree();

    int count =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> LibraryScanTool.scanFiles(tmp.toFile(), "all"));

    assertEquals(1, count);
  }
}
