/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostCachePeerDiscoverySourceTest {

  @TempDir File tempDir;

  @Test
  void fetchEndpointsReturnsPingableCacheEntries() {
    IceBridgeHostCache cache = new IceBridgeHostCache(new File(tempDir, "hosts.txt"));
    cache.addOrUpdate("54.172.26.106", 6888, "FORWARDER");
    cache.markSuccess("54.172.26.106", 6888, "FORWARDER");
    cache.addOrUpdate("dead.example.com", 6888, "BOTH"); // never pinged OK

    HostCachePeerDiscoverySource source = new HostCachePeerDiscoverySource(cache);
    List<DiscoveredEndpoint> endpoints = source.fetchEndpoints();

    assertEquals(1, endpoints.size());
    assertEquals("54.172.26.106", endpoints.get(0).host);
    assertEquals(6888, endpoints.get(0).port);
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
