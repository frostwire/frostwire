/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class TransferResetVisibilityTest {

  @Test
  public void engineResetDoesNotEmptyTransfersBeforeRestore() throws Exception {
    String manager = projectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String reset = blockStartingAt(manager, "public void reset()");

    assertFalse(reset.contains("clearTransfers();"));
    assertTrue(reset.contains("sessionTorrentsRestored.set(false);"));
    assertTrue(reset.contains("loadTorrentsTask(0)"));
  }

  @Test
  public void explicitShutdownStillClearsTransfers() throws Exception {
    String manager = projectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String shutdown = blockStartingAt(manager, "public void onShutdown(boolean disconnected)");

    assertTrue(shutdown.contains("if (!disconnected)"));
    assertTrue(shutdown.contains("clearTransfers();"));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    int depth = 0;
    for (int i = openingBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    return source.substring(start);
  }

  private static String projectFile(String relative) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relative);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relative);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
