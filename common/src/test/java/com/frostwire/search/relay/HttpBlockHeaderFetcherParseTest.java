/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

/**
 * Found by HttpBlockHeaderFetcherFuzzTest: a block-height response with a non-ASCII digit in place
 * of a hex digit was accepted as a different block hash than any explorer serves, and a cached
 * block is never fetched again. The response is the hash as 64 ASCII hex digits, possibly with
 * surrounding whitespace.
 */
class HttpBlockHeaderFetcherParseTest {

  private static final String GENESIS =
      "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f";

  @Test
  void aHashWithANonAsciiDigitIsNotABlockHash() {
    // Minimized from the fuzzer's input: one ARABIC-INDIC DIGIT ONE among 63 zeros.
    assertNull(HttpBlockHeaderFetcher.parsePlainHash("١" + "0".repeat(63)));
    // The fuzzer's original: the genesis hash with one digit replaced, and a trailing newline.
    assertNull(
        HttpBlockHeaderFetcher.parsePlainHash(
            "000000000019d6689c085ae165831e934ff7c085١e165831e934f1b60a8ce26f\n"));
  }

  @Test
  void explorerResponsesStillDecode() {
    assertEquals(GENESIS, Hex.encode(HttpBlockHeaderFetcher.parsePlainHash(GENESIS + "\n")));
    assertEquals(
        GENESIS, Hex.encode(HttpBlockHeaderFetcher.parsePlainHash("  " + GENESIS.toUpperCase())));
  }

  @Test
  void anythingButSixtyFourDigitsIsNotABlockHash() {
    assertNull(HttpBlockHeaderFetcher.parsePlainHash(null));
    assertNull(HttpBlockHeaderFetcher.parsePlainHash(""));
    assertNull(HttpBlockHeaderFetcher.parsePlainHash(GENESIS.substring(1)));
    assertNull(HttpBlockHeaderFetcher.parsePlainHash(GENESIS + "0"));
    assertNull(HttpBlockHeaderFetcher.parsePlainHash("<html>503 Service Unavailable</html>"));
  }
}
