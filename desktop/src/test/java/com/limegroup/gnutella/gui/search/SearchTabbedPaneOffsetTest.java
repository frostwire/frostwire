/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Pressing the tab-switch shortcut with no search tab open took a remainder by zero: an
 * ArithmeticException in SearchTabbedPane.switchToTabByOffset (Icebase #920f70bc4d73, build 338).
 */
class SearchTabbedPaneOffsetTest {

  @Test
  void noTabsMeansNoIndexInsteadOfADivisionByZero() {
    assertEquals(-1, SearchTabbedPane.tabIndexByOffset(-1, 1, 0));
    assertEquals(-1, SearchTabbedPane.tabIndexByOffset(0, -1, 0));
  }

  @Test
  void theIndexWrapsAroundBothEnds() {
    assertEquals(1, SearchTabbedPane.tabIndexByOffset(0, 1, 3));
    assertEquals(0, SearchTabbedPane.tabIndexByOffset(2, 1, 3));
    assertEquals(2, SearchTabbedPane.tabIndexByOffset(0, -1, 3));
    assertEquals(1, SearchTabbedPane.tabIndexByOffset(-1, 1, 3), "nothing selected counts as 0");
  }

  @Test
  void switchingWithoutTabsDoesNothing() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SearchTabbedPane pane = new SearchTabbedPane();
          pane.switchToTabByOffset(1);
          pane.switchToTabByOffset(-1);
          assertEquals(-1, pane.getSelectedIndex());
        });
  }
}
