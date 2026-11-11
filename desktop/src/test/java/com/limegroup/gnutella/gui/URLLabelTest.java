/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * URLLabel used to wrap its text in {@code <html><a href=...>}, which made Swing parse it and build
 * and style an HTML view on the EDT for every link label (Icebase #ef396b31fb9d,
 * frostwire/frostwire#1526). It must show plain text, underlined through its font.
 */
class URLLabelTest {

  @Test
  void aLinkIsPlainUnderlinedTextNotAnHtmlView() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          URLLabel link =
              new URLLabel("https://www.frostwire.com/scams", "Do not pay for FrostWire.");

          assertEquals("Do not pay for FrostWire.", link.getText());
          assertNull(link.getClientProperty("html"), "the link built an HTML view");
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));
          assertEquals("https://www.frostwire.com/scams", link.getToolTipText());
          assertTrue(link.getMouseListeners().length > 0, "the link opens its URL on click");
        });
  }

  @Test
  void theLinkKeepsItsColorAndStaysUnderlinedWhenItsTextOrFontChange() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          URLLabel link = new URLLabel("https://www.frostwire.com", "");
          Color linkColor = UIManager.getColor("Label.foreground");

          link.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
          link.setText("Help Translate FrostWire");

          assertEquals("Help Translate FrostWire", link.getText());
          assertNull(link.getClientProperty("html"));
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));
          assertEquals(14, link.getFont().getSize());
          if (linkColor != null) {
            assertEquals(linkColor, link.getForeground());
          }
        });
  }

  private static Object underline(Font font) {
    return font.getAttributes().get(TextAttribute.UNDERLINE);
  }
}
