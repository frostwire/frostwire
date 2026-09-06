/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RudpSessionManagerTest {
  @Test
  void challengedHandshakeDeliversSignedData() throws Exception {
    List<byte[]> received = new ArrayList<>();
    try (RudpFixture f = new RudpFixture((pub, payload) -> received.add(payload))) {
      f.handshake();
      byte[] payload = {1, 2, 3};
      f.receive(f.signed(RudpPacket.Type.DATA, 1, 0, payload));
      assertEquals(1, received.size());
      assertArrayEquals(payload, received.get(0));
      assertEquals(1, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
    }
  }

  @Test
  void bareHelloDoesNotAuthenticateOrDeliverData() throws Exception {
    List<byte[]> received = new ArrayList<>();
    try (RudpFixture f = new RudpFixture((pub, payload) -> received.add(payload))) {
      f.receive(
          new RudpPacket(
              RudpPacket.Type.HELLO, f.cid, 0, 0, RudpAuth.createHelloPayload(f.peer, f.cid)));
      assertEquals(1, f.manager.sessionCount());
      assertFalse(f.manager.hasRemotePubForTest(f.address));
      f.take(RudpPacket.Type.HELLO_ACK);
      f.receive(new RudpPacket(RudpPacket.Type.DATA, f.cid, 1, 0, new byte[] {1}));
      assertTrue(received.isEmpty());
      assertTrue(f.drain().isEmpty());
    }
  }

  @Test
  void badHelloIsDropped() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.receive(new RudpPacket(RudpPacket.Type.HELLO, f.cid, 0, 0, new byte[104]));
      assertEquals(0, f.manager.sessionCount());
      assertTrue(f.drain().isEmpty());
    }
  }

  @Test
  void unknownAckAndRelayDoNotCreateState() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.receive(new RudpPacket(RudpPacket.Type.DATA_ACK, f.cid, 0, 99, new byte[0]));
      f.receive(
          new RudpPacket(
              RudpPacket.Type.RELAY,
              f.cid,
              1,
              0,
              RelayFrame.encode(
                  f.peer.ed25519PubRaw(), f.local.ed25519PubRaw(), 1, new byte[] {1})));
      assertEquals(0, f.manager.sessionCount());
      assertEquals(0, f.registry.size());
      assertTrue(f.drain().isEmpty());
    }
  }
}
