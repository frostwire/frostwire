/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Identity discovery over rUDP: a node that only knows an {@code ip:udpPort} (from the DHT, the LAN
 * or a seed list) opens a handshake with no expected identity and learns who answers.
 */
class RudpProbeTest {

  @Test
  void probingAnUnknownEndpointOpensAnIdentityDiscoveryHandshake() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      RudpSessionManager.ProbeResult result = f.manager.probe("127.0.0.1", 62001, false);

      assertEquals(RudpSessionManager.ProbeState.PENDING, result.state());
      assertNull(result.pub());
      RudpPacket hello = f.take(RudpPacket.Type.HELLO);
      assertTrue(RudpAuth.verifyHello(hello.connectionId(), hello.payload()));
      assertTrue(
          RudpAuth.intendedFor(hello.payload(), new byte[32]),
          "no identity is pinned when discovering");
    }
  }

  @Test
  void probingAgainWhileThePeerHasNotAnsweredDoesNotOpenAnotherHandshake() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.manager.probe("127.0.0.1", 62001, false);
      f.take(RudpPacket.Type.HELLO);

      assertEquals(
          RudpSessionManager.ProbeState.PENDING,
          f.manager.probe("127.0.0.1", 62001, false).state());

      assertEquals(1, f.manager.sessionCount());
      assertTrue(f.drain().isEmpty(), "polling must not send more packets");
    }
  }

  @Test
  void anAuthenticatedPeerIsReportedWithItsIdentity() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.manager.probe("127.0.0.1", 62001, false);
      f.finishOutbound();

      RudpSessionManager.ProbeResult result = f.manager.probe("127.0.0.1", 62001, false);

      assertEquals(RudpSessionManager.ProbeState.ESTABLISHED, result.state());
      assertArrayEquals(f.peer.ed25519PubRaw(), result.pub());
    }
  }

  @Test
  void cancellingDropsAHandshakeThatNeverCompletedButKeepsAnEstablishedPeer() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.manager.probe("127.0.0.1", 62001, false);
      assertEquals(1, f.manager.sessionCount());

      assertEquals(
          RudpSessionManager.ProbeState.NONE, f.manager.probe("127.0.0.1", 62001, true).state());
      assertEquals(0, f.manager.sessionCount(), "a dead endpoint must not hold a session");
      f.drain(); // the abandoned HELLO

      f.manager.probe("127.0.0.1", 62001, false);
      f.finishOutbound();
      assertEquals(
          RudpSessionManager.ProbeState.ESTABLISHED,
          f.manager.probe("127.0.0.1", 62001, true).state());
      assertEquals(1, f.manager.sessionCount());
    }
  }

  @Test
  void onlyLiteralRoutableEndpointsAreProbed() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      for (Object[] bad :
          new Object[][] {
            {"example.com", 6889},
            {"", 6889},
            {null, 6889},
            {"127.0.0.1", 0},
            {"127.0.0.1", 65536},
            {"0.0.0.0", 6889},
            {"224.0.0.1", 6889}
          }) {
        assertEquals(
            RudpSessionManager.ProbeState.NONE,
            f.manager.probe((String) bad[0], (Integer) bad[1], false).state(),
            String.valueOf(bad[0]) + ":" + bad[1]);
      }
      assertEquals(0, f.manager.sessionCount());
      assertTrue(f.drain().isEmpty());
    }
  }
}
