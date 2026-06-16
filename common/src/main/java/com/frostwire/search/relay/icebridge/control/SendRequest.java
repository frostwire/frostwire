/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * JSON body for the {@code POST /send} control endpoint.
 */
public final class SendRequest {

    public String targetPub;
    public String payload;
}
