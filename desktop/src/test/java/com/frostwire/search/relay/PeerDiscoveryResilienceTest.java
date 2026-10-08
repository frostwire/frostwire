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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for "the desktop only sees itself": unreachable peers announced on the DHT must
 * not starve the reachable ones (the dedicated IceBridge servers) out of a discovery pass.
 */
class PeerDiscoveryResilienceTest {

  private final List<DiscoveredEndpoint> endpoints = new ArrayList<>();
  private final AtomicLong nowMs = new AtomicLong(1_000_000);
  private PeerDirectory directory;

  @BeforeEach
  void setUp() {
    directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
  }

  @AfterEach
  void tearDown() {
    endpoints.clear();
  }

  private PeerDiscovery discovery(PeerAuthenticator authenticator) {
    PeerDiscoverySource source =
        new PeerDiscoverySource() {
          @Override
          public List<DiscoveredEndpoint> fetchEndpoints() {
            return new ArrayList<>(endpoints);
          }

          @Override
          public Entry fetchIdentityEntry(byte[] peerPub) {
            return null;
          }
        };
    PeerDiscovery discovery = new PeerDiscovery(source, directory, authenticator);
    discovery.setClock(nowMs::get);
    return discovery;
  }

  private static IdentityRecord record(int port) {
    try {
      KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      return IdentityRecord.createSigned(new byte[20], keys, new byte[32], port);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void slowUnreachableEndpointsDoNotStarveAReachableOneListedLast() {
    for (int i = 0; i < 40; i++) {
      endpoints.add(new DiscoveredEndpoint("203.0.113." + (i + 1), 6888));
    }
    endpoints.add(new DiscoveredEndpoint("198.51.100.7", 6888));
    IdentityRecord server = record(6888);
    PeerDiscovery discovery =
        discovery(
            (host, port) -> {
              if (host.equals("198.51.100.7")) return Optional.of(server);
              sleep(200); // an unreachable peer burns its connect timeout
              return Optional.empty();
            });
    discovery.setPassTimeoutMs(3_000);
    discovery.setProbeConcurrency(8);

    long started = System.nanoTime();
    List<DiscoveredEndpoint> found = discovery.discoverAndRegister();

    assertEquals(1, found.size(), "the reachable server must be found despite 40 dead peers");
    assertTrue(directory.get(server.ed25519Pub()).isPresent());
    assertTrue((System.nanoTime() - started) / 1_000_000 < 2_500, "probes must run in parallel");
  }

  @Test
  void preferredEndpointsAreProbedBeforeCrowdedDhtResults() {
    for (int i = 0; i < 30; i++) {
      endpoints.add(new DiscoveredEndpoint("203.0.113." + (i + 1), 6888));
    }
    endpoints.add(new DiscoveredEndpoint("198.51.100.7", 6888, true));
    List<String> order = Collections.synchronizedList(new ArrayList<>());
    PeerDiscovery discovery =
        discovery(
            (host, port) -> {
              order.add(host);
              return Optional.empty();
            });
    discovery.setProbeConcurrency(1);

    discovery.discoverAndRegister();

    assertEquals("198.51.100.7", order.get(0), "bootstrap/host-cache servers go first");
  }

  @Test
  void failedEndpointsBackOffSoLaterPassesReachTheRest() {
    for (int i = 0; i < 100; i++) {
      endpoints.add(new DiscoveredEndpoint("203.0.113." + (i + 1), 6888));
    }
    Set<String> probed = ConcurrentHashMap.newKeySet();
    AtomicInteger calls = new AtomicInteger();
    PeerDiscovery discovery =
        discovery(
            (host, port) -> {
              calls.incrementAndGet();
              probed.add(host);
              return Optional.empty();
            });

    discovery.discoverAndRegister();
    assertEquals(PeerDiscovery.MAX_CANDIDATES_PER_PASS, calls.get());
    discovery.discoverAndRegister();

    assertEquals(100, probed.size(), "the second pass must cover the endpoints the first skipped");
    assertEquals(100, calls.get(), "nothing is re-probed while it is backing off");
  }

  @Test
  void backoffExpiresAndPreferredEndpointsRetrySooner() {
    endpoints.add(new DiscoveredEndpoint("203.0.113.1", 6888));
    endpoints.add(new DiscoveredEndpoint("198.51.100.7", 6888, true));
    AtomicInteger ordinary = new AtomicInteger();
    AtomicInteger preferred = new AtomicInteger();
    PeerDiscovery discovery =
        discovery(
            (host, port) -> {
              (host.startsWith("198") ? preferred : ordinary).incrementAndGet();
              return Optional.empty();
            });

    discovery.discoverAndRegister();
    assertEquals(1, ordinary.get());
    assertEquals(1, preferred.get());

    nowMs.addAndGet(10_000);
    discovery.discoverAndRegister();
    assertEquals(1, ordinary.get(), "an ordinary dead peer stays quiet for a while");
    assertEquals(2, preferred.get(), "a known server is retried within seconds");

    nowMs.addAndGet(15 * 60_000);
    discovery.discoverAndRegister();
    assertEquals(2, ordinary.get(), "backoff is bounded, the peer is eventually retried");
  }

  @Test
  void successClearsBackoff() {
    endpoints.add(new DiscoveredEndpoint("198.51.100.7", 6888, true));
    IdentityRecord server = record(6888);
    AtomicInteger calls = new AtomicInteger();
    PeerDiscovery discovery =
        discovery(
            (host, port) -> calls.incrementAndGet() == 1 ? Optional.empty() : Optional.of(server));

    assertTrue(discovery.discoverAndRegister().isEmpty());
    nowMs.addAndGet(10_000);
    assertEquals(1, discovery.discoverAndRegister().size());
    assertEquals(2, calls.get());
    assertTrue(directory.get(server.ed25519Pub()).isPresent());
  }

  @Test
  void slowProbesAreCancelledAtThePassDeadlineWithoutBlockingForever() {
    for (int i = 0; i < 4; i++) {
      endpoints.add(new DiscoveredEndpoint("203.0.113." + (i + 1), 6888));
    }
    PeerDiscovery discovery =
        discovery(
            (host, port) -> {
              sleep(60_000);
              return Optional.empty();
            });
    discovery.setPassTimeoutMs(500);

    long started = System.nanoTime();
    assertTrue(discovery.discoverAndRegister().isEmpty());
    assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
