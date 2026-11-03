/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Table renderers sanitize every cell's text on every paint, and SafeText normalized and copied
 * even plain ASCII names, which it never changes (frostwire/frostwire#1477, Icebase #a2fec79d2180).
 */
class SafeTextAsciiTest {

  @Test
  void printableAsciiComesBackAsTheSameStringWithoutACopy() {
    StringBuilder all = new StringBuilder("\t\n\r");
    for (char c = ' '; c <= '~'; c++) {
      all.append(c);
    }
    String ascii = all.toString();
    assertSame(ascii, SafeText.sanitize(ascii));
    String name = "Ubuntu 24.04 LTS (64-bit) [x86_64].iso";
    assertSame(name, SafeText.sanitize(name));
  }

  @Test
  void textThatNeedsFilteringOrNormalizingStillGetsIt() {
    assertEquals("ab", SafeText.sanitize("a\u0000b"));
    assertEquals("ab", SafeText.sanitize("a\u200Bb"));
    assertEquals("caf\u00E9", SafeText.sanitize("cafe\u0301"));
    assertEquals("Song ", SafeText.sanitize("Song \uD83D\uDE00"));
  }
}
