/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Entry;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * A discovery pass listed every network interface once per candidate endpoint to check whether the
 * candidate was this machine, and used a whole CPU core every 30 s on a 4-CPU Windows machine
 * (Icebase #f15136ff272e, frostwire/frostwire#1574). The machine's addresses are now read once per
 * pass.
 */
class PeerDiscoveryLocalAddressesTest {

  @Test
  void aPassReadsTheMachineAddressesOnceAndStillSkipsOurOwn() throws Exception {
    List<DiscoveredEndpoint> endpoints = new ArrayList<>();
    for (int i = 1; i <= 200; i++) {
      endpoints.add(new DiscoveredEndpoint("8.8." + (i / 250) + "." + (i % 250), 6881));
    }
    endpoints.add(new DiscoveredEndpoint("203.0.113.7", 6881, true)); // ours, first if not skipped
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
    PeerDirectory directory =
        new PeerDirectory(
            new PeerKarmaCache(
                new RemoteKarmaChainFetcher(
                    new KarmaChainSource() {
                      @Override
                      public Entry fetchManifest(byte[] peerPub) {
                        return null;
                      }
                    })));
    PeerDiscovery discovery = new PeerDiscovery(source, directory);
    AtomicInteger reads = new AtomicInteger();
    InetAddress own = InetAddress.getByName("203.0.113.7");
    discovery.setMachineAddresses(
        () -> {
          reads.incrementAndGet();
          return Set.of(own);
        });

    List<DiscoveredEndpoint> registered = discovery.discoverAndRegister();

    assertEquals(1, reads.get(), "the interfaces are listed once per pass, not per candidate");
    assertFalse(registered.isEmpty());
    assertTrue(
        registered.stream().noneMatch(ep -> "203.0.113.7".equals(ep.host)),
        "this machine's own address is still skipped");
  }
}
