/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * JSON representation of an {@link InboundMessage} returned by
 * {@code GET /poll}.
 */
public final class InboundMessageInfo {

    @SuppressWarnings("unused")
    public final String sourcePub;
    @SuppressWarnings("unused")
    public final String payload;
    @SuppressWarnings("unused")
    public final long receivedMs;

    public InboundMessageInfo(String sourcePub, String payload, long receivedMs) {
        this.sourcePub = sourcePub;
        this.payload = payload;
        this.receivedMs = receivedMs;
    }
}
