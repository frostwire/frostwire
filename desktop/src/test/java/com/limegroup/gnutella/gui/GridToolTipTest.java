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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Icebase #a9acc27ec32d, frostwire/frostwire#1331: the status bar's bandwidth tooltip was an HTML
 * table, which {@code SynthToolTipUI} parsed into a CSS table view tree on the EDT on every hover.
 * The tooltip must show the same rows without going through {@code BasicHTML}.
 */
class GridToolTipTest {

  private LookAndFeel previous;

  @BeforeEach
  void useSynthLookAndFeel() throws Exception {
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new NimbusLookAndFeel()); // Synth, like FrostWire's skin
  }

  @AfterEach
  void restoreLookAndFeel() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  /** Shows the tip the way ToolTipManager.showTipWindow does. */
  private static JToolTip showBandwidthTip() {
    JToolTip tip = StatusLine.createBandwidthToolTip(new JLabel());
    tip.setTipText(
        StatusLine.bandwidthToolTipText(3, "1.2 MB/s", 12, "345.6 KB/s", "1.5 GB", "800 MB"));
    tip.setSize(tip.getPreferredSize());
    tip.doLayout();
    return tip;
  }

  @Test
  void bandwidthToolTipIsNotRenderedAsHtml() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JToolTip tip = showBandwidthTip();
          assertFalse(BasicHTML.isHTMLString(tip.getTipText()), tip.getTipText());
          assertNull(
              tip.getClientProperty(BasicHTML.propertyKey),
              "the tooltip built an HTML view tree on the EDT");
        });
  }

  @Test
  void bandwidthToolTipLaysOutEveryRowAsPlainCells() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JToolTip tip = showBandwidthTip();
          assertEquals(12, tip.getComponentCount(), "4 + 4 + 2 + 2 cells");
          for (Component cell : tip.getComponents()) {
            assertNull(((JLabel) cell).getClientProperty(BasicHTML.propertyKey));
          }
          JLabel downRate = (JLabel) tip.getComponent(3);
          assertEquals("1.2 MB/s", downRate.getText());
          assertEquals(SwingConstants.RIGHT, downRate.getHorizontalAlignment());
          JLabel upRate = (JLabel) tip.getComponent(7);
          assertEquals(
              upRate.getX() + upRate.getWidth(),
              downRate.getX() + downRate.getWidth(),
              "rates share a right edge");

          Dimension size = tip.getPreferredSize();
          int lineHeight = downRate.getPreferredSize().height;
          assertTrue(size.height >= 4 * lineHeight, "four rows tall: " + size);
        });
  }

  @Test
  void paintsUnderSynthWithoutThePlainTipText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JToolTip tip = showBandwidthTip();
          BufferedImage image =
              new BufferedImage(tip.getWidth(), tip.getHeight(), BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();
          try {
            tip.paint(g);
          } finally {
            g.dispose();
          }
          assertTrue(tip.getTipText().contains("1.2 MB/s"), "tip text kept for accessibility");
        });
  }
}
