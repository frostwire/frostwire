/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

/**
 * The transfer details' "hide" and "Save location" links were HTML labels ({@code <html><a
 * href='#'>}), so setting their text built an HTML view on the EDT, the first time loading Swing's
 * HTML and CSS support, and froze the window (Icebase #f60b533f6b41, frostwire/frostwire#1571).
 * They must be plain text drawn as links.
 */
class TransferDetailLinksTest {

  @Test
  void theHideLinkIsPlainUnderlinedTextThatHidesTheDetailsOnClick() throws Exception {
    MouseAdapter hide = new MouseAdapter() {};
    SwingUtilities.invokeAndWait(
        () -> {
          JLabel link = TransferDetailComponent.hideLink(hide);
          assertIsAPlainLink(link);
          assertTrue(
              Arrays.asList(link.getMouseListeners()).contains(hide), "a click no longer hides");
        });
  }

  @Test
  void theSaveLocationLinkIsPlainUnderlinedText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> assertIsAPlainLink(TransferDetailGeneral.saveLocationLink()));
  }

  @Test
  void theTransferDetailPanesSetNoHtmlLinks() throws Exception {
    for (String file :
        new String[] {"TransferDetailComponent.java", "TransferDetailGeneral.java"}) {
      String source = TransferDetailRevalidationTest.readSource(file);
      assertFalse(source.contains("\"<html><a"), file + " still builds an HTML link");
    }
  }

  private static void assertIsAPlainLink(JLabel link) {
    assertNull(link.getClientProperty(BasicHTML.propertyKey), "the link built an HTML view");
    assertFalse(link.getText().contains("<"), link.getText());
    assertEquals(
        TextAttribute.UNDERLINE_ON, link.getFont().getAttributes().get(TextAttribute.UNDERLINE));
    assertEquals(Cursor.HAND_CURSOR, link.getCursor().getType());
  }
}
