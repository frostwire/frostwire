/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for the Hex.decode fix (frostwire/frostwire#1399). The boundary is the
 * length's parity: every even-length string of hex digits must decode to the same bytes as before,
 * and a non-hex character must still be rejected with IllegalArgumentException.
 */
class HexCompatibilityTest {

  @Test
  void evenLengthsDecodeToTheirBytes() {
    assertArrayEquals(new byte[0], Hex.decode(""));
    assertArrayEquals(new byte[] {0x00}, Hex.decode("00"));
    assertArrayEquals(new byte[] {(byte) 0xff}, Hex.decode("ff"));
    assertArrayEquals(new byte[] {0x7f, (byte) 0x80}, Hex.decode("7f80"));
    assertArrayEquals(
        new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef},
        Hex.decode("0123456789abcdef"));
    assertArrayEquals(new byte[] {0x12, 0x34}, Hex.decode(new char[] {'1', '2', '3', '4'}));
  }

  @Test
  void upperLowerAndMixedCaseDecodeAlike() {
    byte[] expected = {(byte) 0xab, (byte) 0xcd, (byte) 0xef};
    assertArrayEquals(expected, Hex.decode("abcdef"));
    assertArrayEquals(expected, Hex.decode("ABCDEF"));
    assertArrayEquals(expected, Hex.decode("aBcDeF"));
  }

  @Test
  void aFortyDigitInfoHashDecodesToTwentyBytes() {
    byte[] bytes = Hex.decode("DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C");
    assertEquals(20, bytes.length);
    assertEquals((byte) 0xdd, bytes[0]);
    assertEquals((byte) 0x1c, bytes[19]);
    assertEquals("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c", Hex.encode(bytes));
  }

  @Test
  void everyByteValueSurvivesARoundTrip() {
    byte[] all = new byte[256];
    for (int i = 0; i < all.length; i++) {
      all[i] = (byte) i;
    }
    String lower = Hex.encode(all);
    assertEquals(512, lower.length());
    assertArrayEquals(all, Hex.decode(lower));
    assertArrayEquals(all, Hex.decode(new String(Hex.encode(all, false))));
  }

  @Test
  void nonHexCharactersInEvenLengthInputAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("g0"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("0g"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("  "));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("abcd-1"));
  }

  /** Rejected since the fix with IllegalArgumentException; it used to be an AIOOBE. */
  @Test
  void oddLengthsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("0"));
    assertThrows(IllegalArgumentException.class, () -> Hex.decode("abcde"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Hex.decode("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c0"));
  }
}
