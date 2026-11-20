/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * The tooltip of a copyable value in the details window is plain text (frostwire/frostwire#1562):
 * it used to be {@code <html>tip<br>value</html>}, which Swing parses and lays out on the EDT each
 * time it is shown.
 */
class DistributedSearchResultCopyTipTest {

  @Test
  void aValueShownInFullIsJustTheHint() {
    DistributedSearchResultDetailsWindow.Field field =
        new DistributedSearchResultDetailsWindow.Field("Size", "12 MB", true, false);
    assertEquals(
        "Click to copy Size",
        DistributedSearchResultDetailsWindow.copyTip("Click to copy Size", field));
  }

  @Test
  void aShortenedValueGetsTheWholeValueOnTheNextLineUnescaped() {
    String value = "a<b>&" + "x".repeat(400);
    DistributedSearchResultDetailsWindow.Field field =
        new DistributedSearchResultDetailsWindow.Field("Info Hash", value, true, true);
    String tip = DistributedSearchResultDetailsWindow.copyTip("Click to copy Info Hash", field);
    assertEquals("Click to copy Info Hash\n" + value, tip);
    assertFalse(tip.startsWith("<html"), "must stay plain text");
  }
}
