/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.net.InetSocketAddress;

/**
 * A decoded {@link RudpPacket} together with the addresses from the UDP
 * datagram it arrived on.
 */
final class RudpPacketEnvelope {

    private final RudpPacket packet;
    private final InetSocketAddress sender;
    private final InetSocketAddress recipient;

    RudpPacketEnvelope(RudpPacket packet, InetSocketAddress sender, InetSocketAddress recipient) {
        this.packet = packet;
        this.sender = sender;
        this.recipient = recipient;
    }

    RudpPacket packet() {
        return packet;
    }

    InetSocketAddress sender() {
        return sender;
    }

    InetSocketAddress recipient() {
        return recipient;
    }
}