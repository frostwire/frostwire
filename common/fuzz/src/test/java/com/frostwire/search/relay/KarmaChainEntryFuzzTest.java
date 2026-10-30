/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.HexFormat;

/**
 * Fuzzes {@link KarmaChainEntry#fromBencode(byte[], byte[])} and {@link
 * KarmaChainEntry#verifySignature()}: one entry of the karma chain manifest a peer publishes as a
 * BEP 46 DHT item, which {@link RemoteKarmaChainFetcher} reads for every peer whose karma it
 * checks.
 *
 * <p>The owner key is fixed so the committed seeds (an epoch commitment and endorsements, with and
 * without the per-entry {@code pub}, signed by that key) parse and verify, and the fuzzer starts
 * from entries that are accepted.
 *
 * <p>{@code fromBencode} documents a null result for a well-formed dictionary with missing or
 * wrongly typed fields, and {@link IllegalArgumentException} for bytes that aren't canonical
 * bencode or a malformed hex or base64 field. {@code verifySignature} and {@code entryHash} on an
 * entry it returned document no exception. Anything else escaping, or a hang, is a finding.
 */
class KarmaChainEntryFuzzTest {

  private static final byte[] OWNER =
      HexFormat.of().parseHex("b3c0b48c52553c67db5d54fec8eb0f696e60110bf61780c1d4b4113e672f4ec1");

  @FuzzTest
  void fromBencode(FuzzedDataProvider data) {
    KarmaChainEntry entry;
    try {
      entry = KarmaChainEntry.fromBencode(data.consumeRemainingAsBytes(), OWNER);
    } catch (IllegalArgumentException documented) {
      return;
    }
    if (entry == null) {
      return;
    }
    entry.verifySignature();
    entry.entryHash();
  }
}
