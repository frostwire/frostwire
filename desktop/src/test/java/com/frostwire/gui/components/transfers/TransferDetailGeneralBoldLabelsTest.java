/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The transfer detail pane's "Name:" and "complete" labels were HTML, bold through {@code <b>}, set
 * in a later EDT event that still parsed and laid them out there (frostwire/frostwire#1502). The
 * pane needs the theme to be built, so this checks its source; GUIUtils.setBoldText's behavior is
 * covered by VPNWarningLabelBoldTest.
 */
class TransferDetailGeneralBoldLabelsTest {

  @Test
  void nameAndCompleteAreBoldThroughTheirFont() throws Exception {
    String source =
        TransferDetailRevalidationTest.readSource("TransferDetailGeneral.java")
            .replaceAll("\\s+", "");

    assertTrue(source.contains("GUIUtils.setBoldText(nameLabel,"), "Name: is not set bold");
    assertTrue(source.contains("GUIUtils.setBoldText(completeLabel,"), "complete is not set bold");
    assertFalse(source.contains("\"<html><b>\""), "a bold-only HTML label is left");
  }
}
