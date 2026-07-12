/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * A single application payload received from a remote IceBridge peer,
 * after mesh envelope demux.
 */
public final class InboundMessage {

    private final byte[] sourcePub;
    private final byte[] payload;
    private final long receivedMs;
    private final int protocolId;

    public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs) {
        this(sourcePub, payload, receivedMs, com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH);
    }

    public InboundMessage(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
        this.sourcePub = sourcePub;
        this.payload = payload;
        this.receivedMs = receivedMs;
        this.protocolId = protocolId;
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

    public int protocolId() {
        return protocolId;
    }
}
