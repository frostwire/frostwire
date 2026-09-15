/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Entry;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PeerDirectoryRoutingTest {

  private static PeerDirectory directory() {
    return new PeerDirectory(
        new PeerKarmaCache(
            new RemoteKarmaChainFetcher(
                new KarmaChainSource() {
                  @Override
                  public Entry fetchManifest(byte[] peerPub) {
                    return null;
                  }
                })));
  }

  private static byte[] pub(int seed) throws GeneralSecurityException {
    return IdentityKeys.generate(seed).ed25519PubRaw();
  }

  @Test
  void holderWithMatchingDigestIsSelectedFirst() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] holder = pub(1);
    byte[] other = pub(2);
    dir.upsertVerified(holder, "10.0.0.1", 6889);
    dir.upsertVerified(other, "10.0.0.2", 6889);
    dir.setIndexDigest(
        holder, IndexDigest.build(List.of("Inglés En Miami (audio).webm")).toBytes());

    List<PeerDirectory.PeerInfo> sampled =
        dir.sampleHolders("miami", 4, Set.of(), NodeCapabilities.NONE, new Random(7), 1);
    assertFalse(sampled.isEmpty());
    assertArrayEquals(holder, sampled.get(0).peerPub());
  }

  @Test
  void malformedDigestIsIgnoredAndPeerStaysQueryable() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] peer = pub(3);
    dir.upsertVerified(peer, "10.0.0.3", 6889);
    dir.setIndexDigest(peer, new byte[] {1, 2, 3});
    PeerDirectory.PeerInfo info = dir.get(peer).orElseThrow();
    assertEquals(1, dir.rankByHoldership("miami", List.of(info)).size());
    assertFalse(
        dir.sampleHolders("miami", 2, Set.of(), NodeCapabilities.NONE, new Random(1), 1).isEmpty());
  }

  @Test
  void unreachablePeersAreEvictedAfterMaxFailures() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] peer = pub(4);
    dir.upsertVerified(peer, "10.0.0.4", 6889);
    for (int i = 0; i < PeerDirectory.MAX_FAILURES - 1; i++) {
      assertFalse(dir.markFailure(peer));
    }
    assertTrue(dir.markFailure(peer));
    assertTrue(dir.get(peer).isEmpty());
  }

  @Test
  void contactResetsFailureStreak() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] peer = pub(5);
    dir.upsertVerified(peer, "10.0.0.5", 6889);
    for (int i = 0; i < PeerDirectory.MAX_FAILURES - 1; i++) {
      dir.markFailure(peer);
    }
    dir.markContact(peer);
    assertFalse(dir.markFailure(peer));
    assertTrue(dir.get(peer).isPresent());
  }

  @Test
  void evictUnreachableDropsSilentNeverContactedPeers() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] peer = pub(6);
    dir.upsertVerified(peer, "10.0.0.6", 6889);
    long future = System.currentTimeMillis() + PeerDirectory.AFFIRM_TTL_MS + 1_000;
    assertEquals(1, dir.evictUnreachable(future));
    assertTrue(dir.get(peer).isEmpty());
  }

  @Test
  void recentlyContactedPeerSurvivesSweep() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] peer = pub(7);
    dir.upsertVerified(peer, "10.0.0.7", 6889);
    dir.markContact(peer);
    long now = System.currentTimeMillis();
    assertEquals(0, dir.evictUnreachable(now));
  }

  @Test
  void evictedPeersAreNotSampled() throws GeneralSecurityException {
    PeerDirectory dir = directory();
    byte[] good = pub(8);
    byte[] bad = pub(9);
    dir.upsertVerified(good, "10.0.0.8", 6889);
    dir.upsertVerified(bad, "10.0.0.9", 6889);
    for (int i = 0; i < PeerDirectory.MAX_FAILURES; i++) {
      dir.markFailure(bad);
    }
    assertTrue(dir.get(bad).isEmpty());
    List<PeerDirectory.PeerInfo> sampled =
        dir.sampleVerified(4, Set.of(), NodeCapabilities.NONE, new Random(3));
    assertEquals(1, sampled.size());
    assertArrayEquals(good, sampled.get(0).peerPub());
  }
}
