/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Process-wide accessor for the relay wiring needed to send mesh requests
 * (e.g. TORRENT_FETCH metadata) from download paths that have no direct
 * reference to the transport — set once by the relay stack bootstrap
 * (desktop Initializer), read on transfer threads.
 *
 * <p>Fields are volatile for cross-thread visibility; all methods are
 * null-safe so download paths degrade to their direct fallback when the
 * relay stack is not running.
 */
public final class MeshRequestContext {

    private static volatile DistributedSearchTransport transport;
    private static volatile IdentityKeys identity;

    private MeshRequestContext() {
    }

    public static void init(DistributedSearchTransport searchTransport, IdentityKeys keys) {
        transport = searchTransport;
        identity = keys;
    }

    public static DistributedSearchTransport transport() {
        return transport;
    }

    public static IdentityKeys identity() {
        return identity;
    }

    public static boolean isReady() {
        return transport != null && identity != null;
    }
}