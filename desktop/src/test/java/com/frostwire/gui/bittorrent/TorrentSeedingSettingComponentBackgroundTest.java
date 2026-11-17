/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * The explanations under the wizard's seeding choices were drawn on white strips, not on the
 * panel's background (the pixel at the end of the explanation must equal the panel's pixel beside
 * it): Nimbus paints a JTextArea's background whether or not it is opaque, so the "label-like" text
 * area covered the panel (seen in the setup wizard and Options). The text area's background is now
 * fully transparent, which Nimbus honors, and every look and feel then shows the panel through it.
 */
class TorrentSeedingSettingComponentBackgroundTest {

  @Test
  void theExplanationsShowThePanelBehindThemUnderNimbus() throws Exception {
    assertPanelShowsBehindExplanations("javax.swing.plaf.nimbus.NimbusLookAndFeel");
  }

  @Test
  void theExplanationsShowThePanelBehindThemUnderMetal() throws Exception {
    assertPanelShowsBehindExplanations("javax.swing.plaf.metal.MetalLookAndFeel");
  }

  private static void assertPanelShowsBehindExplanations(String lookAndFeelClass) throws Exception {
    LookAndFeel previous = UIManager.getLookAndFeel();
    AtomicReference<BufferedImage> image = new AtomicReference<>();
    AtomicReference<int[]> sample = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              UIManager.setLookAndFeel(lookAndFeelClass);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            TorrentSeedingSettingComponent component =
                new TorrentSeedingSettingComponent(false, false);
            JPanel host = new JPanel(new java.awt.BorderLayout());
            host.add(component);
            host.setSize(new Dimension(700, 200));
            layOut(host);
            BufferedImage img = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            host.paint(g);
            g.dispose();
            image.set(img);
            JTextArea description = component.seedingDescription();
            java.awt.Rectangle r =
                SwingUtilities.convertRectangle(
                    description.getParent(), description.getBounds(), host);
            // Right end of the text area: past the end of the (wrapped) text, so only the
            // background is there.
            // The panel itself, a few pixels left of the text area on the same row.
            sample.set(new int[] {r.x + r.width - 4, r.y + r.height - 3, r.x - 3});
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              UIManager.setLookAndFeel(previous);
            } catch (Exception ignored) {
              // best effort
            }
          });
    }
    int[] p = sample.get();
    assertEquals(
        image.get().getRGB(p[2], p[1]) & 0xFFFFFF,
        image.get().getRGB(p[0], p[1]) & 0xFFFFFF,
        lookAndFeelClass + ": the explanation hides the panel's background");
  }

  private static void layOut(java.awt.Container c) {
    c.doLayout();
    for (java.awt.Component child : c.getComponents()) {
      if (child instanceof java.awt.Container k) {
        layOut(k);
      }
    }
  }
}
