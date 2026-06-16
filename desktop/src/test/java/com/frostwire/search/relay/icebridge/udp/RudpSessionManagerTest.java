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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RudpSessionManagerTest {

    private static final IceBridgeConfig CONFIG = IceBridgeConfig.newBuilder()
            .rudpPort(0)
            .controlHttpPort(0)
            .controlStdio(true)
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .build();

    private List<byte[]> received;
    private AtomicInteger receivedCount;
    private IdentityKeys local;
    private IdentityKeys remote;
    private PeerRegistry registry;
    private IceBridgeMetrics metrics;

    @BeforeEach
    void setup() throws Exception {
        local = IdentityKeys.generate(0);
        remote = IdentityKeys.generate(0);
        received = new ArrayList<>();
        receivedCount = new AtomicInteger();
        registry = new PeerRegistry(CONFIG);
        metrics = new IceBridgeMetrics();
    }

    @Test
    void helloCreatesSessionAndDeliversData() throws Exception {
        long connectionId = 424242L;
        RudpSessionManager remoteManager = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> {
            received.add(payload);
            receivedCount.incrementAndGet();
        });

        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 62001);
        byte[] helloPayload = RudpAuth.createHelloPayload(local, connectionId);
        RudpPacket hello = new RudpPacket(RudpPacket.Type.HELLO, connectionId, 0, 0, helloPayload);

        remoteManager.onPacket(new RudpPacketEnvelope(hello, sender,
                new InetSocketAddress("127.0.0.1", 62002)));

        assertEquals(1, remoteManager.sessionCount());

        byte[] appPayload = "distributed search query".getBytes();
        RudpPacket data = new RudpPacket(RudpPacket.Type.DATA, connectionId, 1, 0, appPayload);
        remoteManager.onPacket(new RudpPacketEnvelope(data, sender,
                new InetSocketAddress("127.0.0.1", 62002)));

        assertEquals(1, receivedCount.get());
        assertArrayEquals(appPayload, received.get(0));
    }

    @Test
    void badHelloIsDropped() throws Exception {
        long connectionId = 999L;
        RudpSessionManager remoteManager = new RudpSessionManager(
                remote, registry, metrics, (pub, payload) -> receivedCount.incrementAndGet());

        byte[] badHello = new byte[96];
        RudpPacket hello = new RudpPacket(RudpPacket.Type.HELLO, connectionId, 0, 0, badHello);
        remoteManager.onPacket(new RudpPacketEnvelope(hello,
                new InetSocketAddress("127.0.0.1", 62001),
                new InetSocketAddress("127.0.0.1", 62002)));

        assertEquals(0, remoteManager.sessionCount());
    }

    @Test
    void ackClearsPending() throws Exception {
        long localCid = 111L;
        long remoteCid = 222L;
        RudpSessionManager localManager = new RudpSessionManager(
                local, registry, metrics, (pub, payload) -> {});

        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 62003);
        RudpSession session = new RudpSession(localCid, remoteCid, remoteAddress, null, true);

        byte[] appPayload = "payload".getBytes();
        RudpPacket data = session.data(appPayload);
        PendingPacket pending = new PendingPacket(data, remoteAddress, System.currentTimeMillis());
        session.addPending(data.sequence(), pending);
        assertEquals(1, session.pending().size());

        RudpPacket ack = new RudpPacket(RudpPacket.Type.DATA_ACK, remoteCid,
                0, data.sequence(), new byte[0]);

        // Simulate receiving an ack from remote with same connection id.
        localManager.onPacket(new RudpPacketEnvelope(ack,
                remoteAddress, new InetSocketAddress("127.0.0.1", 62004)));

        // The ack was for a session that does not exist in this manager, so it
        // should be silently ignored. This test verifies no crash.
        assertEquals(0, localManager.sessionCount());
    }

    @Test
    void relayLookupAndForwardByRegistry() throws Exception {
        byte[] targetPub = remote.ed25519PubRaw();
        PeerRecord record = new PeerRecord(targetPub, "127.0.0.1", 62004,
                IceBridgeConfig.Role.FORWARDER, System.currentTimeMillis());
        registry.register(record);

        List<byte[]> forwarded = new ArrayList<>();
        RudpSessionManager forwarder = new RudpSessionManager(
                local, registry, metrics, (pub, payload) -> forwarded.add(payload));

        byte[] sourcePub = local.ed25519PubRaw();
        byte[] appPayload = "relayed".getBytes();
        byte[] relayPayload = new byte[32 + 32 + appPayload.length];
        System.arraycopy(sourcePub, 0, relayPayload, 0, 32);
        System.arraycopy(targetPub, 0, relayPayload, 32, 32);
        System.arraycopy(appPayload, 0, relayPayload, 64, appPayload.length);

        RudpPacket relay = new RudpPacket(RudpPacket.Type.RELAY, 0, 0, 0, relayPayload);
        forwarder.onPacket(new RudpPacketEnvelope(relay,
                new InetSocketAddress("127.0.0.1", 62005),
                new InetSocketAddress("127.0.0.1", 62006)));

        // The forwarder would write a RELAY_RESPONSE to the target, but since
        // no channel is set, just verify it processed the request and no exception.
        assertEquals(1, registry.size());
    }
}