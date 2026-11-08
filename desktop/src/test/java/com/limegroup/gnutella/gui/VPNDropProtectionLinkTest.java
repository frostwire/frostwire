/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Hovering the status bar's "VPN" link used to set its text to {@code <html><u>VPN</u></html>},
 * which made Swing build and style an HTML view on the EDT on every hover (Icebase #90481940a5e8,
 * frostwire/frostwire#1500). The link must be underlined through its font, with its text left
 * alone.
 */
class VPNDropProtectionLinkTest {

  @Test
  void hoveringTheVpnLinkUnderlinesItWithoutHtml() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JLabel link = vpnLink(new VPNDropProtectionCheckbox());
          String text = link.getText();
          assertEquals(null, underline(link.getFont()), "underlined before hovering");

          hover(link, MouseEvent.MOUSE_ENTERED);
          assertEquals(text, link.getText());
          assertNull(link.getClientProperty("html"), "the link built an HTML view on hover");
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));

          hover(link, MouseEvent.MOUSE_EXITED);
          assertEquals(text, link.getText());
          assertNull(link.getClientProperty("html"));
          assertEquals(null, underline(link.getFont()), "still underlined after the mouse left");
        });
  }

  @Test
  void underlinedKeepsTheFontsFamilyStyleAndSize() {
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    Font underlined = VPNDropProtectionCheckbox.underlined(font);
    assertEquals(font.getFamily(), underlined.getFamily());
    assertEquals(font.getStyle(), underlined.getStyle());
    assertEquals(font.getSize(), underlined.getSize());
    assertEquals(TextAttribute.UNDERLINE_ON, underline(underlined));
  }

  private static Object underline(Font font) {
    return font.getAttributes().get(TextAttribute.UNDERLINE);
  }

  private static void hover(JLabel link, int id) {
    link.dispatchEvent(new MouseEvent(link, id, System.currentTimeMillis(), 0, 1, 1, 0, false));
  }

  private static JLabel vpnLink(VPNDropProtectionCheckbox panel) {
    for (Component child : panel.getComponents()) {
      if (child instanceof JLabel label && label.getCursor().getType() == Cursor.HAND_CURSOR) {
        return label;
      }
    }
    return fail("no link label in " + panel);
  }
}
