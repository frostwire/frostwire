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
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RudpSessionManagerComprehensiveTest {
  @Test
  void duplicateDataIsReAckedWithoutRedelivery() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      RudpPacket packet = f.signed(RudpPacket.Type.DATA, 1, 0, new byte[] {1});
      f.receive(packet);
      RudpPacket ack = f.take(RudpPacket.Type.DATA_ACK);
      f.receive(packet);
      assertEquals(ack, f.take(RudpPacket.Type.DATA_ACK));
      assertEquals(1, delivered.get());
      f.receive(f.signed(RudpPacket.Type.DATA, 2, 0, new byte[] {2}));
      assertEquals(2, delivered.get());
    }
  }

  @Test
  void outOfOrderDataIsRejectedThenRetryAccepted() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      RudpPacket second = f.signed(RudpPacket.Type.DATA, 2, 0, new byte[] {2});
      f.receive(second);
      assertEquals(0, delivered.get());
      assertTrue(f.drain().isEmpty());
      f.receive(f.signed(RudpPacket.Type.DATA, 1, 0, new byte[] {1}));
      f.receive(second);
      assertEquals(2, delivered.get());
      assertEquals(2, f.drain().size());
    }
  }

  @Test
  void fullQueueDoesNotAckUntilAdmission() throws Exception {
    InboundMessageQueue queue = new InboundMessageQueue(1);
    try (RudpFixture f = new RudpFixture(queue)) {
      f.handshake();
      f.receive(f.signed(RudpPacket.Type.DATA, 1, 0, new byte[] {1}));
      assertEquals(1, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      RudpPacket second = f.signed(RudpPacket.Type.DATA, 2, 0, new byte[] {2});
      f.receive(second);
      assertTrue(f.drain().isEmpty());
      assertArrayEquals(new byte[] {1}, queue.poll(1).get(0).payload());
      f.receive(second);
      assertEquals(2, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      assertArrayEquals(new byte[] {2}, queue.poll(1).get(0).payload());
    }
  }

  @Test
  void completedMultiFragmentMessageSurvivesBackpressureAndDuplicateRetry() throws Exception {
    InboundMessageQueue queue = new InboundMessageQueue(1);
    try (RudpFixture f = new RudpFixture(queue)) {
      f.handshake();
      assertTrue(f.manager.deliver(f.local.ed25519PubRaw(), new byte[] {9}));
      byte[] prefix = new byte[RudpPacket.MAX_FRAGMENT_PAYLOAD];
      Arrays.fill(prefix, (byte) 3);
      f.receive(f.signed(RudpPacket.Type.DATA_FRAG, 1, 0, fragment(42, 0, 2, prefix)));
      assertEquals(1, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      RudpPacket last =
          f.signed(RudpPacket.Type.DATA_END, 2, 0, fragment(42, 1, 2, new byte[] {4}));
      f.receive(last);
      assertTrue(f.drain().isEmpty(), "no final ACK before queue admission");
      assertArrayEquals(new byte[] {9}, queue.poll(1).get(0).payload());
      f.receive(last);
      assertEquals(2, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      byte[] expected = Arrays.copyOf(prefix, prefix.length + 1);
      expected[prefix.length] = 4;
      assertArrayEquals(expected, queue.poll(1).get(0).payload());
      f.receive(last);
      assertEquals(2, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      assertEquals(0, queue.size());
    }
  }

  @Test
  void fragmentGroupCannotBeAbandonedByDifferentPacketType() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      f.receive(f.signed(RudpPacket.Type.DATA_FRAG, 1, 0, fragment(1, 0, 2, new byte[] {1})));
      f.take(RudpPacket.Type.DATA_ACK);
      f.receive(f.signed(RudpPacket.Type.DATA, 2, 0, new byte[] {2}));
      assertEquals(0, delivered.get());
      assertTrue(f.drain().isEmpty());
      f.receive(f.signed(RudpPacket.Type.DATA_END, 2, 0, fragment(1, 1, 2, new byte[] {2})));
      assertEquals(1, delivered.get());
    }
  }

  @Test
  void outboundDataIsHeldUntilReadyAndValidAckClearsIt() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      assertTrue(f.manager.sendData(f.address, new byte[] {1}));
      assertEquals(2, f.manager.pendingCountForTest(f.address));
      f.finishOutbound();
      RudpPacket data = f.take(RudpPacket.Type.DATA);
      assertNotNull(
          RudpAuth.unprotect(f.local.ed25519PubRaw(), f.peer.ed25519PubRaw(), f.transcript, data));
      assertEquals(1, f.manager.pendingCountForTest(f.address));
      f.receive(f.signed(RudpPacket.Type.DATA_ACK, 0, 2, new byte[0]));
      assertEquals(1, f.manager.pendingCountForTest(f.address), "impossible ACK is rejected");
      f.receive(f.signed(RudpPacket.Type.DATA_ACK, 0, 1, new byte[0]));
      assertEquals(0, f.manager.pendingCountForTest(f.address));
    }
  }

  @Test
  void unsignedAndWrongTranscriptPacketsCannotAdvanceReceiveOrAck() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      assertTrue(f.manager.sendData(f.address, new byte[] {8}));
      f.take(RudpPacket.Type.DATA);
      f.receive(new RudpPacket(RudpPacket.Type.DATA_ACK, f.cid, 0, 1, new byte[0]));
      f.receive(new RudpPacket(RudpPacket.Type.DATA, f.cid, 1, 0, new byte[] {1}));
      f.receive(
          RudpAuth.protect(
              f.peer,
              f.local.ed25519PubRaw(),
              new byte[32],
              new RudpPacket(RudpPacket.Type.DATA, f.cid, 1, 1, new byte[] {1})));
      assertEquals(0, delivered.get());
      assertEquals(1, f.manager.pendingCountForTest(f.address));
      assertTrue(f.drain().isEmpty());
      f.receive(f.signed(RudpPacket.Type.DATA, 1, 1, new byte[] {1}));
      assertEquals(1, delivered.get());
      assertEquals(0, f.manager.pendingCountForTest(f.address));
    }
  }

  @Test
  void migrationRequiresChallengeAndRetriesUseCurrentEndpoint() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      assertTrue(f.manager.sendData(f.address, new byte[] {7}));
      f.take(RudpPacket.Type.DATA);
      InetSocketAddress moved = new InetSocketAddress("127.0.0.1", 62002);
      RudpPacket packet = f.signed(RudpPacket.Type.DATA, 1, 0, new byte[] {1});
      f.receive(packet, moved);
      assertEquals(f.address, f.manager.remoteAddressForTest(f.cid));
      assertEquals(0, delivered.get());
      RudpPacket challenge =
          RudpAuth.unprotect(
              f.local.ed25519PubRaw(),
              f.peer.ed25519PubRaw(),
              f.transcript,
              f.take(RudpPacket.Type.PATH_CHALLENGE));
      assertNotNull(challenge);
      f.receive(f.signed(RudpPacket.Type.PATH_RESPONSE, 0, 0, challenge.payload()), moved);
      assertEquals(moved, f.manager.remoteAddressForTest(f.cid));
      f.receive(packet, moved);
      assertEquals(1, delivered.get());
      f.take(RudpPacket.Type.DATA_ACK);
      session(f).pending().get(1).lastSentMs -= 10_000;
      tick(f.manager);
      RudpPacketEnvelope retried = f.channel.readOutbound();
      assertNotNull(retried);
      assertEquals(moved, retried.recipient());
      assertEquals(RudpPacket.Type.DATA, retried.packet().type());
    }
  }

  @Test
  void simultaneousOpenTransfersAllHeldDataAndKeepsOneAssociation() throws Exception {
    List<byte[]> atA = new ArrayList<>();
    List<byte[]> atB = new ArrayList<>();
    try (RudpFixture a = new RudpFixture((pub, payload) -> atA.add(payload));
        RudpFixture b = new RudpFixture((pub, payload) -> atB.add(payload))) {
      InetSocketAddress addrA = new InetSocketAddress("127.0.0.1", 63001);
      InetSocketAddress addrB = new InetSocketAddress("127.0.0.1", 63002);
      byte[] large = new byte[2500];
      Arrays.fill(large, (byte) 5);
      assertTrue(a.manager.sendData(addrB, large));
      assertTrue(b.manager.sendData(addrA, large));
      long oldA = a.manager.remoteConnectionIdForTest(addrB);
      long oldB = b.manager.remoteConnectionIdForTest(addrA);
      for (int i = 0; i < 12; i++) {
        transfer(a, b, addrA);
        transfer(b, a, addrB);
      }
      assertEquals(1, a.manager.sessionCount());
      assertEquals(1, b.manager.sessionCount());
      assertEquals(
          a.manager.remoteConnectionIdForTest(addrB), b.manager.remoteConnectionIdForTest(addrA));
      assertEquals(1, atA.size());
      assertEquals(1, atB.size());
      assertArrayEquals(large, atA.get(0));
      assertArrayEquals(large, atB.get(0));
      assertEquals(0, a.manager.pendingCountForTest(addrB));
      assertEquals(0, b.manager.pendingCountForTest(addrA));
      a.manager.shutdown();
      b.manager.shutdown();
      assertNull(a.manager.remoteAddressForTest(oldA));
      assertNull(a.manager.remoteAddressForTest(oldB));
      assertNull(b.manager.remoteAddressForTest(oldA));
      assertNull(b.manager.remoteAddressForTest(oldB));
    }
  }

  @Test
  void pendingTimeoutDropsAllAliasesAndAllowsFreshReconnect() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      assertTrue(f.manager.sendData(f.address, new byte[] {1}));
      f.cid = f.manager.remoteConnectionIdForTest(f.address);
      session(f).pending().get(0).retries = 5;
      tick(f.manager);
      assertEquals(0, f.manager.sessionCount());
      assertNull(f.manager.remoteAddressForTest(f.cid));
      assertEquals(0, f.manager.pendingCountForTest(f.address));
      assertNotEquals(-1, f.manager.connect(f.address));
      assertNotEquals(f.cid, f.manager.remoteConnectionIdForTest(f.address));
    }
  }

  @Test
  void pendingAdmissionAndSendWindowAreBounded() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      f.handshake();
      for (int i = 0; i < RudpSession.MAX_PENDING_PACKETS; i++) {
        assertTrue(f.manager.sendData(f.address, new byte[] {1}));
      }
      assertFalse(f.manager.sendData(f.address, new byte[] {2}));
      assertEquals(RudpSession.MAX_PENDING_PACKETS, f.manager.pendingCountForTest(f.address));
      assertEquals(32, f.drain().size());
      f.receive(f.signed(RudpPacket.Type.DATA_ACK, 0, 33, new byte[0]));
      assertEquals(RudpSession.MAX_PENDING_PACKETS, f.manager.pendingCountForTest(f.address));
      f.receive(f.signed(RudpPacket.Type.DATA_ACK, 0, 32, new byte[0]));
      assertEquals(RudpSession.MAX_PENDING_PACKETS - 32, f.manager.pendingCountForTest(f.address));
      assertEquals(32, f.drain().size());
    }
  }

  @Test
  void localDeliveryAndOversizedSendsRejectBeforeAdmission() throws Exception {
    InboundMessageQueue queue = new InboundMessageQueue(1);
    try (RudpFixture f = new RudpFixture(queue)) {
      assertTrue(f.manager.deliver(f.local.ed25519PubRaw(), new byte[] {1}));
      assertFalse(f.manager.deliver(f.local.ed25519PubRaw(), new byte[] {2}));
      assertFalse(
          f.manager.sendData(
              f.address, new byte[(int) FragmentReassembler.MAX_ASSEMBLED_SIZE + 1]));
      assertFalse(
          f.manager.sendRelay(
              f.address, f.peer.ed25519PubRaw(), new byte[RelayFrame.MAX_APP_PAYLOAD + 1]));
      assertEquals(0, f.manager.sessionCount());
    }
  }

  @Test
  void inboundAndOutboundSessionsShareTheConfiguredCap() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      assertEquals(IceBridgeConfig.DEFAULT_MAX_SESSIONS, f.manager.maxSessions());
      assertThrows(IllegalArgumentException.class, () -> f.manager.setMaxSessions(0));
      f.manager.setMaxSessions(2);
      f.handshake();
      assertNotEquals(-1, f.manager.connect(new InetSocketAddress("127.0.0.1", 63002)));
      assertEquals(-1, f.manager.connect(new InetSocketAddress("127.0.0.1", 63003)));
      f.receive(
          new RudpPacket(RudpPacket.Type.HELLO, 99, 0, 0, RudpAuth.createHelloPayload(f.peer, 99)),
          new InetSocketAddress("127.0.0.1", 63004));
      assertEquals(2, f.manager.sessionCount());
    }
  }

  @Test
  void relayRoutesOnlyAfterAdmissionAndPreservesHopAttribution() throws Exception {
    List<byte[]> sources = new ArrayList<>();
    List<byte[]> delivered = new ArrayList<>();
    try (RudpFixture f =
        new RudpFixture(
            (pub, payload) -> {
              sources.add(pub);
              delivered.add(payload);
            })) {
      f.handshake();
      byte[] payload = {1, 2};
      byte[] response = ByteBuffer.allocate(34).put(new byte[32]).put(payload).array();
      f.receive(f.signed(RudpPacket.Type.RELAY_RESPONSE, 1, 0, response));
      assertArrayEquals(f.peer.ed25519PubRaw(), sources.get(0));
      assertArrayEquals(payload, delivered.get(0));
      f.take(RudpPacket.Type.DATA_ACK);
      byte[] invalid = RelayFrame.encode(new byte[32], f.local.ed25519PubRaw(), 1, payload);
      f.receive(f.signed(RudpPacket.Type.RELAY, 2, 0, invalid));
      assertEquals(1, delivered.size());
      assertTrue(f.drain().isEmpty());
      byte[] valid = RelayFrame.encode(f.peer.ed25519PubRaw(), f.local.ed25519PubRaw(), 0, payload);
      f.receive(f.signed(RudpPacket.Type.RELAY, 2, 0, valid));
      assertEquals(2, delivered.size());
      assertEquals(2, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
    }
  }

  @Test
  void relayForwardsToKnownTargetAndNeverForwardsUnknownTargetAtZeroTtl() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      f.handshake();
      byte[] target = IdentityKeys.generate(0).ed25519PubRaw();
      InetSocketAddress next = new InetSocketAddress("127.0.0.1", 64001);
      assertTrue(
          f.registry.register(
              new PeerRecord(
                  target,
                  "127.0.0.1",
                  next.getPort(),
                  IceBridgeConfig.Role.FORWARDER,
                  System.currentTimeMillis())));
      f.receive(
          f.signed(
              RudpPacket.Type.RELAY,
              1,
              0,
              RelayFrame.encode(f.peer.ed25519PubRaw(), new byte[32], 0, new byte[] {1})));
      assertEquals(1, f.manager.sessionCount());
      assertTrue(f.drain().isEmpty());
      f.receive(
          f.signed(
              RudpPacket.Type.RELAY,
              1,
              0,
              RelayFrame.encode(f.peer.ed25519PubRaw(), target, 1, new byte[] {1})));
      assertEquals(2, f.manager.sessionCount());
      assertEquals(2, f.manager.pendingCountForTest(next), "HELLO plus held relay response");
      f.take(RudpPacket.Type.HELLO);
      assertEquals(1, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
    }
  }

  @Test
  void introductionsRemainDisabledEvenForAuthenticatedPeers() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      f.handshake();
      for (RudpPacket.Type type :
          new RudpPacket.Type[] {RudpPacket.Type.HOLE_PUNCH, RudpPacket.Type.HOLE_PUNCH_RESPONSE}) {
        f.receive(f.signed(type, 1, 0, new byte[32]));
      }
      assertEquals(1, f.manager.sessionCount());
      assertEquals(0, f.registry.size());
      assertTrue(f.drain().isEmpty());
    }
  }

  @Test
  void metricsCountActualWireWrites() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      f.handshake();
      assertEquals(2, f.metrics.rudpPacketsIn());
      assertEquals(2, f.metrics.rudpPacketsOut());
    }
  }

  @Test
  void lostReadyRecoversFromFinishRetryWithoutChangingSession() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      f.handshake();
      f.receive(f.signed(RudpPacket.Type.HELLO_FINISH, 0, 0, new byte[0]));
      assertNotNull(
          RudpAuth.unprotect(
              f.local.ed25519PubRaw(),
              f.peer.ed25519PubRaw(),
              f.transcript,
              f.take(RudpPacket.Type.HELLO_READY)));
      assertEquals(1, f.manager.sessionCount());
      assertTrue(f.manager.hasRemotePubForTest(f.address));
    }
  }

  @Test
  void wrongExpectedPeerIsRejectedBeforeCreatingSession() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> fail("unexpected delivery"))) {
      byte[] expected = IdentityKeys.generate(0).ed25519PubRaw();
      f.receive(
          new RudpPacket(
              RudpPacket.Type.HELLO,
              f.cid,
              0,
              0,
              RudpAuth.createHelloPayload(f.peer, f.cid, expected)));
      assertEquals(0, f.manager.sessionCount());
      assertEquals(0, f.registry.size());
      assertTrue(f.drain().isEmpty());
    }
  }

  @Test
  void threeIndependentAuthenticatedSessionsDeliverOnOneManager() throws Exception {
    AtomicInteger delivered = new AtomicInteger();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      for (int i = 0; i < 3; i++) {
        IdentityKeys peer = IdentityKeys.generate(0);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 63000 + i);
        long cid = 100 + i;
        byte[] hello = RudpAuth.createHelloPayload(peer, cid, f.local.ed25519PubRaw());
        f.receive(new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, hello), address);
        byte[] transcript = RudpAuth.transcript(hello, f.take(RudpPacket.Type.HELLO_ACK).payload());
        f.receive(
            RudpAuth.protect(
                peer,
                f.local.ed25519PubRaw(),
                transcript,
                new RudpPacket(RudpPacket.Type.HELLO_FINISH, cid, 0, 0, new byte[0])),
            address);
        f.take(RudpPacket.Type.HELLO_READY);
        f.receive(
            RudpAuth.protect(
                peer,
                f.local.ed25519PubRaw(),
                transcript,
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, new byte[] {(byte) i})),
            address);
        assertEquals(1, f.take(RudpPacket.Type.DATA_ACK).ackThrough());
      }
      assertEquals(3, delivered.get());
      assertEquals(3, f.manager.sessionCount());
    }
  }

  @Test
  void pendingBytesAndEnqueueLifetimeAreBoundedBeforeHandshake() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {})) {
      byte[] large = new byte[(int) FragmentReassembler.MAX_ASSEMBLED_SIZE];
      assertTrue(f.manager.sendData(f.address, large));
      assertFalse(f.manager.sendData(f.address, large));
      f.cid = f.manager.remoteConnectionIdForTest(f.address);
      assertEquals(257, f.manager.pendingCountForTest(f.address));
      session(f).pending().get(0).firstSentMs -= 16_000;
      tick(f.manager);
      assertEquals(0, f.manager.sessionCount());
      assertEquals(0, f.manager.pendingCountForTest(f.address));
    }
  }

  @Test
  void twoRealServersExchangeSingleAndFragmentedPayloads() throws Exception {
    IdentityKeys a = IdentityKeys.generate(0);
    IdentityKeys b = IdentityKeys.generate(0);
    IceBridgeConfig config = IceBridgeConfig.newBuilder().controlHttpPort(8797).rudpPort(0).build();
    List<byte[]> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);
    RudpSessionManager ma =
        new RudpSessionManager(
            a, new PeerRegistry(config), new IceBridgeMetrics(), (pub, payload) -> {});
    RudpSessionManager mb =
        new RudpSessionManager(
            b,
            new PeerRegistry(config),
            new IceBridgeMetrics(),
            (pub, payload) -> {
              received.add(payload);
              latch.countDown();
            });
    RudpServer sa = new RudpServer(config, ma);
    RudpServer sb = new RudpServer(config, mb);
    try {
      sa.start();
      sb.start();
      InetSocketAddress target = new InetSocketAddress("127.0.0.1", sb.port());
      byte[] single = new byte[RudpPacket.MAX_FRAGMENT_PAYLOAD];
      byte[] large = new byte[RudpPacket.MAX_FRAGMENT_PAYLOAD * 3 + 200];
      Arrays.fill(large, (byte) 17);
      assertTrue(ma.sendData(target, single));
      assertTrue(ma.sendData(target, large));
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(2, received.size());
      assertArrayEquals(single, received.get(0));
      assertArrayEquals(large, received.get(1));
    } finally {
      sa.close();
      sb.close();
      ma.shutdown();
      mb.shutdown();
    }
  }

  private static byte[] fragment(int group, int index, int total, byte[] payload) {
    return ByteBuffer.allocate(12 + payload.length)
        .putInt(group)
        .putInt(index)
        .putInt(total)
        .put(payload)
        .array();
  }

  private static void transfer(RudpFixture from, RudpFixture to, InetSocketAddress sender) {
    for (RudpPacketEnvelope envelope : from.drain()) {
      to.receive(envelope.packet(), sender);
    }
  }

  @SuppressWarnings("unchecked")
  private static RudpSession session(RudpFixture f) throws Exception {
    Field field = RudpSessionManager.class.getDeclaredField("sessionsByRemoteId");
    field.setAccessible(true);
    return ((Map<Long, RudpSession>) field.get(f.manager)).get(f.cid);
  }

  private static void tick(RudpSessionManager manager) throws Exception {
    Method method = RudpSessionManager.class.getDeclaredMethod("retransmitAndEvict");
    method.setAccessible(true);
    method.invoke(manager);
  }
}
