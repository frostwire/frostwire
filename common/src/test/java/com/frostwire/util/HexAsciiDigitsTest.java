/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Found by HttpBlockHeaderFetcherFuzzTest: Hex.decode read digits with {@code Character.digit},
 * which also takes every other Unicode decimal digit (Arabic-Indic, Devanagari, fullwidth...) and
 * the fullwidth letters A-F, so a non-hex string decoded to the same bytes as a real hash. Hex is
 * ASCII {@code 0-9}, {@code a-f} and {@code A-F}; anything else is rejected with the documented
 * IllegalArgumentException.
 */
class HexAsciiDigitsTest {

  @Test
  void nonAsciiDigitsAreRejected() {
    // The fuzzer's block hash had one ARABIC-INDIC DIGIT ONE where a '1' belongs.
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("١" + "0"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("००")); // Devanagari 0
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("０９")); // fullwidth 0 9
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("Ａｆ")); // fullwidth A f
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("0" + "٩")); // last digit
  }

  @Test
  void asciiHexDigitsDecodeAsBefore() {
    assertArrayEquals(
        new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef},
        Hex.decode("0123456789abcdef"));
    assertArrayEquals(new byte[] {(byte) 0xab, (byte) 0xcd, (byte) 0xef}, Hex.decode("ABCDEF"));
    assertArrayEquals(new byte[] {0x09, (byte) 0xfa}, Hex.decode("09fA"));
  }

  @Test
  void asciiNeighborsOfTheDigitRangesAreStillRejected() {
    for (String s : new String[] {"/0", "0:", "@0", "G0", "`0", "g0", "0 "}) {
      assertThrows(IllegalArgumentException.class, () -> Hex.decode(s), s);
    }
  }
}
