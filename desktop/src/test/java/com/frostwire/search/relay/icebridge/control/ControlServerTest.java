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
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
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
    private InboundMessageQueue inboundQueue;
    private RudpSessionManager rudpSessionManager;
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
        java.io.File tmpTokens = java.io.File.createTempFile("control-server-test-tokens-", ".txt");
        tmpTokens.deleteOnExit();
        IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
        tokens.addRuntimeToken(authToken);
        server = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue, tokens);
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

    @Test
    void routeAddsPeerWithoutSignature() throws Exception {
        RouteRequest req = new RouteRequest();
        req.pub = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.ed25519PubRaw());
        req.host = "198.51.100.2";
        req.rudpPort = 6889;
        req.role = IceBridgeConfig.Role.BOTH;

        HttpResponse<String> response = post("/route", req);
        assertEquals(200, response.statusCode(), response.body());
        ApiResponse<?> body = GSON.fromJson(response.body(), ApiResponse.class);
        assertTrue(body.ok, response.body());
        assertEquals(1, registry.size());
    }

    @Test
    void pollRetrievesInboundMessages() throws Exception {
        String pubB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.ed25519PubRaw());
        byte[] appPayload = "hello".getBytes(StandardCharsets.UTF_8);
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(appPayload);
        inboundQueue.onMessage(identity.ed25519PubRaw(),
                com.frostwire.search.relay.icebridge.MeshEnvelope.encodeForWire(
                        com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, appPayload));

        HttpResponse<String> response = get("/poll?count=10");
        assertEquals(200, response.statusCode());
        java.lang.reflect.Type type = new TypeToken<ApiResponse<List<InboundMessageInfo>>>() {
        }.getType();
        ApiResponse<List<InboundMessageInfo>> body = GSON.fromJson(response.body(), type);
        assertTrue(body.ok, response.body());
        assertEquals(1, body.data.size());
        assertEquals(pubB64, body.data.get(0).sourcePub);
        assertEquals(payloadB64, body.data.get(0).payload);
    }

    @Test
    void sendAcceptsTargetAndPayload() throws Exception {
        byte[] targetPub = identity.ed25519PubRaw();
        String targetB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(targetPub);
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("query".getBytes(StandardCharsets.UTF_8));

        SendRequest req = new SendRequest();
        req.targetPub = targetB64;
        req.payload = payloadB64;

        HttpResponse<String> response = post("/send", req);
        assertEquals(200, response.statusCode());
        ApiResponse<?> body = GSON.fromJson(response.body(), ApiResponse.class);
        assertTrue(body.ok, response.body());
        assertEquals("queued", body.data);
    }

    private String signRegister(RegisterRequest req) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(req.canonicalString().getBytes(StandardCharsets.UTF_8));
        byte[] sig = signer.sign();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET();
        addAuthHeader(builder, path);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        addAuthHeader(builder, path);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void addAuthHeader(HttpRequest.Builder builder, String path) {
        if (!path.startsWith("/health")) {
            builder.header("X-IceBridge-Token", authToken);
        }
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