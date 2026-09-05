/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DashMuxTest {

  @Test
  void supportedPairs() {
    assertTrue(DashMux.supportsPair("mp4", "m4a"));
    assertTrue(DashMux.supportsPair("mp4", "mp4"));
    assertTrue(DashMux.supportsPair("webm", "webm"));
    assertTrue(DashMux.supportsPair("MP4", "M4A"));
  }

  @Test
  void unsupportedPairs() {
    assertFalse(DashMux.supportsPair("mp4", "webm"));
    assertFalse(DashMux.supportsPair("webm", "m4a"));
    assertFalse(DashMux.supportsPair("webm", null));
    assertFalse(DashMux.supportsPair(null, "m4a"));
    assertFalse(DashMux.supportsPair("avi", "mp3"));
  }
}
