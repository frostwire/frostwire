/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MainActivityShutdownStructureTest {

  @Test
  public void shutdownFinishesWithoutRemovingTask() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/activities/MainActivity.java");
    String shutdownBlock = blockStartingAt(source, "public void shutdown()");
    String finishForShutdownBlock = blockStartingAt(source, "private void finishForShutdown()");
    String finishOverrideBlock = blockStartingAt(source, "public void finish()");
    String onLastDialogButtonPositiveBlock =
        blockStartingAt(source, "private void onLastDialogButtonPositive()");

    assertTrue(shutdownBlock.contains("finishForShutdown();"));
    assertFalse(shutdownBlock.contains("finish();"));
    assertTrue(finishForShutdownBlock.contains("super.finish();"));
    assertFalse(finishForShutdownBlock.contains("allowThreadDiskWrites"));
    assertFalse(finishForShutdownBlock.contains("finishAndRemoveTask"));
    assertTrue(finishOverrideBlock.contains("super.finishAndRemoveTask();"));
    assertTrue(onLastDialogButtonPositiveBlock.contains("moveTaskToBack(true);"));
    assertFalse(onLastDialogButtonPositiveBlock.contains("allowThreadDiskWrites"));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    if (openingBrace < 0) {
      return source.substring(start);
    }
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

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
