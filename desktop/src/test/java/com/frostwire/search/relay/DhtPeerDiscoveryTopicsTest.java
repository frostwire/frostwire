/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TcpEndpoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dedicated servers live on the bootstrap topic; a crowded relay topic must not hide them. */
class DhtPeerDiscoveryTopicsTest {

  private static SessionManager session(
      List<TcpEndpoint> bootstrap, List<TcpEndpoint> relays, List<TcpEndpoint> peers) {
    return new SessionManager() {
      @Override
      public ArrayList<TcpEndpoint> dhtGetPeers(Sha1Hash topic, int timeoutSeconds) {
        String key = topic.toString();
        if (key.equals(DhtRendezvous.bootstrapTopic().toString()))
          return new ArrayList<>(bootstrap);
        if (key.equals(DhtRendezvous.relayTopic().toString())) return new ArrayList<>(relays);
        return new ArrayList<>(peers);
      }
    };
  }

  @Test
  void bootstrapServersAreReportedEvenWhenRelayAndPeerTopicsAreCrowded() {
    List<TcpEndpoint> relays = new ArrayList<>();
    List<TcpEndpoint> peers = new ArrayList<>();
    for (int i = 1; i <= 41; i++) relays.add(new TcpEndpoint("203.0.113." + i, 6888));
    for (int i = 1; i <= 12; i++) peers.add(new TcpEndpoint("198.51.100." + i, 6888));
    TcpEndpoint server = new TcpEndpoint("54.172.26.106", 6888);

    List<DiscoveredEndpoint> found =
        new DhtPeerDiscoverySource(session(List.of(server), relays, peers)).fetchEndpoints();

    DiscoveredEndpoint first = found.get(0);
    assertEquals("54.172.26.106", first.host);
    assertTrue(first.preferred, "bootstrap servers are probed before the crowd");
    assertEquals(1 + 41 + 12, found.size());
    assertEquals(1, found.stream().filter(e -> e.preferred).count());
  }

  @Test
  void aServerListedOnSeveralTopicsStaysPreferredAndAppearsOnce() {
    TcpEndpoint server = new TcpEndpoint("54.172.26.106", 6888);

    List<DiscoveredEndpoint> found =
        new DhtPeerDiscoverySource(session(List.of(server), List.of(server), List.of(server)))
            .fetchEndpoints();

    assertEquals(1, found.size());
    assertTrue(found.get(0).preferred);
  }

  @Test
  void relayAndPeerEndpointsAreNotPreferred() {
    List<DiscoveredEndpoint> found =
        new DhtPeerDiscoverySource(
                session(
                    List.of(),
                    List.of(new TcpEndpoint("203.0.113.1", 6888)),
                    List.of(new TcpEndpoint("198.51.100.1", 6888))))
            .fetchEndpoints();

    assertEquals(2, found.size());
    assertTrue(found.stream().noneMatch(e -> e.preferred));
  }
}
