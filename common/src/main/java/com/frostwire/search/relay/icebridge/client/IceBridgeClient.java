/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeConstants;
import com.frostwire.search.relay.icebridge.control.ApiResponse;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import com.frostwire.search.relay.icebridge.control.RegisterRequest;
import com.frostwire.search.relay.icebridge.control.RouteRequest;
import com.frostwire.search.relay.icebridge.control.SendRequest;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.HttpUrl;
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
    /** Own Ed25519 pub for multi-client /poll demux (set on successful register). */
    private volatile byte[] ownPub;
    private volatile boolean identityPolling;
    private volatile boolean closed;

    public IceBridgeClient(int controlPort) {
        this("http://127.0.0.1:" + controlPort);
    }

    public IceBridgeClient(String baseUrl) {
        HttpUrl url = HttpUrl.get(baseUrl);
        String host = url.host();
        boolean loopback = "localhost".equals(host) || "::1".equals(host)
                || host.matches("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
        if (!url.isHttps() && !loopback || !url.username().isEmpty() || !url.password().isEmpty()
                || url.query() != null || url.fragment() != null) {
            throw new IllegalArgumentException("control URL requires TLS outside loopback and no URL credentials/query");
        }
        String normalized = url.toString();
        this.baseUrl = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
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
     * Optional own public key so {@link #poll(int)} can request a per-client
     * demux queue on multi USE_REMOTE forwarders ({@code /poll?pub=}).
     * Subscribes synchronously before changing poll mode; call off the UI thread.
     */
    public synchronized void setOwnPub(byte[] ownPub) {
        if (closed) {
            return;
        }
        if (ownPub != null && ownPub.length != 32) {
            throw new IllegalArgumentException("ownPub must be 32 bytes");
        }
        byte[] next = ownPub == null ? null : ownPub.clone();
        if (next != null) {
            identityPolling = true;
        }
        if (Arrays.equals(this.ownPub, next)) {
            if (next != null && !consumer(next, true)) {
                this.ownPub = null;
            }
            return;
        }
        if (this.ownPub != null && !consumer(this.ownPub, false)) {
            return; // Accepted messages must be drained before changing queue ownership.
        }
        this.ownPub = next != null && consumer(next, true) ? next : null;
        identityPolling = next != null;
    }

    private boolean consumer(byte[] pub, boolean enabled) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("pub", Base64.getUrlEncoder().withoutPadding().encodeToString(pub));
        request.put("enabled", enabled);
        ApiResponse<?> response = post("/consumer", request, new TypeToken<ApiResponse<?>>() {});
        return response != null && response.ok;
    }

    /**
     * Check that the daemon is alive and responding.
     */
    public boolean health() {
        return health(TimeUnit.SECONDS.toMillis(CALL_TIMEOUT_SEC));
    }

    /** Synchronous health probe bounded by the caller's remaining budget in milliseconds. */
    public boolean health(long timeoutMs) {
        if (timeoutMs <= 0) {
            return false;
        }
        try {
            ApiResponse<?> response = get("/health", new TypeToken<ApiResponse<?>>() {
            }, timeoutMs);
            return response != null && response.ok;
        } catch (Exception e) {
            LOG.warn("IceBridgeClient.health failed for " + baseUrl + ": " + e.getMessage());
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
    public synchronized boolean register(IdentityKeys identity, String host, int rudpPort,
                            IceBridgeConfig.Role role) {
        if (closed || identity == null || host == null || host.isEmpty() || rudpPort <= 0 || role == null) {
            return false;
        }
        // Identity registration is an explicit mode choice, even if the daemon is unavailable.
        identityPolling = true;
        String pubB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.ed25519PubRaw());
        long timestamp = System.currentTimeMillis() / 1000;

        RegisterRequest req = new RegisterRequest();
        req.pub = pubB64;
        req.host = host;
        req.rudpPort = rudpPort;
        req.role = role;
        req.timestamp = timestamp;
        req.icebridgeVersion = IceBridgeConstants.SOFTWARE_VERSION;

        try {
            Signature signer = IdentityKeys.softwareSignature("Ed25519");
            signer.initSign(identity.ed25519().getPrivate());
            signer.update(req.canonicalString().getBytes(StandardCharsets.UTF_8));
            req.signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (Exception e) {
            LOG.warn("IceBridgeClient.register: Ed25519 sign failed", e);
            return false;
        }

        ApiResponse<?> response = post("/register", req, new TypeToken<ApiResponse<?>>() {
        });
        boolean ok = response != null && response.ok;
        if (ok) {
            setOwnPub(identity.ed25519PubRaw());
            ok = Arrays.equals(ownPub, identity.ed25519PubRaw());
        }
        return ok;
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
     * Send an opaque payload to a target peer (protocol SEARCH).
     */
    public boolean send(byte[] targetPub, byte[] payload) {
        return send(targetPub, com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, payload);
    }

    /**
     * Send an opaque application payload under the given mesh protocol id.
     *
     * @param protocolId see {@link com.frostwire.search.relay.icebridge.MeshProtocolId}
     */
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
        if (targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            return false;
        }
        DistributedSearchTransport.SendOperation operation = createSend(targetPub, protocolId, payload,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(CALL_TIMEOUT_SEC));
        try {
            return operation.execute();
        } finally {
            operation.cancel();
        }
    }

    /** Owns one cancellable HTTP send; deadline is absolute System.nanoTime(), including queue time. */
    public DistributedSearchTransport.SendOperation createSend(byte[] targetPub, int protocolId,
                                                               byte[] payload, long deadlineNanos) {
        if (targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            throw new IllegalArgumentException("invalid send payload or target");
        }
        SendRequest req = new SendRequest();
        req.targetPub = Base64.getUrlEncoder().withoutPadding().encodeToString(targetPub);
        req.payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        req.protocolId = protocolId;

        Request.Builder builder = new Request.Builder().url(baseUrl + "/send")
                .post(RequestBody.create(GSON.toJson(req), JSON));
        addAuthHeader(builder);
        Call call = http.newCall(builder.build());
        call.timeout().deadlineNanoTime(deadlineNanos);
        return new DistributedSearchTransport.SendOperation() {
            @Override
            public boolean execute() {
                if (closed || call.isCanceled() || Thread.currentThread().isInterrupted()
                        || System.nanoTime() >= deadlineNanos) {
                    call.cancel();
                    return false;
                }
                try (Response response = call.execute()) {
                    String body = readBody(response);
                    ApiResponse<?> decoded = body == null ? null : GSON.fromJson(body, ApiResponse.class);
                    return !call.isCanceled() && decoded != null && decoded.ok;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void cancel() {
                call.cancel();
            }
        };
    }

    /**
     * Poll the local IceBridge for payloads addressed to us.
     */
    public List<InboundMessage> poll(int count) {
        byte[] pub;
        synchronized (this) {
            if (closed || count <= 0 || identityPolling && ownPub == null) {
                return Collections.emptyList();
            }
            pub = ownPub;
        }
        String path = "/poll?count=" + Math.min(count, 256);
        if (pub != null && pub.length == 32) {
            path += "&pub=" + Base64.getUrlEncoder().withoutPadding().encodeToString(pub);
        }
        ApiResponse<List<com.frostwire.search.relay.icebridge.control.InboundMessageInfo>> response =
                get(path,
                        new TypeToken<ApiResponse<List<com.frostwire.search.relay.icebridge.control.InboundMessageInfo>>>() {
                        });
        if (response == null || response.data == null || response.data.size() > Math.min(count, 256)) {
            return Collections.emptyList();
        }
        List<InboundMessage> out = new ArrayList<>(response.data.size());
        try {
            for (com.frostwire.search.relay.icebridge.control.InboundMessageInfo info : response.data) {
                byte[] source = decode(info.sourcePub);
                if (source.length != 0 && source.length != 32) {
                    return Collections.emptyList();
                }
                out.add(new InboundMessage(source, decode(info.payload), info.receivedMs, info.protocolId));
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            return Collections.emptyList();
        }
        return out;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        http.dispatcher().cancelAll();
        if (ownPub != null) {
            consumer(ownPub, false);
            ownPub = null;
        }
        closed = true;
        http.dispatcher().cancelAll();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private <T> T get(String path, TypeToken<T> type) {
        return get(path, type, TimeUnit.SECONDS.toMillis(CALL_TIMEOUT_SEC));
    }

    private <T> T get(String path, TypeToken<T> type, long timeoutMs) {
        if (closed) {
            return null;
        }
        try {
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + path)
                    .get();
            addAuthHeader(builder);
            Call call = http.newCall(builder.build());
            call.timeout().timeout(Math.max(1, Math.min(timeoutMs, 10_000)), TimeUnit.MILLISECONDS);
            try (Response response = call.execute()) {
                String body = readBody(response);
                if (body == null) {
                    return null;
                }
                return GSON.fromJson(body, type.getType());
            }
        } catch (Exception e) {
            LOG.warn("IceBridgeClient GET " + path + " failed: " + e);
            return null;
        }
    }

    private <T> T post(String path, Object body, TypeToken<T> type) {
        if (closed) {
            return null;
        }
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
            LOG.warn("IceBridgeClient POST " + path + " failed: " + e);
            return null;
        }
    }

    private static String readBody(Response response) throws java.io.IOException {
        if (!response.isSuccessful()) {
            return null;
        }
        if (response.body() == null) {
            return "";
        }
        okhttp3.ResponseBody rb = response.body();
        long len = rb.contentLength();
        if (len > MAX_RESPONSE_BYTES) {
            LOG.warn("Control API response too large: " + len + " bytes");
            return null;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream input = rb.byteStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer, 0, Math.min(buffer.length,
                    MAX_RESPONSE_BYTES - body.size() + 1))) != -1) {
                if (read > MAX_RESPONSE_BYTES - body.size()) {
                    return null;
                }
                body.write(buffer, 0, read);
            }
        }
        return new String(body.toByteArray(), StandardCharsets.UTF_8);
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
        private final int protocolId;

        public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs) {
            this(sourcePub, payload, receivedMs,
                    com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH);
        }

        public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
            this.sourcePub = sourcePub == null ? new byte[0] : sourcePub.clone();
            this.payload = payload == null ? new byte[0] : payload.clone();
            this.receivedMs = receivedMs;
            this.protocolId = protocolId;
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

        public int protocolId() {
            return protocolId;
        }
    }
}
