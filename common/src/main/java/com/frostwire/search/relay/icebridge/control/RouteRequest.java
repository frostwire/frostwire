/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;

/**
 * JSON body for the {@code POST /route} control endpoint.
 *
 * <p>Unlike {@link RegisterRequest}, this does not require a signature.
 * The endpoint is localhost-only and trusts the co-located FrostWire
 * process to provide accurate peer routing information.
 */
public final class RouteRequest {

    /** 32-byte Ed25519 public key, base64url-no-padding encoded. */
    public String pub;

    /** Hostname or IP address where this peer accepts rUDP. */
    public String host;

    /** rUDP port. */
    public int rudpPort;

    /** Role advertised by the peer: FORWARDER, CLIENT, or BOTH. */
    public IceBridgeConfig.Role role;
}
