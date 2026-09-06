/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class RudpAuthTest {
  @Test
  void handshakeBindsRolesPeerCidAndFreshChallenges() throws Exception {
    IdentityKeys a = IdentityKeys.generate(0);
    IdentityKeys b = IdentityKeys.generate(0);
    IdentityKeys c = IdentityKeys.generate(0);
    byte[] hello = RudpAuth.createHelloPayload(a, 1, b.ed25519PubRaw());
    byte[] anotherHello = RudpAuth.createHelloPayload(a, 1, b.ed25519PubRaw());
    byte[] ack = RudpAuth.createAckPayload(b, 1, hello);
    assertTrue(RudpAuth.verifyHello(1, hello));
    assertFalse(RudpAuth.verifyHello(2, hello));
    assertFalse(RudpAuth.verifyHello(1, ack));
    assertTrue(RudpAuth.intendedFor(hello, b.ed25519PubRaw()));
    assertFalse(RudpAuth.intendedFor(hello, c.ed25519PubRaw()));
    assertTrue(RudpAuth.verifyAck(1, hello, ack));
    assertFalse(RudpAuth.verifyAck(2, hello, ack));
    assertFalse(RudpAuth.verifyAck(1, anotherHello, ack));
    assertFalse(RudpAuth.verifyAck(1, hello, RudpAuth.createAckPayload(c, 1, hello)));
    assertFalse(java.util.Arrays.equals(hello, anotherHello));
    for (long timestamp : new long[] {Long.MIN_VALUE, Long.MAX_VALUE, 0}) {
      byte[] invalid = hello.clone();
      ByteBuffer.wrap(invalid, 96, 8).putLong(timestamp);
      assertFalse(RudpAuth.verifyHello(1, invalid));
    }
  }

  @Test
  void packetSignatureBindsEveryRoutingAndAcknowledgementField() throws Exception {
    IdentityKeys a = IdentityKeys.generate(0);
    IdentityKeys b = IdentityKeys.generate(0);
    IdentityKeys c = IdentityKeys.generate(0);
    byte[] hello = RudpAuth.createHelloPayload(a, 1, b.ed25519PubRaw());
    byte[] transcript = RudpAuth.transcript(hello, RudpAuth.createAckPayload(b, 1, hello));
    RudpPacket plain = new RudpPacket(RudpPacket.Type.DATA, 1, 1, 0, new byte[] {1});
    RudpPacket signed = RudpAuth.protect(a, b.ed25519PubRaw(), transcript, plain);
    assertEquals(
        plain, RudpAuth.unprotect(a.ed25519PubRaw(), b.ed25519PubRaw(), transcript, signed));
    for (RudpPacket changed :
        new RudpPacket[] {
          new RudpPacket(RudpPacket.Type.RELAY, 1, 1, 0, signed.payload()),
          new RudpPacket(RudpPacket.Type.DATA, 2, 1, 0, signed.payload()),
          new RudpPacket(RudpPacket.Type.DATA, 1, 2, 0, signed.payload()),
          new RudpPacket(RudpPacket.Type.DATA, 1, 1, 1, signed.payload())
        }) {
      assertNull(RudpAuth.unprotect(a.ed25519PubRaw(), b.ed25519PubRaw(), transcript, changed));
    }
    byte[] changed = signed.payload();
    changed[0] ^= 1;
    assertNull(
        RudpAuth.unprotect(
            a.ed25519PubRaw(),
            b.ed25519PubRaw(),
            transcript,
            new RudpPacket(RudpPacket.Type.DATA, 1, 1, 0, changed)));
    assertNull(RudpAuth.unprotect(c.ed25519PubRaw(), b.ed25519PubRaw(), transcript, signed));
    assertNull(RudpAuth.unprotect(a.ed25519PubRaw(), c.ed25519PubRaw(), transcript, signed));
    assertNull(RudpAuth.unprotect(a.ed25519PubRaw(), b.ed25519PubRaw(), new byte[32], signed));
    assertNull(RudpAuth.unprotect(a.ed25519PubRaw(), b.ed25519PubRaw(), transcript, plain));
  }
}
