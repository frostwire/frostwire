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

    /**
     * Optional IceBridge software version (e.g. {@code 1.1.0}).
     * When non-empty it is included in the signed canonical string so
     * peers cannot spoof versions of other identities.
     */
    public String icebridgeVersion;

    /** Ed25519 signature of the canonical registration string, base64url-no-padding. */
    public String signature;

    /**
     * Canonical string for signature verification.
     *
     * <p>Uses length-prefixed fields to prevent delimiter injection —
     * a host containing {@code |} cannot produce the same canonical
     * string as a different (pub, host, port, role, timestamp) tuple.
     *
     * <p>When {@link #icebridgeVersion} is non-blank, it is appended as a
     * final length-prefixed field. Pre-1.1.0 clients omit it and sign the
     * legacy 5-field form — both verify correctly on the server.
     */
    public String canonicalString() {
        String base = pub.length() + ":" + pub + "|"
                + host.length() + ":" + host + "|"
                + rudpPort + "|"
                + (role == null ? "null" : role.name()) + "|"
                + timestamp;
        if (icebridgeVersion != null && !icebridgeVersion.isBlank()) {
            String v = icebridgeVersion.trim();
            return base + "|" + v.length() + ":" + v;
        }
        return base;
    }
}