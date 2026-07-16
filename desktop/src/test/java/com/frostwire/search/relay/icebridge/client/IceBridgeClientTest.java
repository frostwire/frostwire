/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IceBridgeClientTest {

    private IdentityKeys identity;
    private PeerRegistry registry;
    private IceBridgeMetrics metrics;
    private RudpSessionManager rudpSessionManager;
    private InboundMessageQueue inboundQueue;
    private ControlServer server;
    private IceBridgeClient client;
    private String authToken;

    @BeforeEach
    void startServer() throws Exception {
        identity = IdentityKeys.generate(0);
        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .controlHttpPort(freePort())
                .rudpPort(0)
                .role(IceBridgeConfig.Role.BOTH)
                .maxPeers(100)
                .peerTtlSec(120)
                .maxQpsPerKey(100.0)
                .build();
        registry = new PeerRegistry(config);
        metrics = new IceBridgeMetrics();
        inboundQueue = new InboundMessageQueue();
        rudpSessionManager = new RudpSessionManager(identity, registry, metrics, inboundQueue);
        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        authToken = com.frostwire.util.Hex.encode(tokenBytes);
        java.io.File tmpTokens = java.io.File.createTempFile("ice-client-test-tokens-", ".txt");
        tmpTokens.deleteOnExit();
        IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
        tokens.addRuntimeToken(authToken);
        server = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue, tokens);
        server.start();
        client = new IceBridgeClient(server.port());
        client.setAuthToken(authToken);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void healthReturnsOk() {
        assertTrue(client.health());
    }

    @Test
    void registerAndLookupForwarder() {
        assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.FORWARDER));
        List<PeerInfo> peers = client.lookup(10);
        assertEquals(1, peers.size());
        assertEquals("127.0.0.1", peers.get(0).host);
        assertEquals(6888, peers.get(0).rudpPort);
    }

    @Test
    void sendAndPollRoundTrip() {
        byte[] targetPub = identity.ed25519PubRaw();
        byte[] payload = "search query".getBytes(StandardCharsets.UTF_8);
        inboundQueue.onMessage(targetPub,
                com.frostwire.search.relay.icebridge.MeshEnvelope.encodeForWire(
                        com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, payload));
        List<IceBridgeClient.InboundMessage> messages = client.poll(10);
        assertEquals(1, messages.size());
        assertArrayEquals(targetPub, messages.get(0).sourcePub());
        assertArrayEquals(payload, messages.get(0).payload());
        assertEquals(com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH,
                messages.get(0).protocolId());
    }

    @Test
    void barePayloadIsAcceptedAsSearchFallback() {
        // Intentional: local RELAY delivery may hand bare app payloads;
        // InboundMessageQueue treats them as SEARCH (MeshProtocolId.SEARCH).
        byte[] source = identity.ed25519PubRaw();
        byte[] bare = "not-framed".getBytes(StandardCharsets.UTF_8);
        inboundQueue.onMessage(source, bare);
        List<IceBridgeClient.InboundMessage> messages = client.poll(10);
        assertEquals(1, messages.size());
        assertArrayEquals(bare, messages.get(0).payload());
        assertEquals(com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH,
                messages.get(0).protocolId());
    }

    @Test
    void inboundMessage_defensiveCopies_preventAliasing() {
        byte[] sourcePub = new byte[32];
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        IceBridgeClient.InboundMessage msg = new IceBridgeClient.InboundMessage(sourcePub, payload, 123L);

        byte[] gotSource = msg.sourcePub();
        byte[] gotPayload = msg.payload();
        gotSource[0] = (byte) 0xFF;
        gotPayload[0] = (byte) 0xFF;

        assertArrayEquals(sourcePub, msg.sourcePub());
        assertArrayEquals(payload, msg.payload());
        assertEquals(123L, msg.receivedMs());
    }

    @Test
    void inboundMessage_handlesNullArrays() {
        IceBridgeClient.InboundMessage msg = new IceBridgeClient.InboundMessage(null, null, 0L);
        assertEquals(0, msg.sourcePub().length);
        assertEquals(0, msg.payload().length);
        assertEquals(0L, msg.receivedMs());
    }

    @Test
    void close_doesNotThrow() {
        client.health();
        client.close();
        client.close();
    }

    @Test
    void route_addsPeerToRegistry() {
        byte[] peerPub = new byte[32];
        peerPub[0] = 0x42;
        assertTrue(client.route(peerPub, "10.0.0.5", 6889, IceBridgeConfig.Role.BOTH));
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
