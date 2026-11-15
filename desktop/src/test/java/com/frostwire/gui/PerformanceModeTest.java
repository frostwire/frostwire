/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode.Choice;
import com.frostwire.gui.PerformanceMode.MachineFacts;
import com.frostwire.gui.PerformanceMode.Mode;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Automatic's thresholds and the mode each choice gives (frostwire/frostwire#1555). */
class PerformanceModeTest {
  /** 8 CPUs, 16 GB, 8 GB free, a 3 s warm-up. */
  private static final MachineFacts STRONG = new MachineFacts(8, 16384, 8192, 3000);

  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
  }

  @Test
  void thresholdsComeFromTheIcebaseEvidence() {
    assertEquals(2, PerformanceMode.WEAK_MAX_CPUS);
    assertEquals(4096, PerformanceMode.WEAK_TOTAL_MEMORY_MB);
    assertEquals(1024, PerformanceMode.WEAK_FREE_MEMORY_MB);
    assertEquals(30_000, PerformanceMode.SLOW_WARM_UP_MS);
  }

  @Test
  void automaticPicksLiteOnEachSignOfAWeakMachine() {
    assertFalse(PerformanceMode.isWeak(STRONG));
    // #1512's machine: 2 CPUs.
    assertTrue(PerformanceMode.isWeak(new MachineFacts(2, 16384, 8192, 3000)));
    assertFalse(PerformanceMode.isWeak(new MachineFacts(3, 16384, 8192, 3000)));
    // Under 4 GB of RAM.
    assertTrue(PerformanceMode.isWeak(new MachineFacts(8, 4095, 2048, 3000)));
    assertFalse(PerformanceMode.isWeak(new MachineFacts(8, 4096, 2048, 3000)));
    // Icebase #0207d9b0209b: 8 CPUs but 103 MB free.
    assertTrue(PerformanceMode.isWeak(new MachineFacts(8, 16384, 103, 3000)));
    assertFalse(PerformanceMode.isWeak(new MachineFacts(8, 16384, 1024, 3000)));
    // A slow warm-up last time.
    assertTrue(PerformanceMode.isWeak(new MachineFacts(8, 16384, 8192, 30_000)));
    assertFalse(PerformanceMode.isWeak(new MachineFacts(8, 16384, 8192, 29_999)));
  }

  @Test
  void unknownFactsDoNotCount() {
    // Free memory is only read on Windows; a first start has no warm-up time yet.
    assertFalse(PerformanceMode.isWeak(new MachineFacts(8, -1, -1, -1)));
  }

  @Test
  void automaticDecidesFromTheMachine() {
    PerformanceMode.resolve(Choice.AUTOMATIC, new MachineFacts(2, 2048, 300, 90_000));
    assertTrue(PerformanceMode.isLite());
    assertTrue(PerformanceMode.isAutomatic());
    assertEquals("lite", PerformanceMode.reportValue());

    PerformanceMode.resolve(Choice.AUTOMATIC, STRONG);
    assertEquals(Mode.FULL, PerformanceMode.effective());
    assertTrue(PerformanceMode.isAutomatic());
    assertEquals("full", PerformanceMode.reportValue());
  }

  @Test
  void fullAndLiteIgnoreTheMachine() {
    PerformanceMode.resolve(Choice.LITE, STRONG);
    assertTrue(PerformanceMode.isLite());
    assertFalse(PerformanceMode.isAutomatic());

    PerformanceMode.resolve(Choice.FULL, new MachineFacts(1, 1024, 50, 250_000));
    assertFalse(PerformanceMode.isLite());
    assertFalse(PerformanceMode.isAutomatic());
  }

  @Test
  void choosingInOptionsSavesTheChoiceAndAppliesFullOrLiteNow() {
    String saved = ApplicationSettings.PERFORMANCE_MODE.getValue();
    try {
      PerformanceMode.resolve(Choice.FULL, null);
      PerformanceMode.choose(Choice.LITE);
      assertTrue(PerformanceMode.isLite());
      assertEquals("LITE", ApplicationSettings.PERFORMANCE_MODE.getValue());

      // The session started in Lite, so there is no machine reading: Automatic decides next start.
      PerformanceMode.choose(Choice.AUTOMATIC);
      assertTrue(PerformanceMode.isAutomatic());
      assertTrue(PerformanceMode.isLite());

      // A session that started on Automatic keeps its startup reading.
      PerformanceMode.resolve(Choice.AUTOMATIC, STRONG);
      PerformanceMode.choose(Choice.LITE);
      assertTrue(PerformanceMode.isLite());
      PerformanceMode.choose(Choice.AUTOMATIC);
      assertFalse(PerformanceMode.isLite());
    } finally {
      ApplicationSettings.PERFORMANCE_MODE.setValue(saved);
    }
  }

  @Test
  void fullUntilResolved() {
    assertEquals(Mode.FULL, PerformanceMode.effective());
  }

  @Test
  void unknownSettingValuesAreAutomatic() {
    assertEquals(Choice.LITE, Choice.parse("lite"));
    assertEquals(Choice.FULL, Choice.parse(" FULL "));
    assertEquals(Choice.AUTOMATIC, Choice.parse("TURBO"));
    assertEquals(Choice.AUTOMATIC, Choice.parse(null));
  }

  /** Automatic reads the machine at startup, before the theme and the warm-up depend on it. */
  @Test
  void initializerResolvesTheModeBeforeTheLookAndFeelAndTheWarmUp() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    int settings = source.indexOf("preinit();");
    int resolve = source.indexOf("PerformanceMode.resolveAtStartup();");
    int theme = source.indexOf("ThemeMediator.loadThemeAtStartup(saved);");
    int warmUp = source.indexOf("JdkWarmUp.startInBackground(");
    assertTrue(settings >= 0 && resolve > settings, "resolved before the settings load");
    assertTrue(resolve < theme && resolve < warmUp, "resolved after the theme or the warm-up");
    assertTrue(
        source.contains("PerformanceMode::warmUpJdkStepsFinished"),
        "the warm-up's time isn't recorded for Automatic");
  }
}
