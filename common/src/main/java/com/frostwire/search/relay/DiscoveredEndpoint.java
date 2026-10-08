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
    /**
     * True for endpoints that are worth trying before the crowd: the dedicated bootstrap/forwarder
     * servers, hosts we already verified, built-in seeds and peers on our own LAN. A discovery pass
     * probes these first and retries them sooner after a failure.
     */
    public final boolean preferred;

    public DiscoveredEndpoint(String host, int port) {
        this(host, port, false);
    }

    public DiscoveredEndpoint(String host, int port, boolean preferred) {
        this.host = host;
        this.port = port;
        this.preferred = preferred;
    }

    @Override
    public String toString() {
        return "DiscoveredEndpoint{" + host + ":" + port + (preferred ? " preferred" : "") + "}";
    }
}
