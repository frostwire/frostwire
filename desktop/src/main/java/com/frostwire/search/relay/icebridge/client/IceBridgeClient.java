/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.control.ApiResponse;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import com.frostwire.search.relay.icebridge.control.RegisterRequest;
import com.frostwire.search.relay.icebridge.control.RouteRequest;
import com.frostwire.search.relay.icebridge.control.SendRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import java.util.stream.Collectors;

/**
 * HTTP client for the local IceBridge control API.
 *
 * <p>All methods target {@code http://127.0.0.1:<controlPort>} so FrostWire can
 * talk to the co-located IceBridge daemon started by
 * {@link IceBridgeProcessLauncher}.
 */
public final class IceBridgeClient implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final String baseUrl;
    private volatile String authToken;

    public IceBridgeClient(int controlPort) {
        this("http://127.0.0.1:" + controlPort);
    }

    public IceBridgeClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Set the auth token to include in the {@code X-IceBridge-Token} header
     * of every control API request (except /health).
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Check that the daemon is alive and responding.
     */
    public boolean health() {
        try {
            ApiResponse<?> response = get("/health", new TypeToken<ApiResponse<?>>() {
            });
            return response != null && response.ok;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Look up recent forward-capable peers.
     *
     * @param count max peers to return
     */
    public List<PeerInfo> lookup(int count) {
        ApiResponse<List<PeerInfo>> response = get("/lookup?count=" + count,
                new TypeToken<ApiResponse<List<PeerInfo>>>() {
                });
        return response == null || response.data == null ? Collections.emptyList() : response.data;
    }

    /**
     * Register this node's identity and endpoint with the local IceBridge.
     */
    public boolean register(IdentityKeys identity, String host, int rudpPort,
                            IceBridgeConfig.Role role) {
        if (identity == null || host == null || host.isEmpty() || rudpPort <= 0 || role == null) {
            return false;
        }
        String pubB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.ed25519PubRaw());
        long timestamp = System.currentTimeMillis() / 1000;

        RegisterRequest req = new RegisterRequest();
        req.pub = pubB64;
        req.host = host;
        req.rudpPort = rudpPort;
        req.role = role;
        req.timestamp = timestamp;

        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(identity.ed25519().getPrivate());
            signer.update(req.canonicalString().getBytes(StandardCharsets.UTF_8));
            req.signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (Exception e) {
            return false;
        }

        ApiResponse<?> response = post("/register", req, new TypeToken<ApiResponse<?>>() {
        });
        return response != null && response.ok;
    }

    /**
     * Add a peer to the local IceBridge daemon's registry without a
     * signature. This is a localhost-trusted call used by the desktop to
     * tell the daemon where to route packets for a discovered peer.
     */
    public boolean route(byte[] peerPub, String host, int rudpPort,
                         IceBridgeConfig.Role role) {
        if (peerPub == null || peerPub.length != 32
                || host == null || host.isEmpty()
                || rudpPort <= 0 || role == null) {
            return false;
        }
        RouteRequest req = new RouteRequest();
        req.pub = Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub);
        req.host = host;
        req.rudpPort = rudpPort;
        req.role = role;
        ApiResponse<?> response = post("/route", req, new TypeToken<ApiResponse<?>>() {
        });
        return response != null && response.ok;
    }

    /**
     * Send an opaque payload to a target peer identified by public key.
     */
    public boolean send(byte[] targetPub, byte[] payload) {
        if (targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            return false;
        }
        SendRequest req = new SendRequest();
        req.targetPub = Base64.getUrlEncoder().withoutPadding().encodeToString(targetPub);
        req.payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

        ApiResponse<?> response = post("/send", req, new TypeToken<ApiResponse<?>>() {
        });
        return response != null && response.ok;
    }

    /**
     * Poll the local IceBridge for payloads addressed to us.
     */
    public List<InboundMessage> poll(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        ApiResponse<List<com.frostwire.search.relay.icebridge.control.InboundMessageInfo>> response =
                get("/poll?count=" + count,
                        new TypeToken<ApiResponse<List<com.frostwire.search.relay.icebridge.control.InboundMessageInfo>>>() {
                        });
        if (response == null || response.data == null) {
            return Collections.emptyList();
        }
        return response.data.stream()
                .map(info -> new InboundMessage(
                        decode(info.sourcePub),
                        decode(info.payload),
                        info.receivedMs))
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        // HttpClient does not need explicit shutdown.
    }

    private <T> T get(String path, TypeToken<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .GET();
            addAuthHeader(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return GSON.fromJson(response.body(), type.getType());
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T post(String path, Object body, TypeToken<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
            addAuthHeader(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return GSON.fromJson(response.body(), type.getType());
        } catch (Exception e) {
            return null;
        }
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        String token = authToken;
        if (token != null && !token.isEmpty()) {
            builder.header("X-IceBridge-Token", token);
        }
    }

    private static byte[] decode(String base64) {
        return base64 == null ? new byte[0] : Base64.getUrlDecoder().decode(base64);
    }

    /**
     * A received payload decoded from {@code /poll}.
     */
    public static final class InboundMessage {
        public final byte[] sourcePub;
        public final byte[] payload;
        public final long receivedMs;

        public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs) {
            this.sourcePub = sourcePub;
            this.payload = payload;
            this.receivedMs = receivedMs;
        }
    }
}
