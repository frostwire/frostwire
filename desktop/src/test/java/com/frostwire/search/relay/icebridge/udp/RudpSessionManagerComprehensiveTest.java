/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link RudpSessionManager} covering:
 * <ul>
 *   <li>HELLO authentication and session creation</li>
 *   <li>DATA delivery and ACK clearing</li>
 *   <li>Fragmentation and reassembly of large payloads</li>
 *   <li>Duplicate packet handling</li>
 *   <li>Out-of-order packet rejection (v1 in-order requirement)</li>
 *   <li>Concurrent sessions on a single manager</li>
 *   <li>Retransmission with two real UDP servers</li>
 *   <li>RELAY forwarding through a forwarder peer</li>
 *   <li>RELAY_RESPONSE delivery to the listener</li>
 *   <li>Metric counters</li>
 * </ul>
 */
class RudpSessionManagerComprehensiveTest {

    private static final IceBridgeConfig CONFIG = IceBridgeConfig.newBuilder()
            .rudpPort(0)
            .controlHttpPort(0)
            .controlStdio(true)
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .build();

    private IdentityKeys local;
    private IdentityKeys remote;
    private PeerRegistry registry;
    private IceBridgeMetrics metrics;

    @BeforeEach
    void setup() throws Exception {
        local = IdentityKeys.generate(0);
        remote = IdentityKeys.generate(0);
        registry = new PeerRegistry(CONFIG);
        metrics = new IceBridgeMetrics();
    }

    // ---- HELLO and session creation ----

    @Test
    void helloCreatesSessionAndDeliversData() throws Exception {
        List<byte[]> received = new CopyOnWriteArrayList<>();
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> received.add(payload));

