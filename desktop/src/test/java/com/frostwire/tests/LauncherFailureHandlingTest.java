/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.util.Launcher;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opening a file runs Desktop.open() on a pool thread, where nothing can catch an exception that
 * escapes. Desktop.open() throws IllegalArgumentException when the file no longer exists, which the
 * crash reporter received from real installs (7.1.0 build 332, Windows 11). The failure must reach
 * the caller's onFailure instead.
 */
class LauncherFailureHandlingTest {

  @Test
  void openingAFileThatVanishedDoesNotEscapeThePoolThread() throws Exception {
    Assumptions.assumeFalse(
        GraphicsEnvironment.isHeadless(), "Desktop.open() is only reachable with a display");
    File vanished = Files.createTempFile("frostwire-launcher-", ".txt").toFile();
    assertTrue(vanished.delete());

    List<Throwable> escaped = new CopyOnWriteArrayList<>();
    CompletableFuture<Exception> reported = new CompletableFuture<>();
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> escaped.add(error));
    try {
      Launcher.launchFile(vanished, reported::complete);
      // the open runs asynchronously on DesktopParallelExecutor
      assertInstanceOf(IllegalArgumentException.class, reported.get(5, TimeUnit.SECONDS));
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }

    assertEquals(List.of(), escaped, "the background open must report a missing file, not throw");
  }
}
