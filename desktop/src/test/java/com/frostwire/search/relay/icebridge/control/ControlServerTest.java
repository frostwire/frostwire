/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeAuth;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlServerTest {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private PeerRegistry registry;
    private IceBridgeMetrics metrics;
    private ControlServer server;
    private IdentityKeys identity;

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
        server = new ControlServer(registry, metrics, config);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void healthReturnsOk() throws Exception {
        HttpResponse<String> response = get("/health");
        assertEquals(200, response.statusCode());
        ApiResponse<?> body = GSON.fromJson(response.body(), ApiResponse.class);
        assertTrue(body.ok);
        assertEquals("ok", body.data);
    }

    @Test
    void registerAndLookupForwarder() throws Exception {
        String pubB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.ed25519PubRaw());
        RegisterRequest req = new RegisterRequest();
        req.pub = pubB64;
        req.host = "198.51.100.1";
        req.rudpPort = 6888;
        req.role = IceBridgeConfig.Role.FORWARDER;
        req.timestamp = System.currentTimeMillis() / 1000;
        req.signature = signRegister(req);

        HttpResponse<String> registerResponse = post("/register", req);
        assertEquals(200, registerResponse.statusCode(), registerResponse.body());
        ApiResponse<?> registerBody = GSON.fromJson(registerResponse.body(), ApiResponse.class);
        assertTrue(registerBody.ok, registerResponse.body());
        assertEquals(1, registry.size());

        HttpResponse<String> lookupResponse = get("/lookup?count=10");
        assertEquals(200, lookupResponse.statusCode());
        java.lang.reflect.Type type = new TypeToken<ApiResponse<List<PeerInfo>>>() {
        }.getType();
        ApiResponse<List<PeerInfo>> lookupBody = GSON.fromJson(lookupResponse.body(), type);
        assertTrue(lookupBody.ok, lookupResponse.body());
        assertNotNull(lookupBody.data);
        assertEquals(1, lookupBody.data.size());
        assertEquals("198.51.100.1", lookupBody.data.get(0).host);
    }

    @Test
    void registerRejectsBadSignature() throws Exception {
        String pubB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.ed25519PubRaw());
        RegisterRequest req = new RegisterRequest();
        req.pub = pubB64;
        req.host = "198.51.100.1";
        req.rudpPort = 6888;
        req.role = IceBridgeConfig.Role.FORWARDER;
        req.timestamp = System.currentTimeMillis() / 1000;
        req.signature = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);

        HttpResponse<String> response = post("/register", req);
        assertEquals(400, response.statusCode());
        ApiResponse<?> body = GSON.fromJson(response.body(), ApiResponse.class);
        assertFalse(body.ok);
        assertEquals("invalid signature", body.error);
    }

    private String signRegister(RegisterRequest req) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(req.canonicalString().getBytes(StandardCharsets.UTF_8));
        byte[] sig = signer.sign();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}