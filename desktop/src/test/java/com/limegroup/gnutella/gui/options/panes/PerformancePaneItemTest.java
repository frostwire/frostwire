/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Options &gt; Advanced &gt; Performance (frostwire/frostwire#1555). */
class PerformancePaneItemTest {
  private String saved;

  @BeforeEach
  void save() {
    saved = ApplicationSettings.PERFORMANCE_MODE.getValue();
  }

  @AfterEach
  void restore() {
    ApplicationSettings.PERFORMANCE_MODE.setValue(saved);
    PerformanceMode.resolve(Choice.FULL, null);
  }

  /** The pane itself needs a display (UISettings reads the screen size), so its logic is tested. */
  @Test
  void appliesLiteRightAwayWithoutARestart() {
    ApplicationSettings.PERFORMANCE_MODE.setValue("AUTOMATIC");
    PerformanceMode.resolve(Choice.AUTOMATIC, null);
    assertEquals(Choice.AUTOMATIC, PerformancePaneItem.savedChoice());
    assertEquals(I18n.tr("Running now: Full"), PerformancePaneItem.currentModeText());

    assertFalse(PerformancePaneItem.apply(Choice.LITE), "Lite should not ask for a restart");
    assertTrue(PerformanceMode.isLite());
    assertEquals(Choice.LITE, PerformancePaneItem.savedChoice());
    assertEquals(I18n.tr("Running now: Lite"), PerformancePaneItem.currentModeText());

    assertFalse(PerformancePaneItem.apply(Choice.FULL));
    assertFalse(PerformanceMode.isLite());
    assertEquals("FULL", ApplicationSettings.PERFORMANCE_MODE.getValue());
  }

  @Test
  void livesUnderAdvanced() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "src/main/java/com/limegroup/gnutella/gui/options/OptionsConstructor.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        source.contains(
            "addOption(ADVANCED_KEY,PERFORMANCE_KEY,I18n.tr(\"Performance\"),PerformancePaneItem.class);"),
        "the Performance pane isn't in Options > Advanced");
  }
}
