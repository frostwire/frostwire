/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IceBridgeConsoleWindowTest {

  @Test
  void consoleShowsNewestEntriesAtTheTop() {
    List<String> chronological = Arrays.asList("first", "second", "third", "fourth");
    assertEquals(
        Arrays.asList("fourth", "third", "second", "first"),
        IceBridgeConsoleWindow.newestFirst(chronological, e -> true));
  }

  @Test
  void filteringKeepsNewestFirstOrder() {
    List<String> chronological = Arrays.asList("keep-1", "drop", "keep-2", "keep-3");
    assertEquals(
        Arrays.asList("keep-3", "keep-2", "keep-1"),
        IceBridgeConsoleWindow.newestFirst(chronological, e -> e.startsWith("keep")));
  }
}
