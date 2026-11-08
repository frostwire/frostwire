/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.util.OSUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1484 (Icebase #36017b212d11): on a Mac whose Lucida Grande font is missing or
 * disabled, the JDK never registers its logical fonts, and FrostWire crashed with a
 * NullPointerException in Font.getFont2D while laying out the splash screen. The macOS tests run
 * {@link MissingLucidaGrandeFixture} in a child JVM that is put in that state: first without the
 * repair, to show the setup reproduces the crash, then with it.
 */
class MacLogicalFontsTest {

  @Test
  void withoutLucidaGrandeMeasuringTextCrashesAsReported() throws Exception {
    assumeTrue(OSUtils.isMacOSX(), "the JDK builds its logical fonts from Lucida Grande on macOS");
    assertEquals(
        "CRASH java.lang.NullPointerException java.awt.Font.getFont2D",
        runFixture("unrepaired"),
        "the simulated Mac without Lucida Grande no longer reproduces the reported crash");
  }

  @Test
  void theRepairedLogicalFontsMeasureTextWithoutLucidaGrande() throws Exception {
    assumeTrue(OSUtils.isMacOSX(), "the JDK builds its logical fonts from Lucida Grande on macOS");
    assertEquals("OK", runFixture("repaired"));
  }

  /**
   * macOS 27 (Icebase #8994426028bf): a native font listing that ended early leaves Lucida Grande's
   * substitutes unregistered too, so the repair has to register a family itself.
   */
  @Test
  void withAFontListingThatEndedEarlyMeasuringTextCrashesAsReported() throws Exception {
    assumeTrue(OSUtils.isMacOSX(), "the JDK builds its logical fonts from Lucida Grande on macOS");
    assertEquals(
        "CRASH java.lang.NullPointerException java.awt.Font.getFont2D",
        runFixture("unlisted-unrepaired"),
        "the simulated unfinished font listing no longer reproduces the reported crash");
  }

  @Test
  void theRepairRegistersAFamilyWhenTheFontListingEndedEarly() throws Exception {
    assumeTrue(OSUtils.isMacOSX(), "the JDK builds its logical fonts from Lucida Grande on macOS");
    assertEquals("OK", runFixture("unlisted-repaired"));
  }

  /** Every family the logical fonts are built from can be registered by its faces' names. */
  @Test
  void everySourceFamilyHasItsFourFaces() {
    for (String family : PlatformFonts.MAC_DIALOG_SUBSTITUTES) {
      assertEquals(4, MacLogicalFonts.FACES.get(family).size(), family);
    }
    assertEquals(4, MacLogicalFonts.FACES.get("Times").size());
    assertEquals(4, MacLogicalFonts.FACES.get("Menlo").size());
  }

  /**
   * A launcher without access to sun.font cannot rebuild the logical fonts; FrostWire's own still
   * work.
   */
  @Test
  void withoutTheRepairFrostWiresOwnFontsStillMeasure() throws Exception {
    assumeTrue(OSUtils.isMacOSX(), "the JDK builds its logical fonts from Lucida Grande on macOS");
    assertEquals("OK Helvetica Neue", runFixture("fallback"));
  }

  /**
   * Runs the fixture in a fresh JVM like this one, with access to sun.font, and returns its line.
   */
  private static String runFixture(String mode) throws IOException, InterruptedException {
    Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    Process child =
        new ProcessBuilder(
                List.of(
                    java.toString(),
                    "-Djava.awt.headless=true",
                    "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                    "-cp",
                    System.getProperty("java.class.path"),
                    MissingLucidaGrandeFixture.class.getName(),
                    mode))
            .redirectErrorStream(true)
            .start();
    assertTrue(child.waitFor(60, TimeUnit.SECONDS), "the fixture JVM did not finish");
    String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return output
        .lines()
        .filter(
            line ->
                line.startsWith("OK")
                    || line.startsWith("CRASH")
                    || line.startsWith("SETUP")
                    || line.startsWith("REPAIR"))
        .findFirst()
        .orElse("no result; output:\n" + output);
  }
}
