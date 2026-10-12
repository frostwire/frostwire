/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.util.Launcher;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opening a file runs Desktop.open() on a pool thread, where nothing can catch an exception that
 * escapes. Desktop.open() throws IllegalArgumentException when the file no longer exists, which the
 * crash reporter received from real installs (7.1.0 build 332, Windows 11).
 */
class LauncherFailureHandlingTest {

  @Test
  void openingAFileThatVanishedDoesNotEscapeThePoolThread() throws Exception {
    Assumptions.assumeFalse(
        GraphicsEnvironment.isHeadless(), "Desktop.open() is only reachable with a display");
    File vanished = Files.createTempFile("frostwire-launcher-", ".txt").toFile();
    assertTrue(vanished.delete());

    List<Throwable> escaped = new CopyOnWriteArrayList<>();
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> escaped.add(error));
    try {
      Launcher.launchFile(vanished);
      Thread.sleep(1500); // the open runs asynchronously on DesktopParallelExecutor
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }

    assertEquals(List.of(), escaped, "the background open must handle a missing file itself");
  }

  @Test
  void backgroundOpenHandlesIllegalArgumentAndSecurityExceptions() throws Exception {
    String source =
        Files.readString(findSource("src/main/java/com/limegroup/gnutella/util/Launcher.java"))
            .replaceAll("\\s+", "");

    assertTrue(
        source.contains("catch(IllegalArgumentException|SecurityExceptione)"),
        "Desktop.open() failures for missing or forbidden files must be handled in the lambda");
  }

  private static Path findSource(String relative) {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {relative, "desktop/" + relative}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return file;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + relative);
  }
}
