/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Collections;
import java.util.List;

/**
 * Supplies the BT listen endpoints a search responder advertises inside
 * signed {@link RemoteSearchResponse} rows ({@code bt} field, v3+), so the
 * requester can fetch the torrent directly from the seeder instead of
 * relying on trackers/DHT that may never have heard of the infohash.
 */
public interface SeederEndpointProvider {

    /** No endpoints advertised (default; pre-v3 behavior). */
    SeederEndpointProvider NONE = Collections::emptyList;

    /**
     * {@code host:port} endpoints where this node's BT engine accepts
     * connections — LAN addresses plus the external address when known.
     * Empty when the BT engine is not up or nothing is worth advertising.
     */
    List<String> seederEndpoints();
}
