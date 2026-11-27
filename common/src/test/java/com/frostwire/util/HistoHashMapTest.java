/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HistoHashMapTest {

  @Test
  void countsAndSortsByFrequency() {
    HistoHashMap<String> histo = new HistoHashMap<>();
    assertEquals(0, histo.get("a"));
    assertEquals(0, histo.getKeyCount());
    assertEquals(1, histo.update("a"));
    assertEquals(2, histo.update("a"));
    assertEquals(1, histo.update("b"));
    for (int i = 0; i < 5; i++) histo.update("c");
    assertEquals(2, histo.get("a"));
    assertEquals(5, histo.get("c"));
    assertEquals(3, histo.getKeyCount());
    List<Map.Entry<String, Integer>> sorted = histo.histogram();
    assertEquals("c", sorted.get(0).getKey());
    assertEquals(5, sorted.get(0).getValue());
    assertEquals("a", sorted.get(1).getKey());
    assertEquals("b", sorted.get(2).getKey());
    assertThrows(UnsupportedOperationException.class, () -> sorted.remove(0));
  }

  @Test
  void resetForgetsEverything() {
    HistoHashMap<Integer> histo = new HistoHashMap<>();
    histo.update(1);
    histo.update(2);
    histo.reset();
    assertEquals(0, histo.getKeyCount());
    assertEquals(0, histo.get(1));
    assertTrue(histo.histogram().isEmpty());
    assertEquals(1, histo.update(1));
  }
}
