/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/** Data of a {@code POST /probe} answer. {@code state}: {@code none}, {@code pending}, {@code established}. */
public final class ProbeResponse {

    public String state;

    /** Authenticated Ed25519 key, base64url without padding. Only present when established. */
    public String pub;

    public ProbeResponse() {
    }

    public ProbeResponse(String state, String pub) {
        this.state = state;
        this.pub = pub;
    }
}
