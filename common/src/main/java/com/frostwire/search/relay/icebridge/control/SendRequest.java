/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * JSON body for the {@code POST /send} control endpoint.
 *
 * <p>{@code protocolId} is optional (default {@code 0} = unspecified → SEARCH).
 * IceBridge frames the payload with {@link com.frostwire.search.relay.icebridge.MeshEnvelope}
 * before mesh delivery.
 */
public final class SendRequest {

    public String targetPub;
    public String payload;
    /** Optional mesh protocol id; see {@link com.frostwire.search.relay.icebridge.MeshProtocolId}. */
    public Integer protocolId;
}
