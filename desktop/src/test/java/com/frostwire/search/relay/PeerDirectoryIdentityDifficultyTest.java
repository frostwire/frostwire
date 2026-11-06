/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * No key without the identity proof of work every released FrostWire mints may become a verified,
 * queryable directory entry, whichever path tries to verify it (IceBridge white paper §10.5).
 * Unverified hints, e.g. DHT placeholders, stay allowed.
 */
class PeerDirectoryIdentityDifficultyTest {

  private static final byte[] DIGEST = IndexDigest.build(List.of("free sybil")).toBytes();

  @Test
  void upsertVerifiedRefusesKeyBelowDifficulty() throws Exception {
    assertRefused((dir, pub) -> dir.upsertVerified(pub, "10.0.0.1", 6889));
    assertRefused((dir, pub) -> dir.upsertVerified(pub, "10.0.0.1", 6889, 6889));
    assertRefused((dir, pub) -> dir.upsertVerified(pub, "10.0.0.1", 6889, 6889, 1L, "1.0"));
  }

  @Test
  void upsertVerifiedAcceptsKeyAtDifficulty() {
    assertVerified((dir, pub) -> dir.upsertVerified(pub, "10.0.0.1", 6889, 6889, 1L, "1.0"));
  }

  @Test
  void unverifiedHintBelowDifficultyIsStillAllowed() throws Exception {
    PeerDirectory dir = directory();
    byte[] low = lowPowPub();
    dir.upsert(low, "10.0.0.1", 6889);
    Optional<PeerDirectory.PeerInfo> entry = dir.get(low);
    assertTrue(entry.isPresent());
    assertFalse(entry.get().isVerified());
  }

  @Test
  void setIndexDigestRefusesKeyBelowDifficulty() throws Exception {
    assertRefused((dir, pub) -> dir.setIndexDigest(pub, DIGEST));
    assertVerified((dir, pub) -> dir.setIndexDigest(pub, DIGEST));
  }

  @Test
  void setClusterDigestRefusesKeyBelowDifficulty() throws Exception {
    assertRefused((dir, pub) -> dir.setClusterDigest(pub, DIGEST));
    assertVerified((dir, pub) -> dir.setClusterDigest(pub, DIGEST));
  }

  @Test
  void applyPeerMetaRefusesKeyBelowDifficulty() throws Exception {
    assertRefused((dir, pub) -> dir.applyPeerMeta(pub, NodeCapabilities.DEFAULT_PEER));
    assertVerified((dir, pub) -> dir.applyPeerMeta(pub, NodeCapabilities.DEFAULT_PEER));
  }

  /** Neither creates a verified entry for an unknown key nor upgrades an existing hint. */
  private static void assertRefused(BiConsumer<PeerDirectory, byte[]> verify) throws Exception {
    PeerDirectory dir = directory();
    byte[] unknown = lowPowPub();
    verify.accept(dir, unknown);
    assertFalse(dir.get(unknown).isPresent(), "created an entry for a free identity");

    byte[] hinted = lowPowPub();
    dir.upsert(hinted, "10.0.0.2", 6889);
    verify.accept(dir, hinted);
    assertFalse(dir.get(hinted).orElseThrow().isVerified(), "verified a free identity");
    assertTrue(dir.topByTrustVerified(10).isEmpty());
  }

  private static void assertVerified(BiConsumer<PeerDirectory, byte[]> verify) {
    PeerDirectory dir = directory();
    byte[] pub = PowTestIdentities.nextPub();
    verify.accept(dir, pub);
    assertTrue(dir.get(pub).orElseThrow().isVerified());
  }

  private static byte[] lowPowPub() throws Exception {
    byte[] pub;
    do {
      pub = IdentityKeys.generate().ed25519PubRaw();
    } while (IdentityKeys.meetsIdentityDifficulty(pub));
    return pub;
  }

  private static PeerDirectory directory() {
    return new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
  }
}
