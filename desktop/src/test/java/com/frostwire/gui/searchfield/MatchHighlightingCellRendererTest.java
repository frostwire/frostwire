/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;
import org.limewire.util.LCS;

class MatchHighlightingCellRendererTest {

  @Test
  void matchMaskFlagsTheTypedRunIgnoringCase() {
    boolean[] mask = LCS.matchMask("FROST", "frostwire");

    assertArrayEquals(
        new boolean[] {true, true, true, true, true, false, false, false, false}, mask);
  }

  @Test
  void matchMaskDropsRunsShorterThanFourCharacters() {
    boolean[] mask = LCS.matchMask("abc", "abcdef");

    for (boolean matched : mask) {
      assertFalse(matched);
    }
  }

  @Test
  void matchMaskHasOneFlagPerTextCharacter() {
    assertEquals(0, LCS.matchMask("query", "").length);
    assertEquals("music video".length(), LCS.matchMask("music", "music video").length);
  }

  @Test
  void highlightMaskIsNullWhenNothingToBold() {
    assertNull(MatchHighlightingCellRenderer.highlightMask(null, "frostwire"));
    assertNull(MatchHighlightingCellRenderer.highlightMask("  ", "frostwire"));
    assertNull(MatchHighlightingCellRenderer.highlightMask("zzzz", "frostwire"));
  }

  @Test
  void unmatchedEntriesUseTheLookAndFeelComponent() {
    DefaultListCellRenderer delegate = new DefaultListCellRenderer();
    MatchHighlightingCellRenderer renderer =
        new MatchHighlightingCellRenderer(delegate, () -> "zzzz");

    Component component =
        renderer.getListCellRendererComponent(new JList<String>(), "frostwire", 0, false, false);

    assertSame(delegate, component);
    assertEquals("frostwire", ((JLabel) component).getText());
  }

  @Test
  void matchedEntriesArePaintedWithoutHtmlAndKeepTheDelegateLook() {
    DefaultListCellRenderer delegate = new DefaultListCellRenderer();
    MatchHighlightingCellRenderer renderer =
        new MatchHighlightingCellRenderer(delegate, () -> "frost");
    JList<String> list = new JList<>();
    list.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));

    Component component = renderer.getListCellRendererComponent(list, "frostwire", 0, true, false);

    assertSame(renderer, component);
    assertFalse(delegate.getText().startsWith("<html"));
    assertEquals(delegate.getBackground(), renderer.getBackground());
    assertEquals(delegate.getForeground(), renderer.getForeground());
    assertTrue(renderer.getPreferredSize().width >= delegate.getPreferredSize().width);
  }

  /** Icebase #cb220c7b852e: every repaint of the suggestion list re-shaped every entry. */
  @Test
  void repaintsReuseTheShapedTextOfEachEntry() {
    MatchHighlightingCellRenderer renderer =
        new MatchHighlightingCellRenderer(new DefaultListCellRenderer(), () -> "frost");
    JList<String> list = new JList<>();
    list.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
    BufferedImage image = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);

    for (int paint = 0; paint < 100; paint++) {
      for (String entry : new String[] {"frostwire", "frostwire plus", "frosted"}) {
        paintCell(renderer, list, entry, image);
      }
    }

    assertEquals(3, renderer.layoutsBuilt());
  }

  @Test
  void cachedLayoutsDrawTheSamePixelsAsAnAttributedDrawString() {
    Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    MatchHighlightingCellRenderer renderer =
        new MatchHighlightingCellRenderer(new DefaultListCellRenderer(), () -> "frost");
    JList<String> list = new JList<>();
    list.setFont(font);
    BufferedImage painted = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
    paintCell(renderer, list, "frostwire plus", painted);
    paintCell(renderer, list, "frostwire plus", painted);

    BufferedImage expected = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = expected.createGraphics();
    try {
      if (renderer.isOpaque()) {
        g.setColor(renderer.getBackground());
        g.fillRect(0, 0, renderer.getWidth(), renderer.getHeight());
      }
      Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
      if (hints instanceof Map<?, ?> map) {
        g.addRenderingHints(map);
      } else {
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }
      Font rendererFont = renderer.getFont();
      AttributedString styled = new AttributedString("frostwire plus");
      styled.addAttribute(TextAttribute.FONT, rendererFont);
      styled.addAttribute(TextAttribute.FONT, rendererFont.deriveFont(Font.BOLD), 0, 5);
      Insets insets = renderer.getInsets();
      FontMetrics metrics = g.getFontMetrics(rendererFont);
      int innerHeight = renderer.getHeight() - insets.top - insets.bottom;
      int baseline = insets.top + (innerHeight - metrics.getHeight()) / 2 + metrics.getAscent();
      g.setColor(renderer.getForeground());
      g.drawString(styled.getIterator(), insets.left, baseline);
    } finally {
      g.dispose();
    }

    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++) {
        assertEquals(expected.getRGB(x, y), painted.getRGB(x, y), "pixel " + x + "," + y);
      }
    }
  }

  private static void paintCell(
      MatchHighlightingCellRenderer renderer, JList<String> list, String entry, BufferedImage img) {
    Component component = renderer.getListCellRendererComponent(list, entry, 0, false, false);
    assertSame(renderer, component);
    renderer.setSize(img.getWidth(), img.getHeight());
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(new Color(0, 0, 0, 0));
      g.setComposite(java.awt.AlphaComposite.Src);
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.setComposite(java.awt.AlphaComposite.SrcOver);
      renderer.paint(g);
    } finally {
      g.dispose();
    }
  }
}
