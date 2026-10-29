/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.limegroup.gnutella.gui.I18n;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1394: closing the window built the "hide FrostWire?" message as an HTML
 * label, so Swing loaded its HTML kit and laid out the text on the EDT during the window-close
 * event.
 */
class HideExitDialogMessageTest {

  @Test
  void theMessageIsPlainTextOneLabelPerLine() {
    JLabel[] labels = HideExitDialog.messageLabels();

    assertEquals(2, labels.length);
    assertEquals(
        I18n.tr("Closing the FrostWire window will only hide the application"),
        labels[0].getText());
    assertEquals(
        I18n.tr("This way file transfers may continue in the background."), labels[1].getText());
    for (JLabel label : labels) {
      assertFalse(BasicHTML.isHTMLString(label.getText()), label.getText());
      assertNull(label.getClientProperty(BasicHTML.propertyKey), "an HTML view was built");
    }
  }
}
