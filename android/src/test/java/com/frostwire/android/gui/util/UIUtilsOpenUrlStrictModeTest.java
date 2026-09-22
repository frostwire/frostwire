/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class UIUtilsOpenUrlStrictModeTest {

  @Test
  public void openUrlLaunchesOffMainThreadWithoutRelaxingStrictMode() throws Exception {
    String source = readProjectFile("src/main/java/com/frostwire/android/gui/util/UIUtils.java");
    String openUrl = blockStartingAt(source, "public static void openURL");

    assertTrue(
        openUrl.contains(
            "SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () ->"));
    assertTrue(openUrl.contains("launchContext.startActivity(intent);"));
    assertTrue(openUrl.contains("SystemUtils.postToUIThread("));
    assertFalse(openUrl.contains("StrictMode"));
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

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
