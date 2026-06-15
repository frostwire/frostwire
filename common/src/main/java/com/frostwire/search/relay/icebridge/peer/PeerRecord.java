/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable record of an authenticated peer known to an IceBridge servent.
 *
 * <p>Identity is the raw 32-byte Ed25519 public key. The record tracks the
 * peer's last known rUDP endpoint, advertised role, and freshness so stale
 * entries can be evicted without disk I/O.
 */
public final class PeerRecord {

    private final byte[] ed25519Pub;
    private final String host;
    private final int rudpPort;
    private final IceBridgeConfig.Role role;
    private final long lastSeenMs;

    public PeerRecord(byte[] ed25519Pub,
                      String host,
                      int rudpPort,
                      IceBridgeConfig.Role role,
                      long lastSeenMs) {
        if (ed25519Pub == null || ed25519Pub.length != 32) {
            throw new IllegalArgumentException("ed25519Pub must be 32 bytes");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (rudpPort <= 0 || rudpPort > 65535) {
            throw new IllegalArgumentException("rudpPort must be in [1, 65535]");
        }
        this.ed25519Pub = ed25519Pub.clone();
        this.host = host;
        this.rudpPort = rudpPort;
        this.role = Objects.requireNonNull(role, "role");
        this.lastSeenMs = lastSeenMs;
    }

    public byte[] ed25519Pub() {
        return ed25519Pub.clone();
    }

    public String ed25519PubHex() {
        return com.frostwire.util.Hex.encode(ed25519Pub);
    }

    public String host() {
        return host;
    }

    public int rudpPort() {
        return rudpPort;
    }

    public IceBridgeConfig.Role role() {
        return role;
    }

    public long lastSeenMs() {
        return lastSeenMs;
    }

    public boolean canForward() {
        return role == IceBridgeConfig.Role.FORWARDER || role == IceBridgeConfig.Role.BOTH;
    }

    public PeerRecord withLastSeen(long lastSeenMs) {
        return new PeerRecord(ed25519Pub, host, rudpPort, role, lastSeenMs);
    }

    public PeerRecord withEndpoint(String host, int rudpPort) {
        return new PeerRecord(ed25519Pub, host, rudpPort, role, lastSeenMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerRecord)) return false;
        PeerRecord that = (PeerRecord) o;
        return Arrays.equals(ed25519Pub, that.ed25519Pub);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ed25519Pub);
    }
}