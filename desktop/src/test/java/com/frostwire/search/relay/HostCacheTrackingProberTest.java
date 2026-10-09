/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostCacheTrackingProberTest {

  @TempDir File tempDir;

  private IceBridgeHostCache cache;

  @BeforeEach
  void setUp() {
    cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
  }

  private static ProbedPeer peer(String role) throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    IdentityRecord record =
        IdentityRecord.createSigned(
            new byte[20], keys, new byte[32], 6889, 6889, role, NodeCapabilities.fromRole(role));
    return new ProbedPeer(record.ed25519Pub(), record);
  }

  private HostCacheTrackingProber tracking(PeerProber delegate) {
    return new HostCacheTrackingProber(delegate, cache);
  }

  @Test
  void aForwarderIsAddedToTheCacheTheMomentItIsDiscovered() throws Exception {
    ProbedPeer server = peer("FORWARDER");

    tracking((host, port, deadline) -> Optional.of(server)).probe("54.172.26.106", 6889, 0);

    IceBridgeHostCache.Entry entry = cache.getAll().get(0);
    assertEquals("54.172.26.106", entry.host);
    assertEquals(6889, entry.port, "the cached port is the rUDP port");
    assertEquals("FORWARDER", entry.role);
    assertTrue(entry.lastSuccessfulPingMs > 0);
  }

  @Test
  void aLeafIsNotAddedButARefreshedCachedHostResetsItsFailures() throws Exception {
    ProbedPeer leaf = peer("CLIENT");
    tracking((host, port, deadline) -> Optional.of(leaf)).probe("192.168.4.96", 62124, 0);
    assertTrue(cache.getAll().isEmpty(), "a leaf is not an IceBridge server");

    cache.addOrUpdate("203.0.113.9", 6889, "BOTH");
    cache.markFailure("203.0.113.9", 6889);
    tracking((host, port, deadline) -> Optional.of(leaf)).probe("203.0.113.9", 6889, 0);

    assertEquals(0, cache.getAll().get(0).consecutiveFailures);
  }

  @Test
  void failuresCountAgainstCachedServersUntilTheyAreEvicted() {
    cache.addOrUpdate("203.0.113.9", 6889, "FORWARDER");
    HostCacheTrackingProber prober = tracking((host, port, deadline) -> Optional.empty());

    prober.probe("203.0.113.9", 6889, 0);
    assertEquals(1, cache.getAll().get(0).consecutiveFailures);
    for (int i = 1; i < IceBridgeHostCache.MAX_CONSECUTIVE_FAILURES; i++) {
      prober.probe("203.0.113.9", 6889, 0);
    }
    assertTrue(cache.getAll().isEmpty(), "a dead server is evicted instead of retried forever");
  }

  @Test
  void hostsThatAreNotCachedAreNeverAddedByAFailure() {
    tracking((host, port, deadline) -> Optional.empty()).probe("203.0.113.77", 6889, 0);
    assertTrue(cache.getAll().isEmpty());
  }

  @Test
  void aBrokenCacheNeverBreaksDiscoveryAndArgumentsAreRequired() throws Exception {
    ProbedPeer server = peer("FORWARDER");
    IceBridgeHostCache unwritable =
        new IceBridgeHostCache(new File(tempDir, "no/such/dir/hosts.txt"));
    Optional<ProbedPeer> result =
        new HostCacheTrackingProber((host, port, deadline) -> Optional.of(server), unwritable)
            .probe("54.172.26.106", 6889, 0);

    assertTrue(result.isPresent());
    assertThrows(IllegalArgumentException.class, () -> new HostCacheTrackingProber(null, cache));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HostCacheTrackingProber((host, port, deadline) -> Optional.empty(), null));
  }
}
