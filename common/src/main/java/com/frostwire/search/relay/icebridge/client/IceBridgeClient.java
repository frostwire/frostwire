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
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client for the local IceBridge control API.
 *
 * <p>All methods target {@code http://127.0.0.1:<controlPort>} so FrostWire can
 * talk to the co-located IceBridge daemon — whether started as a subprocess
 * on desktop or in-process on Android.
 *
 * <p>Uses OkHttp (available on both desktop and Android) instead of
 * {@code java.net.http.HttpClient} (Java 11+, not available on Android).
 */
public final class IceBridgeClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeClient.class);
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long CONNECT_TIMEOUT_SEC = 10;
    private static final long CALL_TIMEOUT_SEC = 10;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final OkHttpClient http;
    private final String baseUrl;
    private volatile String authToken;

    public IceBridgeClient(int controlPort) {
        this("http://127.0.0.1:" + controlPort);
    }

    public IceBridgeClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
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
     * signature. This is a localhost-trusted call used by the desktop
     * or Android app to tell the daemon where to route packets for a
     * discovered peer.
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
        List<InboundMessage> out = new ArrayList<>(response.data.size());
        for (com.frostwire.search.relay.icebridge.control.InboundMessageInfo info : response.data) {
            out.add(new InboundMessage(
                    decode(info.sourcePub),
                    decode(info.payload),
                    info.receivedMs));
        }
        return out;
    }

    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private <T> T get(String path, TypeToken<T> type) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + path)
                    .get();
            addAuthHeader(builder);
            try (Response response = http.newCall(builder.build()).execute()) {
                String body = readBody(response);
                if (body == null) {
                    return null;
                }
                return GSON.fromJson(body, type.getType());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T post(String path, Object body, TypeToken<T> type) {
        try {
            RequestBody requestBody = RequestBody.create(GSON.toJson(body), JSON);
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + path)
                    .post(requestBody);
            addAuthHeader(builder);
            try (Response response = http.newCall(builder.build()).execute()) {
                String responseBody = readBody(response);
                if (responseBody == null) {
                    return null;
                }
                return GSON.fromJson(responseBody, type.getType());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBody(Response response) throws java.io.IOException {
        if (response.body() == null) {
            return "";
        }
        okhttp3.ResponseBody rb = response.body();
        long len = rb.contentLength();
        if (len > MAX_RESPONSE_BYTES) {
            LOG.warn("Control API response too large: " + len + " bytes");
            return null;
        }
        String body = rb.string();
        if (body.length() > MAX_RESPONSE_BYTES) {
            LOG.warn("Control API response too large: " + body.length() + " chars");
            return null;
        }
        return body;
    }

    private void addAuthHeader(Request.Builder builder) {
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
     *
     * <p>Byte arrays are defensively copied on construction and on access
     * to prevent aliasing — callers cannot mutate the internal state.
     */
    public static final class InboundMessage {
        private final byte[] sourcePub;
        private final byte[] payload;
        private final long receivedMs;

        public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs) {
            this.sourcePub = sourcePub == null ? new byte[0] : sourcePub.clone();
            this.payload = payload == null ? new byte[0] : payload.clone();
            this.receivedMs = receivedMs;
        }

        public byte[] sourcePub() {
            return sourcePub.clone();
        }

        public byte[] payload() {
            return payload.clone();
        }

        public long receivedMs() {
            return receivedMs;
        }
    }
}
