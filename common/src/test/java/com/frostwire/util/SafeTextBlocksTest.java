/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1400: SafeText filtered five Unicode blocks through Character.UnicodeBlock
 * constants Android only has from API 34. It now uses code point ranges; this checks the ranges
 * still drop every code point the JDK puts in those blocks.
 */
class SafeTextBlocksTest {

  private static final List<Character.UnicodeBlock> BLOCKS =
      List.of(
          Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS,
          Character.UnicodeBlock.ORNAMENTAL_DINGBATS,
          Character.UnicodeBlock.CHESS_SYMBOLS,
          Character.UnicodeBlock.SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A,
          Character.UnicodeBlock.SYMBOLS_FOR_LEGACY_COMPUTING);

  @Test
  void everyCodePointInTheFiveBlocksIsDropped() {
    int checked = 0;
    for (int cp = 0x10000; cp <= 0x2FFFF; cp++) {
      Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
      if (block != null && BLOCKS.contains(block)) {
        String text = "a" + new String(Character.toChars(cp)) + "b";
        assertEquals("ab", SafeText.sanitize(text), "kept U+" + Integer.toHexString(cp));
        checked++;
      }
    }
    assertTrue(checked > 500, "checked only " + checked + " code points");
  }
}
