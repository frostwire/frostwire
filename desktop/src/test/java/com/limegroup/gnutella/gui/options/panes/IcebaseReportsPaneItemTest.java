/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1565: the Options pane shows and writes the same setting the setup wizard
 * writes, and the pane is in the Options tree.
 */
class IcebaseReportsPaneItemTest {

  private static String compactSource(String path) throws Exception {
    return Files.readString(
            Path.of(System.getProperty("user.dir")).resolve(path), StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }

  /**
   * The pane's Swing parents need a screen (ThemeMediator, UISettings), so the headless tests check
   * its source: it shows the stored choice and stores the user's.
   */
  @Test
  void thePaneShowsAndWritesTheIcebaseReportsSetting() throws Exception {
    String pane =
        compactSource(
            "src/main/java/com/limegroup/gnutella/gui/options/panes/IcebaseReportsPaneItem.java");
    assertTrue(pane.contains("checkbox=newJCheckBox(IcebaseReportsText.label());"));
    assertTrue(pane.contains("super(IcebaseReportsText.paneTitle(),IcebaseReportsText.note());"));
    assertTrue(pane.contains("checkbox.setSelected(IcebaseReports.enabled());"));
    assertTrue(pane.contains("IcebaseReports.setEnabled(checkbox.isSelected());"));
  }

  @Test
  void thePaneIsInTheOptionsTree() throws Exception {
    String constructor =
        compactSource("src/main/java/com/limegroup/gnutella/gui/options/OptionsConstructor.java");
    assertTrue(
        constructor.contains(
            "addOption(ADVANCED_KEY,CRASH_REPORTS_KEY,IcebaseReportsText.optionsNodeTitle(),IcebaseReportsPaneItem.class);"));
  }
}
