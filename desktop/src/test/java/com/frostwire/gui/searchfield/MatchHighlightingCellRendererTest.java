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

import java.awt.Component;
import java.awt.Font;
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
}
