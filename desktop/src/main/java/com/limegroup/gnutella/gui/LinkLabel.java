/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;

/**
 * A label drawn as a link: plain text in the theme's link color, underlined through its font, with
 * a hand cursor. Callers add the mouse listener that does the click. It is not an HTML link ({@code
 * <html><a href='#'>...}): an HTML label makes Swing parse it and build and style an HTML view on
 * the EDT, the first time also loading Swing's HTML and CSS support, just to underline one line of
 * text (frostwire/frostwire#1526, #1571).
 */
public class LinkLabel extends JLabel {

  /** Swing's HTML links are blue (default.css) when the look and feel names no link color. */
  private static final Color FALLBACK_LINK_COLOR = Color.BLUE;

  public LinkLabel(String text) {
    super(text);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  /** The look and feel's colors are installed again on a theme change, so is the link color. */
  @Override
  public void updateUI() {
    super.updateUI();
    setForeground(linkColor());
  }

  /** Every font this label is given is underlined: that is what marks it as a link. */
  @Override
  public void setFont(Font font) {
    if (font == null) {
      super.setFont(null);
      return;
    }
    Font underlined = underlined(font);
    // A look and feel only replaces fonts it installed itself, so keep that mark.
    super.setFont(font instanceof UIResource ? new FontUIResource(underlined) : underlined);
  }

  /** FlatLaf's link color (the one it gives HTML links too), or Swing's HTML link blue. */
  static Color linkColor() {
    Color color = UIManager.getColor("Component.linkColor");
    return color != null ? color : FALLBACK_LINK_COLOR;
  }

  /** {@code font} with a single underline. */
  static Font underlined(Font font) {
    return font.deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
  }
}