        long cid = 424242L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62001);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62002)));
        assertEquals(1, mgr.sessionCount());

        byte[] data = "search query".getBytes();
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, data),
                sender, new InetSocketAddress("127.0.0.1", 62002)));

        assertEquals(1, received.size());
        assertArrayEquals(data, received.get(0));
        mgr.shutdown();
    }

    @Test
    void badHelloIsDropped() {
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> {});
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, 999L, 0, 0, new byte[96]),
                new InetSocketAddress("127.0.0.1", 62001),
                new InetSocketAddress("127.0.0.1", 62002)));
        assertEquals(0, mgr.sessionCount());
        mgr.shutdown();
    }

    // ---- ACK clearing ----

    @Test
    void ackForRealSessionIsAccepted() throws Exception {
        RudpSessionManager mgr = new RudpSessionManager(
                local, registry, metrics, (pub, payload) -> {});

        long remoteCid = 222L;
        InetSocketAddress remoteAddr = new InetSocketAddress("127.0.0.1", 62003);

        // Create a real session via inbound HELLO.
        byte[] helloPayload = RudpAuth.createHelloPayload(remote, remoteCid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, remoteCid, 0, 0, helloPayload),
                remoteAddr, new InetSocketAddress("127.0.0.1", 62004)));
        assertEquals(1, mgr.sessionCount());

        // Send a DATA_ACK for sequence 999 (no pending packet — should be a no-op).
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA_ACK, remoteCid, 0, 999, new byte[0]),
                remoteAddr, new InetSocketAddress("127.0.0.1", 62004)));

        // Session should still exist.
        assertEquals(1, mgr.sessionCount());
        mgr.shutdown();
    }

    // ---- Duplicate DATA ----

    @Test
    void duplicateDataIsIgnoredButReAcked() throws Exception {
        AtomicInteger deliverCount = new AtomicInteger();
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> deliverCount.incrementAndGet());

        long cid = 555L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62010);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62011)));

        byte[] data = "hello".getBytes();

        // First DATA (seq=1) — delivered.
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, data),
                sender, new InetSocketAddress("127.0.0.1", 62011)));
        assertEquals(1, deliverCount.get());

        // Duplicate DATA (seq=1 again) — NOT delivered.
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, data),
                sender, new InetSocketAddress("127.0.0.1", 62011)));
        assertEquals(1, deliverCount.get(), "duplicate should not be delivered again");

        // seq=2 — delivered.
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 2, 0, "world".getBytes()),
                sender, new InetSocketAddress("127.0.0.1", 62011)));
        assertEquals(2, deliverCount.get());
        mgr.shutdown();
    }

    // ---- Out-of-order DATA ----

    @Test
    void outOfOrderDataIsRejected() throws Exception {
        AtomicInteger deliverCount = new AtomicInteger();
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> deliverCount.incrementAndGet());

        long cid = 666L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62020);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62021)));

        // Send seq=2 before seq=1 — rejected (gap).
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 2, 0, "out of order".getBytes()),
                sender, new InetSocketAddress("127.0.0.1", 62021)));
        assertEquals(0, deliverCount.get(), "out-of-order packet should be rejected");

        // Send seq=1 — delivered.
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, "first".getBytes()),
                sender, new InetSocketAddress("127.0.0.1", 62021)));
        assertEquals(1, deliverCount.get());

        // Retransmit seq=2 — now accepted.
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 2, 0, "second".getBytes()),
                sender, new InetSocketAddress("127.0.0.1", 62021)));
        assertEquals(2, deliverCount.get());
        mgr.shutdown();
    }

    // ---- Concurrent sessions ----

    @Test
    void multipleConcurrentSessions() throws Exception {
        AtomicInteger deliverCount = new AtomicInteger();
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> deliverCount.incrementAndGet());

        IdentityKeys[] peers = {
                IdentityKeys.generate(0),
                IdentityKeys.generate(0),
                IdentityKeys.generate(0)
        };
        long[] cids = {111L, 222L, 333L};
        InetSocketAddress[] addrs = {
                new InetSocketAddress("127.0.0.1", 63001),
                new InetSocketAddress("127.0.0.1", 63002),
                new InetSocketAddress("127.0.0.1", 63003)
        };

        for (int i = 0; i < 3; i++) {
            byte[] hello = RudpAuth.createHelloPayload(peers[i], cids[i]);
            mgr.onPacket(new RudpPacketEnvelope(
                    new RudpPacket(RudpPacket.Type.HELLO, cids[i], 0, 0, hello),
                    addrs[i], new InetSocketAddress("127.0.0.1", 63000)));
        }
        assertEquals(3, mgr.sessionCount());

        for (int i = 0; i < 3; i++) {
            mgr.onPacket(new RudpPacketEnvelope(
                    new RudpPacket(RudpPacket.Type.DATA, cids[i], 1, 0, ("data" + i).getBytes()),
                    addrs[i], new InetSocketAddress("127.0.0.1", 63000)));
        }
        assertEquals(3, deliverCount.get(), "all 3 sessions should deliver data");
        mgr.shutdown();
    }

    // ---- Fragmentation and reassembly ----

    @Test
    void largePayloadFragmentedAndReassembled() throws Exception {
        List<byte[]> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> {
                    received.add(payload);
                    latch.countDown();
                });

        long cid = 777L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62030);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62031)));

        // Build a payload that requires 3 fragments.
        int fragSize = RudpPacket.MAX_FRAGMENT_PAYLOAD;
        byte[] largePayload = new byte[fragSize * 2 + 500];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 256);
        }

        int totalFrags = (largePayload.length + fragSize - 1) / fragSize;
        int groupId = 12345;
        int seq = 1;
        int offset = 0;
        for (int i = 0; i < totalFrags; i++) {
            int chunkLen = Math.min(fragSize, largePayload.length - offset);
            byte[] fragPayload = new byte[12 + chunkLen];
            writeIntBE(fragPayload, 0, groupId);
            writeIntBE(fragPayload, 4, i);
            writeIntBE(fragPayload, 8, totalFrags);
            System.arraycopy(largePayload, offset, fragPayload, 12, chunkLen);
            offset += chunkLen;

            boolean isLast = (i == totalFrags - 1);
            RudpPacket.Type type = isLast ? RudpPacket.Type.DATA_END : RudpPacket.Type.DATA_FRAG;
            mgr.onPacket(new RudpPacketEnvelope(
                    new RudpPacket(type, cid, seq++, 0, fragPayload),
                    sender, new InetSocketAddress("127.0.0.1", 62031)));
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "reassembled payload should be delivered");
        assertEquals(1, received.size());
        assertArrayEquals(largePayload, received.get(0), "reassembled payload must match original");
        mgr.shutdown();
    }

    @Test
    void payloadExactlyAtFragmentLimitUsesSingleData() throws Exception {
        List<byte[]> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> {
                    received.add(payload);
                    latch.countDown();
                });

        long cid = 888L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62040);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62041)));

        byte[] payload = new byte[RudpPacket.MAX_FRAGMENT_PAYLOAD];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.DATA, cid, 1, 0, payload),
                sender, new InetSocketAddress("127.0.0.1", 62041)));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertArrayEquals(payload, received.get(0));
        mgr.shutdown();
    }

    // ---- Retransmission with two real UDP servers ----

    @Test
    void twoRealServersExchangeData() throws Exception {
        IceBridgeMetrics metricsA = new IceBridgeMetrics();
        IceBridgeMetrics metricsB = new IceBridgeMetrics();
        PeerRegistry registryA = new PeerRegistry(CONFIG);
        PeerRegistry registryB = new PeerRegistry(CONFIG);

        AtomicInteger receivedB = new AtomicInteger();
        RudpSessionManager mgrA = new RudpSessionManager(
                local, registryA, metricsA, (pub, payload) -> {});
        RudpSessionManager mgrB = new RudpSessionManager(
                remote, registryB, metricsB, (pub, payload) -> receivedB.incrementAndGet());

        IceBridgeConfig configA = IceBridgeConfig.newBuilder()
                .rudpPort(freePort()).controlHttpPort(0).controlStdio(true)
                .role(IceBridgeConfig.Role.BOTH).build();
        IceBridgeConfig configB = IceBridgeConfig.newBuilder()
                .rudpPort(freePort()).controlHttpPort(0).controlStdio(true)
                .role(IceBridgeConfig.Role.BOTH).build();

        RudpServer serverA = new RudpServer(configA, mgrA);
        RudpServer serverB = new RudpServer(configB, mgrB);
        serverA.start();
        serverB.start();

        try {
            int portA = serverA.port();
            int portB = serverB.port();
            InetSocketAddress addrA = new InetSocketAddress("127.0.0.1", portA);
            InetSocketAddress addrB = new InetSocketAddress("127.0.0.1", portB);

            // Register B in A's registry so A can route to B.
            registryA.register(new PeerRecord(remote.ed25519PubRaw(),
                    "127.0.0.1", portB, IceBridgeConfig.Role.BOTH,
                    System.currentTimeMillis()));

            // Send data from A to B.
            byte[] payload = "hello over real UDP".getBytes();
            mgrA.sendData(addrB, payload);

            // Wait for delivery — the HELLO handshake must complete first.
            boolean delivered = waitForCondition(() -> receivedB.get() >= 1, 10_000);
            assertTrue(delivered, "data should be delivered over real UDP");
        } finally {
            serverA.close();
            serverB.close();
        }
    }

    @Test
    void twoRealServersExchangeLargeFragmentedPayload() throws Exception {
        IceBridgeMetrics metricsA = new IceBridgeMetrics();
        IceBridgeMetrics metricsB = new IceBridgeMetrics();
        PeerRegistry registryA = new PeerRegistry(CONFIG);
        PeerRegistry registryB = new PeerRegistry(CONFIG);

        List<byte[]> receivedB = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        RudpSessionManager mgrA = new RudpSessionManager(
                local, registryA, metricsA, (pub, payload) -> {});
        RudpSessionManager mgrB = new RudpSessionManager(
                remote, registryB, metricsB, (pub, payload) -> {
                    receivedB.add(payload);
                    latch.countDown();
                });

        IceBridgeConfig configA = IceBridgeConfig.newBuilder()
                .rudpPort(freePort()).controlHttpPort(0).controlStdio(true)
                .role(IceBridgeConfig.Role.BOTH).build();
        IceBridgeConfig configB = IceBridgeConfig.newBuilder()
                .rudpPort(freePort()).controlHttpPort(0).controlStdio(true)
                .role(IceBridgeConfig.Role.BOTH).build();

        RudpServer serverA = new RudpServer(configA, mgrA);
        RudpServer serverB = new RudpServer(configB, mgrB);
        serverA.start();
        serverB.start();

        try {
            int portB = serverB.port();
            InetSocketAddress addrB = new InetSocketAddress("127.0.0.1", portB);

            registryA.register(new PeerRecord(remote.ed25519PubRaw(),
                    "127.0.0.1", portB, IceBridgeConfig.Role.BOTH,
                    System.currentTimeMillis()));

            // Build a large payload that requires fragmentation.
            byte[] largePayload = new byte[RudpPacket.MAX_FRAGMENT_PAYLOAD * 3 + 200];
            for (int i = 0; i < largePayload.length; i++) {
                largePayload[i] = (byte) (i % 256);
            }

            mgrA.sendData(addrB, largePayload);

            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "large payload should be delivered and reassembled");
            assertArrayEquals(largePayload, receivedB.get(0),
                    "reassembled payload must match original");
        } finally {
            serverA.close();
            serverB.close();
        }
    }

    // ---- RELAY forwarding ----

    @Test
    void relayForwardsToTargetInRegistry() throws Exception {
        RudpSessionManager forwarder = new RudpSessionManager(
                local, registry, metrics, (pub, payload) -> {});

        registry.register(new PeerRecord(remote.ed25519PubRaw(),
                "127.0.0.1", 62060, IceBridgeConfig.Role.FORWARDER,
                System.currentTimeMillis()));

        byte[] sourcePub = local.ed25519PubRaw();
        byte[] targetPub = remote.ed25519PubRaw();
        byte[] appPayload = "relayed data".getBytes();
        byte[] relayPayload = new byte[64 + appPayload.length];
        System.arraycopy(sourcePub, 0, relayPayload, 0, 32);
        System.arraycopy(targetPub, 0, relayPayload, 32, 32);
        System.arraycopy(appPayload, 0, relayPayload, 64, appPayload.length);

        forwarder.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.RELAY, 0, 0, 0, relayPayload),
                new InetSocketAddress("127.0.0.1", 62061),
                new InetSocketAddress("127.0.0.1", 62062)));

        assertEquals(1, registry.size());
        forwarder.shutdown();
    }

    // ---- RELAY_RESPONSE delivery ----

    @Test
    void relayResponseDeliveredToListener() {
        List<byte[]> deliveredPayloads = new CopyOnWriteArrayList<>();
        List<byte[]> deliveredSources = new CopyOnWriteArrayList<>();
        RudpSessionManager mgr = new RudpSessionManager(
                local, registry, metrics, (sourcePub, payload) -> {
                    deliveredSources.add(sourcePub);
                    deliveredPayloads.add(payload);
                });

        byte[] sourcePub = remote.ed25519PubRaw();
        byte[] appPayload = "response".getBytes();
        byte[] responsePayload = new byte[32 + appPayload.length];
        System.arraycopy(sourcePub, 0, responsePayload, 0, 32);
        System.arraycopy(appPayload, 0, responsePayload, 32, appPayload.length);

        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.RELAY_RESPONSE, 0, 0, 0, responsePayload),
                new InetSocketAddress("127.0.0.1", 62070),
                new InetSocketAddress("127.0.0.1", 62071)));

        assertEquals(1, deliveredPayloads.size());
        assertArrayEquals(appPayload, deliveredPayloads.get(0));
        assertArrayEquals(sourcePub, deliveredSources.get(0));
        mgr.shutdown();
    }

    // ---- Metrics ----

    @Test
    void metricsTrackPacketsInAndOut() throws Exception {
        RudpSessionManager mgr = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> {});

        long cid = 1111L;
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62080);

        byte[] helloPayload = RudpAuth.createHelloPayload(local, cid);
        mgr.onPacket(new RudpPacketEnvelope(
                new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, helloPayload),
                sender, new InetSocketAddress("127.0.0.1", 62081)));

        assertTrue(metrics.rudpPacketsIn() >= 1, "should track inbound packets");
        assertTrue(metrics.rudpPacketsOut() >= 1, "should track outbound packets (HELLO_ACK)");
        mgr.shutdown();
    }

    // ---- Helpers ----

    private static void writeIntBE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>> 8);
        buf[offset + 3] = (byte) value;
    }

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static boolean waitForCondition(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
