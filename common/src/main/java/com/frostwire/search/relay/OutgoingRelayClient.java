/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

/**
 * Plain-TCP client that sends a {@link RemoteSearchRequest} to a
 * peer and reads the {@link RemoteSearchResponse} reply.
 *
 * <p>Connection lifecycle: open, write request frame, read
 * response frame, close. No connection reuse. No retries. Callers
 * that want retries wrap this with their own loop.
 *
 * <p>Fail-closed: any network or protocol error returns empty.
 *
 * <p>uTP is a future optimization. For now, plain TCP is enough
 * to prove the protocol end-to-end.
 */
public final class OutgoingRelayClient {

    private static final Logger LOG = Logger.getLogger(OutgoingRelayClient.class);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_SO_TIMEOUT_MS = 10_000;

    private final int connectTimeoutMs;
    private final int soTimeoutMs;

    public OutgoingRelayClient() {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_SO_TIMEOUT_MS);
    }

    public OutgoingRelayClient(int connectTimeoutMs, int soTimeoutMs) {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectTimeoutMs must be > 0");
        }
        if (soTimeoutMs <= 0) {
            throw new IllegalArgumentException("soTimeoutMs must be > 0");
        }
        this.connectTimeoutMs = connectTimeoutMs;
        this.soTimeoutMs = soTimeoutMs;
    }

    /**
     * Send a request to {@code host:port} and return the response.
     * Returns empty on any error (connection refused, timeout,
     * bad protocol, etc.).
     */
    public Optional<RemoteSearchResponse> send(String host, int port,
                                                RemoteSearchRequest request) {
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }
        if (port <= 0 || port > 65535) {
            return Optional.empty();
        }
        if (request == null) {
            return Optional.empty();
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(soTimeoutMs);
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                RelayWireCodec.writeRequest(out, request);
                return Optional.ofNullable(RelayWireCodec.readResponse(in));
            }
        } catch (Throwable t) {
            LOG.debug("OutgoingRelayClient.send failed for " + host + ":" + port, t);
            return Optional.empty();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
