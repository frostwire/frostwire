/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Fuzzes {@link KarmaChain#parseManifest(byte[], byte[])}: a peer's karma chain manifest, the BEP
 * 46 DHT item {@link RemoteKarmaChainFetcher} fetches under the peer's key. It reconstructs every
 * entry and checks the chain with {@link KarmaChain#verify} (a complete chain) or {@link
 * KarmaChain#verifyTail} (a trimmed tail with a {@code base}): hash links, signatures, sequence
 * numbers, block heights, epochs and the endorsement budget.
 *
 * <p>The owner key is fixed so the committed seeds (a complete chain over two epochs, a genesis
 * epoch commitment plus one endorsement, and a trimmed tail with its base, all signed by that key)
 * verify, and the fuzzer starts from manifests that are accepted.
 *
 * <p>The method documents a null result for a manifest that is too large, has the wrong shape or
 * doesn't verify, and {@link IllegalArgumentException} for bytes that aren't canonical bencode or a
 * malformed hex or base64 field. Findings: anything else escaping, a hang, and an accepted chain
 * that breaks what callers rely on (empty, more than {@link KarmaChain#MAX_REMOTE_MANIFEST_ENTRIES}
 * entries, or an entry not signed by the owner).
 */
class KarmaChainFuzzTest {

  private static final byte[] OWNER =
      HexFormat.of().parseHex("b3c0b48c52553c67db5d54fec8eb0f696e60110bf61780c1d4b4113e672f4ec1");

  @FuzzTest
  void parseManifest(FuzzedDataProvider data) {
    List<KarmaChainEntry> chain;
    try {
      chain = KarmaChain.parseManifest(data.consumeRemainingAsBytes(), OWNER);
    } catch (IllegalArgumentException documented) {
      return;
    }
    if (chain == null) {
      return;
    }
    if (chain.isEmpty() || chain.size() > KarmaChain.MAX_REMOTE_MANIFEST_ENTRIES) {
      throw new AssertionError("accepted chain has " + chain.size() + " entries");
    }
    for (KarmaChainEntry entry : chain) {
      if (!Arrays.equals(OWNER, entry.endorserPub()) || !entry.verifySignature()) {
        throw new AssertionError("accepted chain has an entry not signed by the owner: " + entry);
      }
    }
    assertEndorsementBudget(chain);
  }

  /**
   * The point of epoch energy: an accepted chain never has more endorsements after a commitment
   * than floor(energy), and the energy is a finite value in range. A "NaN" energy once lifted the
   * budget because every comparison with NaN is false (frostwire/frostwire#1401).
   */
  private static void assertEndorsementBudget(List<KarmaChainEntry> chain) {
    double energy = -1;
    int endorsements = 0;
    for (KarmaChainEntry entry : chain) {
      if (entry.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        energy = entry.energy();
        endorsements = 0;
        if (!Double.isFinite(energy) || energy < 0 || energy > KarmaConstants.MAX_ENERGY + 0.01) {
          throw new AssertionError("accepted a commitment with energy " + energy);
        }
      } else if (energy >= 0 && ++endorsements > Math.floor(energy)) {
        throw new AssertionError("accepted " + endorsements + " endorsements on energy " + energy);
      }
    }
  }
}
