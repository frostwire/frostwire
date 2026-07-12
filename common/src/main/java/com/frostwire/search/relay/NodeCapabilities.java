/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Bitflags advertised by a node (IdentityRecord / peer registry).
 *
 * <p>Replaces fixed CLIENT/FORWARDER thinking with dynamic capability
 * advertisement. Roles remain a convenience view of these flags for
 * DHT announce topics.
 */
public final class NodeCapabilities {

    public static final long NONE = 0L;
    public static final long RELAY = 1L << 0;
    public static final long SEARCH = 1L << 1;
    public static final long INDEX = 1L << 2;
    public static final long STORE = 1L << 3;
    public static final long DHT = 1L << 4;
    public static final long TORRENT = 1L << 5;
    public static final long AI = 1L << 6;

    /** Typical FrostWire peer that can answer search and seed torrents. */
    public static final long DEFAULT_PEER = SEARCH | INDEX | TORRENT | DHT;

    /** Pure mesh forwarder (cloud IceBridge FORWARDER). */
    public static final long DEFAULT_FORWARDER = RELAY | DHT;

    /** BOTH: forward + participate in search/index. */
    public static final long DEFAULT_BOTH = RELAY | SEARCH | INDEX | TORRENT | DHT;

    private NodeCapabilities() {
    }

    public static boolean has(long caps, long flag) {
        return (caps & flag) == flag;
    }

    public static long with(long caps, long flag) {
        return caps | flag;
    }

    public static long without(long caps, long flag) {
        return caps & ~flag;
    }

    /**
     * Derive capabilities from the role string used by IceBridge
     * config and IdentityRecord v2.
     */
    public static long fromRole(String role) {
        if (role == null || role.isEmpty()) {
            return DEFAULT_BOTH;
        }
        switch (role.toUpperCase()) {
            case "FORWARDER":
                return DEFAULT_FORWARDER;
            case "CLIENT":
                return DEFAULT_PEER;
            case "BOTH":
            default:
                return DEFAULT_BOTH;
        }
    }

    /**
     * Best-effort role label from capabilities (for DHT / registry fields
     * that still expect CLIENT|FORWARDER|BOTH).
     */
    public static String toRole(long caps) {
        boolean relay = has(caps, RELAY);
        boolean app = has(caps, SEARCH) || has(caps, INDEX) || has(caps, TORRENT);
        if (relay && app) {
            return "BOTH";
        }
        if (relay) {
            return "FORWARDER";
        }
        return "CLIENT";
    }
}
