/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * Body of {@code POST /probe}: identity discovery for an {@code ip:udpPort} that was found on the
 * DHT, the LAN or a seed list. Like {@link RouteRequest} it is localhost-only and needs no
 * signature; the daemon authenticates the endpoint with the rUDP handshake.
 */
public final class ProbeRequest {

    /** Literal IPv4/IPv6 address. Names are not resolved by the daemon. */
    public String host;

    /** rUDP port of the endpoint. */
    public int port;

    /** Drop a handshake that never completed instead of polling it. */
    public boolean cancel;
}
