/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.IceBridgeAuth;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Netty HTTP handler for the local IceBridge control API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /register} — register or refresh a peer identity and endpoint (signed).</li>
 *   <li>{@code POST /route} — add a peer to the registry without a signature (localhost-only trust).</li>
 *   <li>{@code GET /lookup?count=N} — return up to N forward-capable peers.</li>
 *   <li>{@code POST /send} — send an opaque payload to a target peer.</li>
 *   <li>{@code GET /poll?count=N} — retrieve received payloads queued for the local process.</li>
 *   <li>{@code GET /metrics} — return in-memory counters and registry size.</li>
 *   <li>{@code GET /health} — liveness check.</li>
 * </ul>
 */
public final class ControlHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger LOG = Logger.getLogger(ControlHandler.class);
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_LOOKUP_COUNT = 10;
    private static final int DEFAULT_POLL_COUNT = 64;

    private final PeerRegistry registry;
    private final IceBridgeMetrics metrics;
    private final IceBridgeConfig config;
    private final RudpSessionManager rudpSessionManager;
    private final InboundMessageQueue inboundQueue;
    private final IceBridgeTokens authTokens;

    public ControlHandler(PeerRegistry registry,
                          IceBridgeMetrics metrics,
                          IceBridgeConfig config,
                          RudpSessionManager rudpSessionManager,
                          InboundMessageQueue inboundQueue,
                          IceBridgeTokens authTokens) {
        this.registry = registry;
        this.metrics = metrics;
        this.config = config;
        this.rudpSessionManager = rudpSessionManager;
        this.inboundQueue = inboundQueue;
        this.authTokens = (authTokens != null) ? authTokens : new IceBridgeTokens(config.authTokensFile());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        metrics.controlRequest();

        // SEC5: Require a valid bearer token (supports multiple) on all endpoints except /health.
        String path = new QueryStringDecoder(request.uri()).path();
        if (!"/health".equals(path)) {
            String token = request.headers().get("X-IceBridge-Token");
            if (authTokens == null || !authTokens.isValid(token)) {
                sendJson(ctx, request, HttpResponseStatus.UNAUTHORIZED,
                        ApiResponse.error("unauthorized"));
                return;
            }
        }

        String uri = request.uri();
        HttpMethod method = request.method();

        try {
            ApiResponse<?> response;
            if (method == HttpMethod.POST && "/register".equals(path)) {
                response = handleRegister(request);
            } else if (method == HttpMethod.POST && "/route".equals(path)) {
                response = handleRoute(request);
            } else if (method == HttpMethod.GET && "/lookup".equals(path)) {
                response = handleLookup(uri);
            } else if (method == HttpMethod.POST && "/send".equals(path)) {
                response = handleSend(request);
            } else if (method == HttpMethod.GET && "/poll".equals(path)) {
                response = handlePoll(uri);
            } else if (method == HttpMethod.GET && "/metrics".equals(path)) {
                response = handleMetrics();
            } else if (method == HttpMethod.GET && "/health".equals(path)) {
                response = ApiResponse.success("ok");
            } else {
                response = ApiResponse.error("unknown endpoint: " + method + " " + path);
            }
            sendJson(ctx, request, response.ok ? HttpResponseStatus.OK : HttpResponseStatus.BAD_REQUEST, response);
        } catch (Throwable t) {
            LOG.warn("Control handler error", t);
            metrics.controlError();
            sendJson(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("internal error"));
        }
    }

    private ApiResponse<String> handleRegister(FullHttpRequest request) {
        ByteBuf content = request.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        RegisterRequest req = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), RegisterRequest.class);
        if (req == null) {
            return ApiResponse.error("missing request body");
        }
        if (req.pub == null || req.signature == null) {
            return ApiResponse.error("pub and signature are required");
        }

        byte[] rawPub;
        byte[] signature;
        try {
            rawPub = IceBridgeAuth.decodeBase64(req.pub);
            signature = IceBridgeAuth.decodeBase64(req.signature);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("invalid base64: " + e.getMessage());
        }
        if (rawPub.length != 32) {
            return ApiResponse.error("pub must be 32 bytes");
        }
        if (signature.length != 64) {
            return ApiResponse.error("signature must be 64 bytes");
        }

        boolean verified = IceBridgeAuth.verify(rawPub, canonicalBytes(req), signature);
        if (!verified) {
            return ApiResponse.error("invalid signature");
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        long diff = nowSec - req.timestamp;
        long tsSkew = diff >= 0 ? diff : -diff;
        if (tsSkew > 60) {
            return ApiResponse.error("timestamp skew too large");
        }

        PeerRecord record = new PeerRecord(rawPub, req.host, req.rudpPort,
                req.role == null ? IceBridgeConfig.Role.CLIENT : req.role,
                System.currentTimeMillis());
        boolean accepted = registry.register(record);
        return accepted
                ? ApiResponse.success("registered")
                : ApiResponse.error("rate limited or at capacity");
    }

    private byte[] canonicalBytes(RegisterRequest req) {
        return req.canonicalString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Localhost-trusted endpoint that adds a peer to the registry without
     * requiring a signature. Only the co-located FrostWire process can
     * call this (the control server binds to 127.0.0.1).
     */
    private ApiResponse<String> handleRoute(FullHttpRequest request) {
        RouteRequest req = decodeBody(request, RouteRequest.class);
        if (req == null || req.pub == null || req.host == null) {
            return ApiResponse.error("pub and host are required");
        }
        byte[] rawPub;
        try {
            rawPub = IceBridgeAuth.decodeBase64(req.pub);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("invalid base64: " + e.getMessage());
        }
        if (rawPub.length != 32) {
            return ApiResponse.error("pub must be 32 bytes");
        }
        if (req.rudpPort <= 0 || req.rudpPort > 65535) {
            return ApiResponse.error("rudpPort must be in [1, 65535]");
        }
        PeerRecord record = new PeerRecord(rawPub, req.host, req.rudpPort,
                req.role == null ? IceBridgeConfig.Role.BOTH : req.role,
                System.currentTimeMillis());
        boolean accepted = registry.register(record);
        return accepted
                ? ApiResponse.success("routed")
                : ApiResponse.error("rate limited or at capacity");
    }

    private ApiResponse<List<PeerInfo>> handleLookup(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        int count = DEFAULT_LOOKUP_COUNT;
        List<String> countParams = decoder.parameters().get("count");
        if (countParams != null && !countParams.isEmpty()) {
            try {
                count = Integer.parseInt(countParams.get(0));
            } catch (NumberFormatException ignored) {
            }
        }
        List<PeerRecord> peers = registry.lookupForwarders(Math.max(1, Math.min(count, 100)));
        List<PeerInfo> info = peers.stream()
                .map(p -> new PeerInfo(
                        Base64.getUrlEncoder().withoutPadding().encodeToString(p.ed25519Pub()),
                        p.host(),
                        p.rudpPort(),
                        p.role(),
                        p.lastSeenMs()))
                .collect(Collectors.toList());
        return ApiResponse.success(info);
    }

    private ApiResponse<String> handleSend(FullHttpRequest request) {
        if (rudpSessionManager == null) {
            return ApiResponse.error("rUDP stack not available");
        }
        SendRequest req = decodeBody(request, SendRequest.class);
        if (req == null || req.targetPub == null || req.payload == null) {
            return ApiResponse.error("targetPub and payload are required");
        }
        byte[] targetPub;
        byte[] payload;
        try {
            targetPub = IceBridgeAuth.decodeBase64(req.targetPub);
            payload = IceBridgeAuth.decodeBase64(req.payload);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("invalid base64: " + e.getMessage());
        }
        if (targetPub.length != 32) {
            return ApiResponse.error("targetPub must be 32 bytes");
        }
        if (payload.length == 0) {
            return ApiResponse.error("payload must not be empty");
        }
        rudpSessionManager.deliver(targetPub, payload);
        return ApiResponse.success("queued");
    }

    private ApiResponse<List<InboundMessageInfo>> handlePoll(String uri) {
        if (inboundQueue == null) {
            return ApiResponse.error("inbound queue not available");
        }
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        int count = DEFAULT_POLL_COUNT;
        List<String> countParams = decoder.parameters().get("count");
        if (countParams != null && !countParams.isEmpty()) {
            try {
                count = Integer.parseInt(countParams.get(0));
            } catch (NumberFormatException ignored) {
            }
        }
        List<InboundMessageInfo> result = inboundQueue.poll(Math.max(1, Math.min(count, 256))).stream()
                .map(m -> new InboundMessageInfo(
                        Base64.getUrlEncoder().withoutPadding().encodeToString(m.sourcePub()),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(m.payload()),
                        m.receivedMs()))
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    private <T> T decodeBody(FullHttpRequest request, Class<T> type) {
        ByteBuf content = request.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    private ApiResponse<MetricsSnapshot> handleMetrics() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                metrics.rudpPacketsIn(), metrics.rudpPacketsOut(),
                metrics.rudpBytesIn(), metrics.rudpBytesOut(),
                metrics.controlRequests(), metrics.controlErrors(),
                registry.size(), registry.registrations(), registry.lookups(), registry.evicted());
        return ApiResponse.success(snapshot);
    }

    private void sendJson(ChannelHandlerContext ctx, FullHttpRequest request,
                          HttpResponseStatus status, Object body) {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                request.protocolVersion(), status,
                Unpooled.wrappedBuffer(bytes));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (!keepAlive) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            ctx.writeAndFlush(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.warn("Control channel exception", cause);
        metrics.controlError();
        ctx.close();
    }

    private static final class MetricsSnapshot {
        @SuppressWarnings("unused")
        final long rudpPacketsIn;
        @SuppressWarnings("unused")
        final long rudpPacketsOut;
        @SuppressWarnings("unused")
        final long rudpBytesIn;
        @SuppressWarnings("unused")
        final long rudpBytesOut;
        @SuppressWarnings("unused")
        final long controlRequests;
        @SuppressWarnings("unused")
        final long controlErrors;
        @SuppressWarnings("unused")
        final int registrySize;
        @SuppressWarnings("unused")
        final long registrations;
        @SuppressWarnings("unused")
        final long lookups;
        @SuppressWarnings("unused")
        final long evicted;

        MetricsSnapshot(long rudpPacketsIn, long rudpPacketsOut,
                        long rudpBytesIn, long rudpBytesOut,
                        long controlRequests, long controlErrors,
                        int registrySize, long registrations,
                        long lookups, long evicted) {
            this.rudpPacketsIn = rudpPacketsIn;
            this.rudpPacketsOut = rudpPacketsOut;
            this.rudpBytesIn = rudpBytesIn;
            this.rudpBytesOut = rudpBytesOut;
            this.controlRequests = controlRequests;
            this.controlErrors = controlErrors;
            this.registrySize = registrySize;
            this.registrations = registrations;
            this.lookups = lookups;
            this.evicted = evicted;
        }
    }
}