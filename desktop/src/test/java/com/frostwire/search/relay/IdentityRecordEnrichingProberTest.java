/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdentityRecordEnrichingProberTest {

  private static final long FORWARDER_CAPS = NodeCapabilities.DEFAULT_FORWARDER;

  private static IdentityRecord record(KeyPair keys, String role, long caps) {
    return IdentityRecord.createSigned(new byte[20], keys, new byte[32], 6889, 6889, role, caps);
  }

  private static KeyPair keys() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  private static long deadline() {
    return System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
  }

  private static final class RecordSource implements PeerDiscoverySource {
    final AtomicInteger lookups = new AtomicInteger();
    final Entry entry;

    RecordSource(IdentityRecord record) {
      this.entry = record == null ? null : record.toEntry();
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
      return List.of();
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
      lookups.incrementAndGet();
      return entry;
    }
  }

  @Test
  void aNewPeerGetsItsRoleAndCapabilitiesFromItsSignedRecord() throws Exception {
    KeyPair server = keys();
    IdentityRecord record = record(server, "FORWARDER", FORWARDER_CAPS);
    ProbedPeer probed = new ProbedPeer(record.ed25519Pub());
    IdentityRecordEnrichingProber prober =
        new IdentityRecordEnrichingProber(
            (host, port, deadline) -> Optional.of(probed), new RecordSource(record), pub -> true);

    ProbedPeer peer = prober.probe("198.51.100.7", 6889, deadline()).orElseThrow();

    assertArrayEquals(record.ed25519Pub(), peer.pub());
    assertEquals("FORWARDER", peer.record().orElseThrow().role());
    assertEquals(FORWARDER_CAPS, peer.record().orElseThrow().capabilities());
  }

  @Test
  void knownPeersAreNotLookedUpAgain() throws Exception {
    IdentityRecord record = record(keys(), "FORWARDER", FORWARDER_CAPS);
    RecordSource source = new RecordSource(record);
    IdentityRecordEnrichingProber prober =
        new IdentityRecordEnrichingProber(
            (host, port, deadline) -> Optional.of(new ProbedPeer(record.ed25519Pub())),
            source,
            pub -> false);

    ProbedPeer peer = prober.probe("198.51.100.7", 6889, deadline()).orElseThrow();

    assertTrue(peer.record().isEmpty());
    assertEquals(0, source.lookups.get());
  }

  @Test
  void aMissingOrForgedRecordNeverGatesDiscovery() throws Exception {
    KeyPair server = keys();
    byte[] pub = record(server, "BOTH", NodeCapabilities.DEFAULT_BOTH).ed25519Pub();
    IdentityRecord someoneElse = record(keys(), "FORWARDER", FORWARDER_CAPS);

    for (IdentityRecord published : new IdentityRecord[] {null, someoneElse}) {
      IdentityRecordEnrichingProber prober =
          new IdentityRecordEnrichingProber(
              (host, port, deadline) -> Optional.of(new ProbedPeer(pub)),
              new RecordSource(published),
              p -> true);

      ProbedPeer peer = prober.probe("198.51.100.7", 6889, deadline()).orElseThrow();

      assertArrayEquals(pub, peer.pub());
      assertTrue(peer.record().isEmpty(), "a record signed by another key must be ignored");
    }
  }

  @Test
  void aSilentEndpointCostsNoLookup() {
    RecordSource source = new RecordSource(null);
    IdentityRecordEnrichingProber prober =
        new IdentityRecordEnrichingProber(
            (host, port, deadline) -> Optional.empty(), source, pub -> true);

    assertTrue(prober.probe("198.51.100.7", 6889, deadline()).isEmpty());
    assertEquals(0, source.lookups.get());
  }

  @Test
  void aRecordForAnotherKeyCannotBeAttachedToAProbedPeer() throws Exception {
    IdentityRecord record = record(keys(), "FORWARDER", FORWARDER_CAPS);
    assertThrows(IllegalArgumentException.class, () -> new ProbedPeer(new byte[32], record));
    assertThrows(IllegalArgumentException.class, () -> new ProbedPeer(new byte[31]));
  }
}
