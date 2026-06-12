/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * A (host, port) pair representing a FrostWire peer discovered
 * via BEP 5 rendezvous. The placeholder pubkey derived from
 * this pair is what the local directory uses as the key until
 * the real pubkey is learned from a request signature.
 */
public final class DiscoveredEndpoint {
    public final String host;
    public final int port;

    public DiscoveredEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return "DiscoveredEndpoint{" + host + ":" + port + "}";
    }
}
