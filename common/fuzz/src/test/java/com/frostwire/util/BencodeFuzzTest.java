/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Arrays;

/**
 * Fuzzes {@link Bencode#decode(byte[])}, the pure-Java canonical bencode reader that parses karma
 * chain manifests and identity records other peers publish in the DHT (and anything else handed to
 * it as untrusted bytes).
 *
 * <p>The decoder documents {@link IllegalArgumentException} for every malformed, non-canonical or
 * over-limit input. It accepts only canonical bencode, so for every input it accepts, {@link
 * Bencode#encode} of the result must give back exactly the input. Findings: any other exception or
 * error (a {@code StackOverflowError} from nesting, an {@code OutOfMemoryError} from a declared
 * length), a hang, or an accepted input that doesn't round-trip.
 */
class BencodeFuzzTest {

  @FuzzTest
  void decode(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    Object value;
    try {
      value = Bencode.decode(input);
    } catch (IllegalArgumentException documented) {
      return;
    }
    byte[] encoded = Bencode.encode(value);
    if (!Arrays.equals(input, encoded)) {
      throw new AssertionError("decode/encode round trip changed the bytes");
    }
  }
}
