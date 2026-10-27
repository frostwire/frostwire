/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.io.StringReader;
import java.util.Locale;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import org.junit.jupiter.api.Test;

/**
 * Swing's HTML renderer asks for the first family of a {@code <font face>} list as is. A family the
 * system lacks ("arial" on most Linux systems) made the JDK open every installed font file on the
 * EDT when the Tip of the Day was laid out (Icebase #d45a8ea5abbe, GitHub #1313).
 */
class TipOfTheDayHtmlFontTest {
  private static final Set<String> LOGICAL_FAMILIES =
      Set.of("dialog", "dialoginput", "serif", "sansserif", "monospaced");

  @Test
  void tipTextAsksSwingForALogicalFontFamily() throws Exception {
    String tip = "FrostWire tip";
    HTMLEditorKit kit = new HTMLEditorKit();
    HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
    kit.read(new StringReader(TipOfTheDayMediator.tipHtml("000000", tip)), doc, 0);

    String text = doc.getText(0, doc.getLength());
    AttributeSet attributes = doc.getCharacterElement(text.indexOf(tip)).getAttributes();
    AttributeSet fontTag =
        assertInstanceOf(AttributeSet.class, attributes.getAttribute(HTML.Tag.FONT));
    String face = (String) fontTag.getAttribute(HTML.Attribute.FACE);
    // CSS.FontFamily uses the first entry of the list and never checks the rest.
    String requested = face.split(",")[0].trim();

    assertTrue(
        LOGICAL_FAMILIES.contains(requested.toLowerCase(Locale.ROOT)),
        "Tip HTML asks for physical font family \"" + requested + "\"");
    assertEquals(Font.SANS_SERIF, requested);
  }
}
