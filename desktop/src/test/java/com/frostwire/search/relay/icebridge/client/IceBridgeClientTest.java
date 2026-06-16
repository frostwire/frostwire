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
        server = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue);
        server.start();
        client = new IceBridgeClient(server.port());
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
        assertTrue(client.send(targetPub, payload));

        inboundQueue.onMessage(targetPub, payload);
        List<IceBridgeClient.InboundMessage> messages = client.poll(10);
        assertEquals(1, messages.size());
        assertArrayEquals(targetPub, messages.get(0).sourcePub);
        assertArrayEquals(payload, messages.get(0).payload);
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
