/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.util.Hex;
import java.util.Locale;

/**
 * Fuzzes {@link HttpBlockHeaderFetcher#parsePlainHash}, which reads the Bitcoin block hash that
 * anchors karma epochs out of an HTTP response body (Blockstream's and mempool.space's {@code
 * /api/block-height/<h>}: the hash as 64 hex digits, possibly with surrounding whitespace).
 *
 * <p>Who sends it: the HTTPS block explorers, or anything between FrostWire and them that can
 * answer the request (a captive portal, a proxy). The body is any text.
 *
 * <p>The method documents no exception and returns null for anything that isn't a hash. A finding
 * is anything thrown, an input taking seconds, or a non-null result that isn't exactly the 32 bytes
 * the trimmed body spells in hex: a hash the explorers never sent, cached for good, since a cached
 * block is never fetched again.
 */
class HttpBlockHeaderFetcherFuzzTest {

  @FuzzTest
  void parsePlainHash(FuzzedDataProvider data) {
    String response = data.consumeRemainingAsString();
    byte[] hash = HttpBlockHeaderFetcher.parsePlainHash(response);
    if (hash == null) {
      return;
    }
    if (hash.length != 32) {
      throw new IllegalStateException(hash.length + "-byte block hash");
    }
    if (!Hex.encode(hash).equals(response.trim().toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException(
          "block hash " + Hex.encode(hash) + " is not the response [" + response.trim() + "]");
    }
  }
}
