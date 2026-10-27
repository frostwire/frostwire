/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The splash window measures the first text on the EDT. JdkWarmUp must already be loading the UI
 * fonts by then, or the EDT opens the font files and parses their character maps itself, which took
 * over 5 seconds on a 2-CPU Windows 10 machine (Icebase #fd055dfa087c, GitHub #1308). Initializer
 * needs the whole app around it, so guard the order at source level.
 */
class JdkWarmUpStartupOrderTest {
  private static final Path INITIALIZER =
      Path.of(System.getProperty("user.dir"))
          .resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java");
  private static final String START_WARM_UP = "JdkWarmUp.startInBackground();";

  @Test
  void warmUpStartsAfterTheLookAndFeelAndBeforeTheSplash() throws Exception {
    String source = Files.readString(INITIALIZER).replaceAll("\\s+", "");
    int lookAndFeel = source.indexOf("ThemeMediator.loadThemeAtStartup(saved);");
    int warmUp = source.indexOf(START_WARM_UP);
    int splash = source.indexOf("switchSplashes(awtSplash);");

    assertTrue(lookAndFeel >= 0, "look and feel installation not found");
    assertTrue(splash >= 0, "splash switch not found");
    // The warm-up reads the look and feel's fonts, so it needs them installed first.
    assertTrue(lookAndFeel < warmUp, "warm-up starts before the look and feel is installed");
    assertTrue(warmUp < splash, "warm-up starts after the splash window measures text");
    assertEquals(warmUp, source.lastIndexOf(START_WARM_UP), "warm-up started twice");
  }
}
