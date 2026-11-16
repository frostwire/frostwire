/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.font.TextAttribute;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

/**
 * A LinkLabel is plain text drawn as a link, never an HTML view, which Swing builds on the EDT
 * (frostwire/frostwire#1571).
 */
class LinkLabelTest {

  @Test
  void aLinkIsPlainUnderlinedTextInTheLinkColorWithAHandCursor() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LinkLabel link = new LinkLabel("hide");

          assertEquals("hide", link.getText());
          assertNull(link.getClientProperty(BasicHTML.propertyKey), "the link built an HTML view");
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));
          assertEquals(LinkLabel.linkColor(), link.getForeground());
          assertEquals(Cursor.HAND_CURSOR, link.getCursor().getType());
        });
  }

  @Test
  void theLinkStaysUnderlinedAndColoredWhenItsFontOrTheLookAndFeelChange() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LinkLabel link = new LinkLabel("Save location:");

          link.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));
          assertEquals(13, link.getFont().getSize());

          link.updateUI();
          assertEquals(TextAttribute.UNDERLINE_ON, underline(link.getFont()));
          assertEquals(LinkLabel.linkColor(), link.getForeground());
        });
  }

  private static Object underline(Font font) {
    return font.getAttributes().get(TextAttribute.UNDERLINE);
  }
}
