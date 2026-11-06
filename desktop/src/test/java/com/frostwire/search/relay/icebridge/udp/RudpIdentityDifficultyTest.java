/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PowTestIdentities;
import org.junit.jupiter.api.Test;

/**
 * The rUDP handshake refuses a peer whose identity lacks the proof of work every released FrostWire
 * mints (IceBridge white paper §10.5), in both directions: an inbound HELLO and the HELLO_ACK
 * answering our own HELLO.
 */
class RudpIdentityDifficultyTest {

  @Test
  void helloFromIdentityBelowDifficultyIsRefused() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, lowPowIdentity())) {
      byte[] hello = RudpAuth.createHelloPayload(f.peer, f.cid, f.local.ed25519PubRaw());
      f.receive(new RudpPacket(RudpPacket.Type.HELLO, f.cid, 0, 0, hello));
      assertEquals(0, f.manager.sessionCount());
      assertTrue(f.drain().isEmpty(), "no HELLO_ACK for a free identity");
      assertEquals(1, f.metrics.helloRejectedCount());
    }
  }

  @Test
  void helloFromIdentityAtDifficultyIsAccepted() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, PowTestIdentities.next())) {
      f.handshake();
      assertEquals(0, f.metrics.helloRejectedCount());
    }
  }

  @Test
  void helloAckFromIdentityBelowDifficultyIsRefused() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, lowPowIdentity())) {
      assertTrue(f.manager.sendData(f.address, new byte[] {1}));
      RudpPacket hello = f.take(RudpPacket.Type.HELLO);
      f.cid = hello.connectionId();
      byte[] ack = RudpAuth.createAckPayload(f.peer, f.cid, hello.payload());
      f.receive(new RudpPacket(RudpPacket.Type.HELLO_ACK, f.cid, 0, 0, ack));
      assertTrue(f.drain().isEmpty(), "no HELLO_FINISH for a free identity");
      assertFalse(f.manager.hasRemotePubForTest(f.address));
      assertEquals(1, f.metrics.helloRejectedCount());
    }
  }

  @Test
  void helloAckFromIdentityAtDifficultyIsAccepted() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, PowTestIdentities.next())) {
      assertTrue(f.manager.sendData(f.address, new byte[] {1}));
      f.finishOutbound();
      assertEquals(0, f.metrics.helloRejectedCount());
    }
  }

  private static IdentityKeys lowPowIdentity() throws Exception {
    IdentityKeys identity;
    do {
      identity = IdentityKeys.generate();
    } while (IdentityKeys.meetsIdentityDifficulty(identity.ed25519PubRaw()));
    return identity;
  }
}
