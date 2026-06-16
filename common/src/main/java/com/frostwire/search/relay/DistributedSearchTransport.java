/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.List;

/**
 * Transport-agnostic interface for sending and receiving opaque payloads
 * between FrostWire peers.
 *
 * <p>The DISTRIBUTED search engine uses this abstraction instead of a concrete
 * TCP or IceBridge client. Implementations run an internal poller thread that
 * feeds registered listeners; callers never poll directly.
 *
 * <h2>Listener model</h2>
 * <ul>
 *   <li>{@link #addListener} registers a listener that receives <em>every</em>
 *       inbound payload. Listeners are responsible for filtering by content
 *       (e.g. decoding as a {@link RemoteSearchResponse} and matching nonce).</li>
 *   <li>The {@link IncomingSearchRequestHandler} (desktop) registers a permanent
 *       listener that processes incoming search requests.</li>
 *   <li>The {@link DistributedSearchPerformer} registers a temporary listener
 *       for the duration of a single search, then removes it.</li>
 * </ul>
 *
 * <p>This design keeps the transport purpose-agnostic: it does not understand
 * search protocol messages, nonces, or signatures. Routing logic lives in the
 * listeners.
 */
public interface DistributedSearchTransport {

    /**
     * Send an opaque payload to a peer identified by raw Ed25519 public key.
     *
     * @param targetPub 32-byte Ed25519 public key of the recipient
     * @param payload   opaque application payload
     * @return {@code true} if the payload was accepted for delivery
     */
    boolean send(byte[] targetPub, byte[] payload);

    /**
     * Register a listener that receives every inbound payload on the
     * transport's internal poller thread.
     */
    void addListener(PayloadListener listener);

    /**
     * Remove a previously registered listener.
     */
    void removeListener(PayloadListener listener);

    /**
     * Functional callback for inbound payloads.
     */
    @FunctionalInterface
    interface PayloadListener {
        /**
         * @param sourcePub   raw Ed25519 public key of the sender
         * @param payload     opaque application payload
         * @param receivedMs  epoch millis when the transport received the payload
         */
        void onPayload(byte[] sourcePub, byte[] payload, long receivedMs);
    }
}
