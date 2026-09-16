/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.google.gson.JsonElement;

/**
 * Hook used by the IceBridge control API to fetch a peer's full
 * shared-torrent catalog over the mesh on behalf of an external crawler.
 *
 * <p>The concrete implementation is owned by the relay application layer
 * ({@code com.frostwire.search.relay.SearchRelayApp}) because only that layer
 * holds a started {@code DistributedSearchTransport}. The relay installs it
 * on the {@link ControlServer} (and therefore on every per-connection
 * {@link ControlHandler}) via {@link ControlServer#setCatalogFetcher}.
 *
 * <p><b>Thread-safety:</b> implementations must be safe to call from multiple
 * HTTP worker threads concurrently. The control server creates one handler
 * per connection and dispatches on a shared worker pool, so {@link #fetch}
 * can be entered in parallel.
 *
 * <p><b>Failure contract:</b> implementations must never throw. Any failure
 * (invalid input, transport error, timeout) must be reported by returning
 * {@code null} or a JSON element describing the error (for example
 * {@code {"error":"..."}}), never as an exception.
 */
public interface CatalogFetcher {

    /**
     * Fetch the catalog of the peer identified by {@code pubBase64Url}.
     *
     * @param pubBase64Url base64url (no padding) encoding of a raw 32-byte
     *                     Ed25519 public key
     * @param timeoutMs    total wall-clock budget in milliseconds
     * @return a JSON element ({@code JsonArray} of catalog rows, or a
     *         {@code JsonObject} error), or {@code null} when the catalog
     *         could not be produced. Never throws.
     */
    JsonElement fetch(String pubBase64Url, int timeoutMs);
}
