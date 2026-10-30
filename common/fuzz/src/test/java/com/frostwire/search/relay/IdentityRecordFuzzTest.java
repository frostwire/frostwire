/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes {@link IdentityRecord#fromBencode(byte[])}: the signed identity record a peer publishes as
 * a BEP 46 DHT item, which {@link IdentityRecordLookup} reads for every peer it enriches. It reads
 * wire versions 1 to {@link IdentityRecord#VERSION} and verifies the record's Ed25519 signature
 * against the key inside it.
 *
 * <p>The committed seeds are one record of each wire version, signed, so the fuzzer starts from
 * records that are accepted.
 *
 * <p>The method documents {@link IllegalArgumentException} for every rejection (bytes that aren't
 * canonical bencode, a missing or wrongly typed field, an unsupported version, a malformed field, a
 * bad signature) and never returns null. Findings: anything else escaping, a hang, a null result,
 * and an accepted record whose signature doesn't verify.
 */
class IdentityRecordFuzzTest {

  @FuzzTest
  void fromBencode(FuzzedDataProvider data) {
    IdentityRecord record;
    try {
      record = IdentityRecord.fromBencode(data.consumeRemainingAsBytes());
    } catch (IllegalArgumentException documented) {
      return;
    }
    if (record == null || !record.verifySignature()) {
      throw new AssertionError("accepted an unverified identity record: " + record);
    }
  }
}
