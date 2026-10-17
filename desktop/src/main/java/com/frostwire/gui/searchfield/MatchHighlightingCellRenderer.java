/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.limewire.util.LCS;

/**
 * Renders auto-complete entries with the part matching the typed query in bold, without HTML.
 *
 * <p>Suggestions used to be {@code <html>...<b>...</b></html>} strings, so the default renderer
 * built an HTML document view for every entry on every size pass and repaint; on slow machines that
 * stalled the EDT for seconds. This renderer wraps the look-and-feel's renderer, copies its colors,
 * font and border, and paints the plain text with matched runs in bold.
 */
final class MatchHighlightingCellRenderer extends JComponent implements ListCellRenderer<String> {
  private static final Object DESKTOP_TEXT_HINTS =
      Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");

  private final ListCellRenderer<? super String> delegate;
  private final Supplier<String> query;
  private String text = "";
  private boolean[] bold;
  private Dimension preferredSize = new Dimension();

  MatchHighlightingCellRenderer(ListCellRenderer<? super String> delegate, Supplier<String> query) {
    this.delegate = delegate;
    this.query = query;
  }

  @Override
  public Component getListCellRendererComponent(
      JList<? extends String> list,
      String value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {
    Component component =
        delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    text = value == null ? "" : value;
    bold = highlightMask(query.get(), text);
    if (bold == null) {
      return component;
    }
    setFont(component.getFont());
    setForeground(component.getForeground());
    setBackground(component.getBackground());
    setOpaque(component.isOpaque());
    setBorder(component instanceof JComponent jc ? jc.getBorder() : null);
    preferredSize = widenForBold(component.getPreferredSize());
    return this;
  }

  /** Returns the characters to bold, or {@code null} when nothing in {@code text} matches. */
  static boolean[] highlightMask(String query, String text) {
    if (query == null || query.isBlank() || text.isEmpty()) {
      return null;
    }
    boolean[] mask = LCS.matchMask(query.trim(), text);
    for (boolean matched : mask) {
      if (matched) {
        return mask;
      }
    }
    return null;
  }

  private Dimension widenForBold(Dimension plain) {
    Font font = getFont();
    if (font == null) {
      return plain;
    }
    StringBuilder boldChars = new StringBuilder();
    for (int i = 0; i < bold.length; i++) {
      if (bold[i]) {
        boldChars.append(text.charAt(i));
      }
    }
    String matched = boldChars.toString();
    int extra =
        getFontMetrics(font.deriveFont(Font.BOLD)).stringWidth(matched)
            - getFontMetrics(font).stringWidth(matched);
    return new Dimension(plain.width + Math.max(0, extra), plain.height);
  }

  @Override
  public Dimension getPreferredSize() {
    return preferredSize;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      if (isOpaque()) {
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
      if (DESKTOP_TEXT_HINTS instanceof Map<?, ?> hints) {
        g2.addRenderingHints(hints);
      } else {
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }
      Font font = getFont();
      AttributedString styled = new AttributedString(text);
      styled.addAttribute(TextAttribute.FONT, font);
      Font boldFont = font.deriveFont(Font.BOLD);
      for (int i = 0; i < bold.length; i++) {
        if (bold[i]) {
          int end = i;
          while (end < bold.length && bold[end]) {
            end++;
          }
          styled.addAttribute(TextAttribute.FONT, boldFont, i, end);
          i = end - 1;
        }
      }
      Insets insets = getInsets();
      FontMetrics metrics = g2.getFontMetrics(font);
      int innerHeight = getHeight() - insets.top - insets.bottom;
      int baseline = insets.top + (innerHeight - metrics.getHeight()) / 2 + metrics.getAscent();
      g2.setColor(getForeground());
      g2.drawString(styled.getIterator(), insets.left, baseline);
    } finally {
      g2.dispose();
    }
  }

  // Renderer stamps never need layout, repaint or change notifications (as
  // DefaultListCellRenderer).

  @Override
  public void validate() {}

  @Override
  public void invalidate() {}

  @Override
  public void revalidate() {}

  @Override
  public void repaint(long tm, int x, int y, int width, int height) {}

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
}
