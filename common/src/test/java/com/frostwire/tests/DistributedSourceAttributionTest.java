/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.DistributedSourceAttribution;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistributedSourceAttributionTest {

  @Test
  void storesAndReadsOnlyOneValidSourceIdentity() {
    Map<String, String> extra = new HashMap<>();
    byte[] source = new byte[32];
    source[0] = 0x42;
    byte[] replacement = new byte[32];
    replacement[0] = 0x24;

    assertTrue(DistributedSourceAttribution.putIfAbsent(extra, source));
    assertArrayEquals(source, DistributedSourceAttribution.get(extra));
    assertFalse(DistributedSourceAttribution.putIfAbsent(extra, replacement));
    assertArrayEquals(source, DistributedSourceAttribution.get(extra));
    assertFalse(DistributedSourceAttribution.putIfAbsent(extra, new byte[31]));
    extra.put(DistributedSourceAttribution.EXTRA_KEY, "not-base64!");
    assertNull(DistributedSourceAttribution.get(extra));
  }
}
