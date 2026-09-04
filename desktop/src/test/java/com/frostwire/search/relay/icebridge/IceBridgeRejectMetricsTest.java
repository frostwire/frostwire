/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.frostwire.util.Hex;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Slice M: reject counters start at zero, increment exactly once per call,
 * reset back to zero, and appear in the {@code GET /metrics} output.
 *
 * <p>Zero behavior change: nothing in production calls the increment methods yet.
 */
class IceBridgeRejectMetricsTest {

    private ControlServer server;
    private IceBridgeMetrics metrics;
    private String authToken;
    private HttpClient http;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        IdentityKeys identity = IdentityKeys.generate(0);
        IceBridgeConfig config =
                IceBridgeConfig.newBuilder()
                        .controlHttpPort(freePort())
                        .rudpPort(0)
                        .role(IceBridgeConfig.Role.BOTH)
                        .maxPeers(100)
                        .peerTtlSec(120)
                        .maxQpsPerKey(100.0)
                        .build();
        PeerRegistry registry = new PeerRegistry(config);
        metrics = new IceBridgeMetrics();
        InboundMessageQueue queue = new InboundMessageQueue();
        RudpSessionManager rudp = new RudpSessionManager(identity, registry, metrics, queue);

        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        authToken = Hex.encode(tokenBytes);

        File tmpTokens = File.createTempFile("ice-reject-metrics-tokens-", ".txt");
        tmpTokens.deleteOnExit();
        try (FileWriter fw = new FileWriter(tmpTokens)) {
            fw.write(authToken + "\n");
        }
        IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
        server = new ControlServer(registry, metrics, config, rudp, queue, tokens);
        server.start();
        port = server.port();
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void rejectCountersStartAtZero() {
        IceBridgeMetrics fresh = new IceBridgeMetrics();
        assertEquals(0, fresh.helloRejectedCount());
        assertEquals(0, fresh.relayRateLimitedCount());
        assertEquals(0, fresh.searchRateLimitedCount());
        assertEquals(0, fresh.powRejectedCount());
        assertEquals(0, fresh.spamMarkedDroppedCount());
    }

    @Test
    void eachIncrementAddsExactlyOne() {
        IceBridgeMetrics fresh = new IceBridgeMetrics();
        fresh.helloRejected();
        fresh.relayRateLimited();
        fresh.searchRateLimited();
        fresh.powRejected();
        fresh.spamMarkedDropped();
        assertEquals(1, fresh.helloRejectedCount());
        assertEquals(1, fresh.relayRateLimitedCount());
        assertEquals(1, fresh.searchRateLimitedCount());
        assertEquals(1, fresh.powRejectedCount());
        assertEquals(1, fresh.spamMarkedDroppedCount());

        fresh.helloRejected();
        assertEquals(2, fresh.helloRejectedCount());
        assertEquals(1, fresh.relayRateLimitedCount());
        assertEquals(1, fresh.searchRateLimitedCount());
        assertEquals(1, fresh.powRejectedCount());
        assertEquals(1, fresh.spamMarkedDroppedCount());
    }

    @Test
    void resetZeroesRejectCounters() {
        IceBridgeMetrics fresh = new IceBridgeMetrics();
        fresh.helloRejected();
        fresh.relayRateLimited();
        fresh.searchRateLimited();
        fresh.powRejected();
        fresh.spamMarkedDropped();
        fresh.reset();
        assertEquals(0, fresh.helloRejectedCount());
        assertEquals(0, fresh.relayRateLimitedCount());
        assertEquals(0, fresh.searchRateLimitedCount());
        assertEquals(0, fresh.powRejectedCount());
        assertEquals(0, fresh.spamMarkedDroppedCount());
    }

    @Test
    void metricsEndpointExposesRejectCounters() throws Exception {
        metrics.helloRejected();
        metrics.helloRejected();
        metrics.relayRateLimited();
        HttpResponse<String> response =
                http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://127.0.0.1:" + port + "/metrics"))
                                .header("X-IceBridge-Token", authToken)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("\"helloRejected\":2"), "missing helloRejected=2, got: " + body);
        assertTrue(body.contains("\"relayRateLimited\":1"), "missing relayRateLimited=1, got: " + body);
        assertTrue(body.contains("\"searchRateLimited\":0"), "missing searchRateLimited=0, got: " + body);
        assertTrue(body.contains("\"powRejected\":0"), "missing powRejected=0, got: " + body);
        assertTrue(body.contains("\"spamMarkedDropped\":0"), "missing spamMarkedDropped=0, got: " + body);
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
