/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;

/**
 * JSON representation of a peer returned by the IceBridge control API.
 */
public final class PeerInfo {

    public String pub;
    public String host;
    public int rudpPort;
    public IceBridgeConfig.Role role;
    public long lastSeenMs;
    /** IceBridge software version when advertised; may be null/empty. */
    public String icebridgeVersion;

    public PeerInfo(String pub, String host, int rudpPort, IceBridgeConfig.Role role, long lastSeenMs) {
        this(pub, host, rudpPort, role, lastSeenMs, null);
    }

    public PeerInfo(String pub, String host, int rudpPort, IceBridgeConfig.Role role, long lastSeenMs,
                    String icebridgeVersion) {
        this.pub = pub;
        this.host = host;
        this.rudpPort = rudpPort;
        this.role = role;
        this.lastSeenMs = lastSeenMs;
        this.icebridgeVersion = icebridgeVersion;
    }
}