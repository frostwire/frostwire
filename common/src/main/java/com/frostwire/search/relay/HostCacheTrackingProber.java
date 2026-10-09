/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.IceBridgeHostCache;

import java.util.Optional;

/**
 * Decorates a {@link PeerProber} so the {@link IceBridgeHostCache} learns from every probe: a
 * successful handshake refreshes a cached server and a failed one counts against it, so dead
 * entries are evicted after {@link IceBridgeHostCache#MAX_CONSECUTIVE_FAILURES} strikes instead of
 * being retried forever. A peer whose identity record says it is a FORWARDER or BOTH node is added
 * to the cache, which is what Settings lists as a known IceBridge server; other hosts are never
 * added here.
 */
public final class HostCacheTrackingProber implements PeerProber {
    private final PeerProber delegate;
    private final IceBridgeHostCache cache;

    public HostCacheTrackingProber(PeerProber delegate, IceBridgeHostCache cache) {
        if (delegate == null || cache == null) {
            throw new IllegalArgumentException("delegate and cache are required");
        }
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<ProbedPeer> probe(String host, int port, long deadlineNanos) {
        Optional<ProbedPeer> peer = delegate.probe(host, port, deadlineNanos);
        try {
            if (peer.isPresent()) {
                String role = peer.get().record().map(IdentityRecord::role).orElse(null);
                if ("FORWARDER".equals(role) || "BOTH".equals(role)) {
                    cache.markSuccess(host, port, role);
                } else {
                    cache.markSuccessIfKnown(host, port);
                }
            } else {
                cache.markFailure(host, port);
            }
        } catch (RuntimeException ignored) {
            // the cache is best-effort
        }
        return peer;
    }
}
