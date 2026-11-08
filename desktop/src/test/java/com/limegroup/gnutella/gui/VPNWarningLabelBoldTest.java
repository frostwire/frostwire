/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The status bar's "VPN Off: BitTorrent disabled" warning was an HTML label, bold through {@code
 * <b>}: each time it was added to the status bar Swing rebuilt its HTML view and measured every
 * glyph on the EDT (Icebase #6e26876cc964, frostwire/frostwire#1502).
 */
class VPNWarningLabelBoldTest {

  @Test
  void theVpnWarningIsBoldPlainText() throws Exception {
    AtomicReference<JLabel> warning = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            Field field = VPNStatusButton.class.getDeclaredField("vpnDropGuardLabel");
            field.setAccessible(true);
            warning.set((JLabel) field.get(new VPNStatusButton()));
          } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
          }
        });
    // Let any text set in a later event land before checking it.
    SwingUtilities.invokeAndWait(() -> {});

    JLabel label = warning.get();
    assertFalse(label.getText().isEmpty(), "the warning has its text right away");
    assertFalse(label.getText().toLowerCase().contains("<html"), "the warning is not HTML");
    assertNull(label.getClientProperty("html"), "Swing built an HTML view for the warning");
    assertTrue(label.getFont().isBold(), "the warning is bold through its font");
  }

  @Test
  void setBoldTextKeepsTheFontsFamilyAndSize() {
    JLabel label = new JLabel();
    Font plain = label.getFont();

    GUIUtils.setBoldText(label, "Name:");

    assertEquals("Name:", label.getText());
    assertTrue(label.getFont().isBold());
    assertEquals(plain.getFamily(), label.getFont().getFamily());
    assertEquals(plain.getSize2D(), label.getFont().getSize2D());
  }
}
