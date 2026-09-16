/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.google.gson.JsonElement;

/**
 * Hook used by the IceBridge control API to fetch the full, holder-signed
 * {@code .torrent} bytes for a known info hash (Protocol #3 TORRENT_FETCH).
 *
 * <p>This is the NAT-proof path used by a thin external tool: the relay asks
 * the holder over the mesh, verifies the holder's chunk signatures, and
 * returns the reassembled metadata. The concrete implementation is owned by
 * the relay application layer
 * ({@code com.frostwire.search.relay.SearchRelayApp}) because only that layer
 * holds a started {@code DistributedSearchTransport}. The relay installs it on
 * the {@link ControlServer} (and therefore on every per-connection
 * {@link ControlHandler}) via {@link ControlServer#setTorrentFetcher}.
 *
 * <p><b>Thread-safety:</b> implementations must be safe to call from multiple
 * HTTP worker threads concurrently. The control server creates one handler per
 * connection and dispatches on a shared worker pool, so {@link #fetch} can be
 * entered in parallel.
 *
 * <p><b>Failure contract:</b> implementations must never throw. On success they
 * return a JSON object of the shape {@code {"data_b64":"<base64 .torrent>"}}.
 * Any failure (invalid input, transport error, timeout, verification failure)
 * must be reported by returning a JSON object of the shape
 * {@code {"error":"..."}} — never as an exception.
 */
public interface TorrentFetcher {

    /**
     * Fetch the full {@code .torrent} for {@code infoHashHex} from the holder
     * identified by {@code holderPubBase64Url}.
     *
     * @param infoHashHex        40-character lowercase/uppercase hex encoding
     *                           of the 20-byte v1 info hash
     * @param holderPubBase64Url base64url (no padding) encoding of the holder's
     *                           raw 32-byte Ed25519 public key
     * @param timeoutMs          total wall-clock budget in milliseconds
     * @return a JSON object ({@code {"data_b64":"..."}} on success, or
     *         {@code {"error":"..."}} on failure). Never throws.
     */
    JsonElement fetch(String infoHashHex, String holderPubBase64Url, int timeoutMs);
}
