/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The seeding button's tooltip is plain text (frostwire/frostwire#1562): its translations carry
 * {@code <b>} and {@code <p>}, and an {@code <html>} tooltip is parsed and laid out on the EDT each
 * time it is shown.
 */
class StatusLineSeedingToolTipTest {

  @Test
  void seedingTipIsPlainTextWithTheTitleOnItsOwnLine() {
    String tip = StatusLine.seedingToolTipText(true);
    assertNoMarkup(tip);
    assertEquals("Seeding\ncompleted torrent downloads.", tip);
  }

  @Test
  void notSeedingTipIsPlainTextWithTheTitleOnItsOwnLine() {
    String tip = StatusLine.seedingToolTipText(false);
    assertNoMarkup(tip);
    assertEquals("Not Seeding\nFile chunks might be shared only during a torrent download.", tip);
  }

  private static void assertNoMarkup(String tip) {
    assertFalse(tip.indexOf('<') >= 0 || tip.indexOf('>') >= 0, "markup in tooltip: " + tip);
    assertTrue(tip.indexOf('\n') > 0, "title and explanation on separate lines: " + tip);
  }
}
