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
 * frostwire/frostwire#1399 (found by the karma chain and identity record fuzz harnesses): odd-length
 * hex from a peer threw ArrayIndexOutOfBoundsException instead of the documented
 * IllegalArgumentException.
 */
class HexTest {

  @Test
  void oddLengthHexIsRejectedWithTheDocumentedException() {
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("abc"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("a"));
  }

  @Test
  void evenLengthAndInvalidCharactersBehaveAsBefore() {
    assertArrayEquals(new byte[] {(byte) 0xab, 0x01}, Hex.decode("ab01"));
    assertArrayEquals(new byte[0], Hex.decode(""));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("zz"));
  }
}
