/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** In-memory wire fixture. Never bypasses manager authentication or packet signing. */
final class RudpFixture implements AutoCloseable {
  final IdentityKeys local = IdentityKeys.generate(0);
  final IdentityKeys peer = IdentityKeys.generate(0);
  final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 62001);
  final EmbeddedChannel channel = new EmbeddedChannel();
  final PeerRegistry registry =
      new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8797).build());
  final IceBridgeMetrics metrics = new IceBridgeMetrics();
  final RudpSessionManager manager;
  long cid = 424242;
  byte[] transcript;

  RudpFixture(RudpMessageListener listener) throws Exception {
    manager = new RudpSessionManager(local, registry, metrics, listener);
    manager.setChannel(channel);
  }

  void handshake() throws Exception {
    byte[] hello = RudpAuth.createHelloPayload(peer, cid, local.ed25519PubRaw());
    receive(new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, hello));
    assertEquals(1, manager.sessionCount());
    assertFalse(manager.hasRemotePubForTest(address));
    RudpPacket ack = take(RudpPacket.Type.HELLO_ACK);
    assertTrue(RudpAuth.verifyAck(cid, hello, ack.payload()));
    transcript = RudpAuth.transcript(hello, ack.payload());
    receive(signed(RudpPacket.Type.HELLO_FINISH, 0, 0, new byte[0]));
    assertNotNull(
        RudpAuth.unprotect(
            local.ed25519PubRaw(),
            peer.ed25519PubRaw(),
            transcript,
            take(RudpPacket.Type.HELLO_READY)));
    assertTrue(manager.hasRemotePubForTest(address));
  }

  void finishOutbound() throws Exception {
    RudpPacket hello = take(RudpPacket.Type.HELLO);
    cid = hello.connectionId();
    byte[] ack = RudpAuth.createAckPayload(peer, cid, hello.payload());
    transcript = RudpAuth.transcript(hello.payload(), ack);
    receive(new RudpPacket(RudpPacket.Type.HELLO_ACK, cid, 0, 0, ack));
    assertFalse(manager.hasRemotePubForTest(address), "ACK alone is not READY");
    assertNotNull(
        RudpAuth.unprotect(
            local.ed25519PubRaw(),
            peer.ed25519PubRaw(),
            transcript,
            take(RudpPacket.Type.HELLO_FINISH)));
    receive(signed(RudpPacket.Type.HELLO_READY, 0, 0, new byte[0]));
    assertTrue(manager.hasRemotePubForTest(address));
  }

  RudpPacket signed(RudpPacket.Type type, int sequence, int ack, byte[] payload) throws Exception {
    return RudpAuth.protect(
        peer, local.ed25519PubRaw(), transcript, new RudpPacket(type, cid, sequence, ack, payload));
  }

  void receive(RudpPacket packet) {
    receive(packet, address);
  }

  void receive(RudpPacket packet, InetSocketAddress sender) {
    manager.onPacket(new RudpPacketEnvelope(packet, sender, address));
  }

  RudpPacket take(RudpPacket.Type type) {
    RudpPacketEnvelope envelope = channel.readOutbound();
    assertNotNull(envelope, "missing " + type);
    assertEquals(type, envelope.packet().type());
    return envelope.packet();
  }

  List<RudpPacketEnvelope> drain() {
    List<RudpPacketEnvelope> packets = new ArrayList<>();
    RudpPacketEnvelope envelope;
    while ((envelope = channel.readOutbound()) != null) {
      packets.add(envelope);
    }
    return packets;
  }

  @Override
  public void close() {
    manager.shutdown();
    channel.finishAndReleaseAll();
  }
}
