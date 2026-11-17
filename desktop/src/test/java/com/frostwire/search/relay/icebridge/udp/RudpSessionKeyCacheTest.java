/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PowTestIdentities;
import com.frostwire.search.relay.icebridge.IceBridgeAuth;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/**
 * The relay verified every packet by building the peer's JDK public key from its 32 raw bytes again
 * (a key factory and X.509 parsing, ~5 % of its CPU in a profile) and found a peer's sessions by
 * copying each session's key to compare it. A session now builds its key once and compares without
 * copying; verification with the cached key must accept and reject exactly what it did before.
 */
class RudpSessionKeyCacheTest {

  private static final InetSocketAddress ADDRESS = new InetSocketAddress("198.18.0.1", 62000);

  private final IdentityKeys local = PowTestIdentities.get(0);
  private final IdentityKeys peer = PowTestIdentities.get(1);
  private final IdentityKeys stranger = PowTestIdentities.get(2);
  private final byte[] transcript = new byte[32];

  private static byte[] otherTranscript() {
    byte[] other = new byte[32];
    other[0] = 9;
    return other;
  }

  @Test
  void aSessionBuildsItsPeersKeyOnceAndItIsTheRightKey() throws Exception {
    RudpSession session = new RudpSession(1, 2, ADDRESS, peer.ed25519PubRaw(), false);
    var key = session.remotePublicKey();
    assertNotNull(key);
    assertSame(key, session.remotePublicKey(), "built once");
    assertArrayEquals(
        IceBridgeAuth.publicKeyFromRaw(peer.ed25519PubRaw()).getEncoded(), key.getEncoded());
  }

  @Test
  void aSessionWithoutAKeyHasNoKeyUntilItIsSet() {
    RudpSession session = new RudpSession(1, 2, ADDRESS, null, false);
    assertNull(session.remotePublicKey());
    session.setRemotePub(peer.ed25519PubRaw());
    assertNotNull(session.remotePublicKey());
  }

  @Test
  void comparingAKeyNeverHandsOutTheSessionsOwnArray() {
    RudpSession session = new RudpSession(1, 2, ADDRESS, peer.ed25519PubRaw(), false);
    assertTrue(session.hasRemotePub(peer.ed25519PubRaw()));
    assertFalse(session.hasRemotePub(stranger.ed25519PubRaw()));
    assertFalse(session.hasRemotePub(null));
    assertFalse(new RudpSession(1, 2, ADDRESS, null, false).hasRemotePub(peer.ed25519PubRaw()));
    // remotePub() still copies: changing the copy must not change the session.
    session.remotePub()[0] ^= 1;
    assertTrue(session.hasRemotePub(peer.ed25519PubRaw()));
  }

  @Test
  void verifyingWithTheCachedKeyAcceptsAndRejectsLikeBeforeIt() throws Exception {
    RudpSession session = new RudpSession(1, 2, ADDRESS, peer.ed25519PubRaw(), false);
    RudpPacket packet = new RudpPacket(RudpPacket.Type.DATA, 2, 1, 0, new byte[] {1, 2, 3});
    RudpPacket signed = RudpAuth.protect(peer, local.ed25519PubRaw(), transcript, packet);

    for (boolean cached : new boolean[] {true, false}) {
      var key = cached ? session.remotePublicKey() : null;
      RudpPacket plain =
          RudpAuth.unprotect(key, peer.ed25519PubRaw(), local.ed25519PubRaw(), transcript, signed);
      assertNotNull(plain, "valid packet, cached key: " + cached);
      assertArrayEquals(new byte[] {1, 2, 3}, plain.payload());

      byte[] tampered = signed.payload().clone();
      tampered[0] ^= 1;
      assertNull(
          RudpAuth.unprotect(
              key,
              peer.ed25519PubRaw(),
              local.ed25519PubRaw(),
              transcript,
              new RudpPacket(RudpPacket.Type.DATA, 2, 1, 0, tampered)),
          "tampered payload, cached key: " + cached);
      assertNull(
          RudpAuth.unprotect(
              key, peer.ed25519PubRaw(), local.ed25519PubRaw(), otherTranscript(), signed),
          "other transcript, cached key: " + cached);
    }

    // A packet signed by someone else is refused with the peer's key, cached or not.
    RudpPacket forged = RudpAuth.protect(stranger, local.ed25519PubRaw(), transcript, packet);
    assertNull(
        RudpAuth.unprotect(
            session.remotePublicKey(),
            peer.ed25519PubRaw(),
            local.ed25519PubRaw(),
            transcript,
            forged));
    assertEquals(
        RudpPacket.Type.DATA,
        RudpAuth.unprotect(peer.ed25519PubRaw(), local.ed25519PubRaw(), transcript, signed).type());
  }
}
