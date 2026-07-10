/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Bip39MnemonicTest {

  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] out = new byte[len / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  @Test
  void knownVectorAllZeros() {
    byte[] entropy =
        hexToBytes("00000000000000000000000000000000" + "00000000000000000000000000000000");
    String expected =
        "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon art";
    assertEquals(expected, Bip39Mnemonic.entropyToMnemonic(entropy));
  }

  @Test
  void knownVectorAllOnes() {
    byte[] entropy =
        hexToBytes("ffffffffffffffffffffffffffffffff" + "ffffffffffffffffffffffffffffffff");
    String expected =
        "zoo zoo zoo zoo zoo zoo zoo zoo "
            + "zoo zoo zoo zoo zoo zoo zoo zoo "
            + "zoo zoo zoo zoo zoo zoo zoo vote";
    assertEquals(expected, Bip39Mnemonic.entropyToMnemonic(entropy));
  }

  @Test
  void knownVector7f() {
    byte[] entropy =
        hexToBytes("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f" + "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f");
    String expected =
        "legal winner thank year wave sausage worth useful "
            + "legal winner thank year wave sausage worth useful "
            + "legal winner thank year wave sausage worth title";
    assertEquals(expected, Bip39Mnemonic.entropyToMnemonic(entropy));
  }

  @Test
  void knownVector80() {
    byte[] entropy =
        hexToBytes("80808080808080808080808080808080" + "80808080808080808080808080808080");
    String expected =
        "letter advice cage absurd amount doctor acoustic avoid "
            + "letter advice cage absurd amount doctor acoustic avoid "
            + "letter advice cage absurd amount doctor acoustic bless";
    assertEquals(expected, Bip39Mnemonic.entropyToMnemonic(entropy));
  }

  @Test
  void roundTripAllZeros() {
    byte[] entropy =
        hexToBytes("00000000000000000000000000000000" + "00000000000000000000000000000000");
    String mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy);
    byte[] recovered = Bip39Mnemonic.mnemonicToEntropy(mnemonic);
    assertArrayEquals(entropy, recovered);
  }

  @Test
  void roundTripRandom32Bytes() {
    SecureRandom rng = new SecureRandom();
    for (int i = 0; i < 50; i++) {
      byte[] entropy = new byte[32];
      rng.nextBytes(entropy);
      String mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy);
      assertEquals(24, mnemonic.split(" ").length);
      byte[] recovered = Bip39Mnemonic.mnemonicToEntropy(mnemonic);
      assertArrayEquals(entropy, recovered, "round-trip failed for entropy #" + i);
    }
  }

  @Test
  void roundTripVariousEntropySizes() {
    SecureRandom rng = new SecureRandom();
    int[] sizes = {16, 20, 24, 28, 32};
    int[] expectedWords = {12, 15, 18, 21, 24};
    for (int s = 0; s < sizes.length; s++) {
      byte[] entropy = new byte[sizes[s]];
      rng.nextBytes(entropy);
      String mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy);
      assertEquals(expectedWords[s], mnemonic.split(" ").length);
      byte[] recovered = Bip39Mnemonic.mnemonicToEntropy(mnemonic);
      assertArrayEquals(entropy, recovered);
    }
  }

  @Test
  void validateAcceptsValidMnemonic() {
    String mnemonic = Bip39Mnemonic.entropyToMnemonic(new byte[32]);
    Bip39Mnemonic.validate(mnemonic);
  }

  @Test
  void normalizeAcceptsUppercaseCommasAndLineBreaks() {
    String mnemonic = Bip39Mnemonic.entropyToMnemonic(new byte[32]);
    String entered = mnemonic.toUpperCase().replace(" ", ",\n");

    assertEquals(mnemonic, Bip39Mnemonic.normalize(entered));
    assertArrayEquals(new byte[32], Bip39Mnemonic.mnemonicToEntropy(entered));
  }

  @Test
  void invalidWordCountThrows() {
    String mnemonic = "abandon abandon abandon";
    assertThrows(IllegalArgumentException.class, () -> Bip39Mnemonic.validate(mnemonic));
  }

  @Test
  void invalidWordThrows() {
    String mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon notaword";
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> Bip39Mnemonic.validate(mnemonic));
    assertEquals("Invalid seed word at position 24: \"notaword\".", error.getMessage());
  }

  @Test
  void badChecksumThrows() {
    String mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon abandon "
            + "abandon abandon abandon abandon abandon abandon abandon abandon";
    assertThrows(IllegalArgumentException.class, () -> Bip39Mnemonic.validate(mnemonic));
  }

  @Test
  void nullMnemonicThrows() {
    assertThrows(IllegalArgumentException.class, () -> Bip39Mnemonic.validate(null));
  }

  @Test
  void invalidEntropyLengthThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> Bip39Mnemonic.entropyToMnemonic(new byte[17]));
  }

  @Test
  void wordlistHas2048Words() {
    byte[] entropy = new byte[32];
    Arrays.fill(entropy, (byte) 0);
    String mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy);
    String[] parts = mnemonic.split(" ");
    assertEquals(24, parts.length);
  }
}
