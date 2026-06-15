/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.net.InetSocketAddress;

/**
 * Outbound packet awaiting acknowledgement.
 */
final class PendingPacket {

    final RudpPacket packet;
    final InetSocketAddress recipient;
    volatile long firstSentMs;
    volatile long lastSentMs;
    volatile int retries;

    PendingPacket(RudpPacket packet, InetSocketAddress recipient, long nowMs) {
        this.packet = packet;
        this.recipient = recipient;
        this.firstSentMs = nowMs;
        this.lastSentMs = nowMs;
    }
}