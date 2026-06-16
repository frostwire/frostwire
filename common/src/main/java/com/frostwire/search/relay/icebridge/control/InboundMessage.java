/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * A single payload received from a remote IceBridge peer.
 */
public final class InboundMessage {

    private final byte[] sourcePub;
    private final byte[] payload;
    private final long receivedMs;

    public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs) {
        this.sourcePub = sourcePub;
        this.payload = payload;
        this.receivedMs = receivedMs;
    }

    public byte[] sourcePub() {
        return sourcePub;
    }

    public byte[] payload() {
        return payload;
    }

    public long receivedMs() {
        return receivedMs;
    }
}
