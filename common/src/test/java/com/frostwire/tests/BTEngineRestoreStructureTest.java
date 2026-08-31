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

/** Regression coverage for previous-session restore queue failure handling. */
class BTEngineRestoreStructureTest {

  @Test
  void failedRestoreAdvancesToTheNextSessionFile() throws Exception {
    String source = read("../common/src/main/java/com/frostwire/bittorrent/BTEngine.java");
    String task = source.substring(source.indexOf("private final class RestoreDownloadTask"));

    assertTrue(
        task.contains("catch (Throwable e)") && task.contains("runNextRestoreDownloadTask();"),
        "an unloadable previous-session torrent must not stall later restores");
  }

  @Test
  void restoreDiscoveryStaysLimitedToThePrivateSessionDirectory() throws Exception {
    String source = read("../common/src/main/java/com/frostwire/bittorrent/BTEngine.java");
    String restore = source.substring(source.indexOf("public void restoreDownloads()"),
        source.indexOf("File settingsFile()"));

    assertTrue(
        restore.contains("ctx.homeDir.listFiles"),
        "restore must only enumerate previous-session files in the engine home directory");
    assertTrue(
        !restore.contains("ctx.torrentsDir.listFiles"),
        "restore must not enumerate all public Torrents files");
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
