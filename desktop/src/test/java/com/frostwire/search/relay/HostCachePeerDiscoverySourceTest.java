/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostCachePeerDiscoverySourceTest {

  @TempDir File tempDir;

  @Test
  void fetchEndpointsReturnsEveryCachedServerIncludingOnesThatNeverAnswered() {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    cache.addOrUpdate("54.172.26.106", 6888, "FORWARDER");
    cache.markSuccess("54.172.26.106", 6888, "FORWARDER");
    cache.addOrUpdate(
        "never-answered.example.com", 6888, "FORWARDER"); // e.g. server was mid-deploy

    HostCachePeerDiscoverySource source = new HostCachePeerDiscoverySource(cache);
    List<DiscoveredEndpoint> endpoints = source.fetchEndpoints();

    assertEquals(2, endpoints.size());
    assertEquals("54.172.26.106", endpoints.get(0).host);
    assertEquals(6888, endpoints.get(0).port);
    assertEquals("never-answered.example.com", endpoints.get(1).host);
    assertTrue(endpoints.stream().allMatch(e -> e.preferred), "known servers go first");
  }

  @Test
  void builtInSeedsAreAlwaysCandidatesEvenWhenTheCacheIsEmptyOrEvicted() {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    HostCachePeerDiscoverySource source =
        new HostCachePeerDiscoverySource(
            cache, Arrays.asList("virginia1.frostwire.com:6888", "bad-seed", "x:notaport", "y:0"));

    List<DiscoveredEndpoint> endpoints = source.fetchEndpoints();

    assertEquals(1, endpoints.size());
    assertEquals("virginia1.frostwire.com", endpoints.get(0).host);
    assertEquals(6888, endpoints.get(0).port);
    assertTrue(endpoints.get(0).preferred);
  }

  @Test
  void seedListCanBeOverriddenOrDisabledWithASystemProperty() {
    String key = "frostwire.icebridge.seeds";
    String original = System.getProperty(key);
    try {
      System.clearProperty(key);
      assertEquals(Arrays.asList(RelayConstants.DEFAULT_SEED_HOSTS), RelayConstants.seedHosts());
      System.setProperty(key, " a.example:1 , b.example:2 ,");
      assertEquals(Arrays.asList("a.example:1", "b.example:2"), RelayConstants.seedHosts());
      System.setProperty(key, "");
      assertTrue(RelayConstants.seedHosts().isEmpty());
    } finally {
      if (original == null) System.clearProperty(key);
      else System.setProperty(key, original);
    }
  }

  @Test
  void refreshPingsUsesTheAuthenticatedHandshakeSoHealthyServersAreNotEvicted() throws Exception {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    cache.addOrUpdate("54.172.26.106", 6888, "FORWARDER");
    java.security.KeyPair keys =
        java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    IdentityRecord server =
        IdentityRecord.createSigned(new byte[20], keys, new byte[32], 6888, 6889, "FORWARDER");
    // The legacy keyless ping would have to be used without an authenticator; it would fail here.
    cache.setPingAuthenticator(
        (host, port) ->
            host.equals("54.172.26.106")
                ? java.util.Optional.of(server)
                : java.util.Optional.empty());

    cache.refreshPings();

    IceBridgeHostCache.Entry entry = cache.getAll().get(0);
    assertTrue(entry.lastSuccessfulPingMs > 0, "ping must be recorded as a success");
    assertEquals(0, entry.consecutiveFailures);
    assertEquals("FORWARDER", entry.role);
  }

  @Test
  void refreshPingsStillCountsFailuresWithTheAuthenticator() {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    cache.addOrUpdate("203.0.113.9", 6888, "BOTH");
    cache.setPingAuthenticator((host, port) -> java.util.Optional.empty());

    cache.refreshPings();

    assertEquals(1, cache.getAll().get(0).consecutiveFailures);
  }

  @Test
  void fetchEndpointsSkipsBlankHostsAndZeroPorts() {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    cache.addOrUpdate("", 6888, "BOTH");
    cache.markSuccess("", 6888, "BOTH");
    cache.addOrUpdate("ok.example.com", 6888, "BOTH");
    cache.markSuccess("ok.example.com", 6888, "BOTH");

    List<DiscoveredEndpoint> endpoints = new HostCachePeerDiscoverySource(cache).fetchEndpoints();
    assertEquals(1, endpoints.size());
    assertEquals("ok.example.com", endpoints.get(0).host);
  }

  @Test
  void fetchIdentityEntryIsNull() {
    HostCachePeerDiscoverySource source =
        new HostCachePeerDiscoverySource(new IceBridgeHostCache(new File(tempDir, "h.txt")));
    assertNull(source.fetchIdentityEntry(new byte[32]));
  }

  @Test
  void compositeConcatenatesAndIsolatesFailures() {
    PeerDiscoverySource failing =
        new PeerDiscoverySource() {
          @Override
          public List<DiscoveredEndpoint> fetchEndpoints() {
            throw new RuntimeException("boom");
          }

          @Override
          public com.frostwire.jlibtorrent.Entry fetchIdentityEntry(byte[] peerPub) {
            return null;
          }
        };
    PeerDiscoverySource good =
        new PeerDiscoverySource() {
          @Override
          public List<DiscoveredEndpoint> fetchEndpoints() {
            return Arrays.asList(new DiscoveredEndpoint("1.2.3.4", 6888));
          }

          @Override
          public com.frostwire.jlibtorrent.Entry fetchIdentityEntry(byte[] peerPub) {
            return null;
          }
        };

    CompositePeerDiscoverySource composite = new CompositePeerDiscoverySource(failing, good, null);
    List<DiscoveredEndpoint> endpoints = composite.fetchEndpoints();
    assertEquals(1, endpoints.size());
    assertEquals("1.2.3.4", endpoints.get(0).host);
    assertNull(composite.fetchIdentityEntry(new byte[32]));
  }
}
