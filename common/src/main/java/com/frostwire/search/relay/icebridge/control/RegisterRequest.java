/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;

/**
 * JSON request body for registering an IceBridge peer with the local registry.
 */
public final class RegisterRequest {

    /** 32-byte Ed25519 public key, base64url-no-padding encoded. */
    public String pub;

    /** Hostname or IP address where this peer accepts rUDP. */
    public String host;

    /** rUDP port. */
    public int rudpPort;

    /** Role advertised by the peer: FORWARDER, CLIENT, or BOTH. */
    public IceBridgeConfig.Role role;

    /** Unix timestamp (seconds) when the request was formed. */
    public long timestamp;

    /** Ed25519 signature of the canonical registration string, base64url-no-padding. */
    public String signature;

    public String canonicalString() {
        return pub + "|" + host + "|" + rudpPort + "|" + role + "|" + timestamp;
    }
}