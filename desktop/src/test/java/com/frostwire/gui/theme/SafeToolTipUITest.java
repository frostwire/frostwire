/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.MultilineToolTip;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JToolTip;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Icebase #497698bc3719, frostwire/frostwire#1580: a tooltip whose text starts with {@code <html>}
 * was parsed and laid out by {@code BasicHTML} on the EDT every time it was shown. Every tooltip
 * must show the plain text instead.
 */
class SafeToolTipUITest {
  private static final String VPN_TIP =
      "<html><p width=\"260\">FrostWire has detected a VPN connection, your privacy is safe from"
          + " prying eyes.</p></html>";
  private static final String SEEDING_TIP =
      "<html><b>Seeding</b><p>completed torrent downloads.</p></html>";

  private LookAndFeel previous;
  private Object previousToolTipUI;

  @BeforeEach
  void useSynthWithTheSafeToolTipUI() throws Exception {
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new NimbusLookAndFeel()); // Synth, like FrostWire's skin
    previousToolTipUI = UIManager.get("ToolTipUI");
    UIManager.put("ToolTipUI", SafeToolTipUI.class.getName()); // what ThemeMediator does
  }

  @AfterEach
  void restore() throws Exception {
    UIManager.put("ToolTipUI", previousToolTipUI);
    UIManager.setLookAndFeel(previous);
  }

  /** Shows the tip the way ToolTipManager.showTipWindow does, and paints it. */
  private static void show(JToolTip tip, String text) {
    tip.setTipText(text);
    Dimension size = tip.getPreferredSize();
    tip.setSize(size);
    tip.doLayout();
    BufferedImage image =
        new BufferedImage(
            Math.max(1, size.width), Math.max(1, size.height), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      tip.paint(g);
    } finally {
      g.dispose();
    }
  }

  @Test
  void anHtmlTooltipNeverBuildsAnHtmlView() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          for (String html : new String[] {VPN_TIP, SEEDING_TIP}) {
            JToolTip tip = new JToolTip();
            assertInstanceOf(SafeToolTipUI.class, tip.getUI());
            show(tip, html);
            assertNull(
                tip.getClientProperty(BasicHTML.propertyKey),
                "the tooltip built an HTML view tree on the EDT for " + html);
            Dimension size = tip.getPreferredSize();
            assertTrue(size.width > 0 && size.height > 0, "size " + size);
          }
        });
  }

  @Test
  void aTableTooltipNeverBuildsAnHtmlViewEither() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          MultilineToolTip tip = new MultilineToolTip();
          tip.setTipArray(new String[] {"<html><b>name</b><img src=\"http://example.com/x.png\">"});
          assertNull(tip.getClientProperty(BasicHTML.propertyKey));
        });
  }

  @Test
  void aShortPlainTooltipKeepsItsSingleLineSize() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JToolTip tip = new JToolTip();
          show(tip, "Copy Magnet");
          Dimension plain = tip.getPreferredSize();
          assertTrue(plain.width < SafeToolTipUI.MAX_WIDTH, "width " + plain.width);
          JToolTip two = new JToolTip();
          show(two, "Copy Magnet\nCopy Magnet");
          assertTrue(two.getPreferredSize().height > plain.height, "two lines are taller");
          assertEquals(plain.width, two.getPreferredSize().width, 10, "same widest line");
        });
  }

  @Test
  void aLongHtmlTooltipWrapsAtTheMaximumWidth() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JToolTip tip = new JToolTip();
          show(tip, VPN_TIP);
          Dimension size = tip.getPreferredSize();
          assertTrue(size.width <= SafeToolTipUI.MAX_WIDTH + 20, "width " + size.width);
          JToolTip one = new JToolTip();
          show(one, "FrostWire has detected a VPN connection");
          assertTrue(size.height > one.getPreferredSize().height, "the long tip wraps");
        });
  }

  /** The skin registers the UI for every tooltip, and removes it when a non-Synth theme loads. */
  @Test
  void theThemeRegistersTheSafeToolTipUIForEveryTooltip() throws Exception {
    String theme =
        java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/frostwire/gui/theme/ThemeMediator.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        theme.contains("UIManager.put(\"ToolTipUI\",\"com.frostwire.gui.theme.SafeToolTipUI\");"));
    assertTrue(theme.contains("\"ToolTipUI\"//never"), "purged with the other Synth keys");
  }
}
