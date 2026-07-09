/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import com.frostwire.search.relay.RateLimiter;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory registry of authenticated IceBridge peers.
 *
 * <p>All state is kept in RAM. Stale peers are evicted periodically by
 * {@link #evictStale(long)}. The registry is intentionally isolated from
 * search semantics: it only tracks who can relay and how to reach them.
 */
public final class PeerRegistry {

    private static final Logger LOG = Logger.getLogger(PeerRegistry.class);

    private final Map<String, PeerRecord> byPubHex = new ConcurrentHashMap<>();
    private final RateLimiter rateLimiter;
    private final int maxPeers;
    private final AtomicLong registrations = new AtomicLong();
    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();

    public PeerRegistry(IceBridgeConfig config) {
        this.maxPeers = config.maxPeers();
        // Capacity = max burst in one second; refill = sustained QPS.
        this.rateLimiter = new RateLimiter(config.maxQpsPerKey(), config.maxQpsPerKey());
    }

    /**
     * Register or refresh a peer. Registration is rate-limited per public key
     * to prevent a single identity from spamming the registry.
     *
     * @return true if the record was accepted
     */
    public boolean register(PeerRecord record) {
        if (record == null) {
            return false;
        }
        byte[] pub = record.ed25519Pub();
        if (!rateLimiter.tryAcquire(pub)) {
            LOG.debug("PeerRegistry: rate-limited registration from " + record.ed25519PubHex());
            return false;
        }
        String key = Hex.encode(pub);
        boolean isNewPeer = !byPubHex.containsKey(key);

        // Reject new identities once we are at capacity. Refreshes of
        // existing peers are always allowed.
        if (isNewPeer && byPubHex.size() >= maxPeers) {
            LOG.warn("PeerRegistry: at capacity (" + maxPeers
                    + "); dropped new peer " + record.ed25519PubHex());
            return false;
        }

        byPubHex.merge(key, record, (existing, incoming) -> {
            // Fresher endpoint wins; otherwise keep existing.
            if (incoming.lastSeenMs() >= existing.lastSeenMs()) {
                return incoming;
            }
            return existing;
        });
        registrations.incrementAndGet();
        return true;
    }

    /**
     * Look up recent peers that advertise relay/forward capability.
     *
     * @param maxResults maximum number of records to return
     * @return an immutable list of forward-capable peers
     */
    public List<PeerRecord> lookupForwarders(int maxResults) {
        return lookupPeers(maxResults, true);
    }

    /**
     * Look up registered peers for mesh discovery / routing.
     *
     * @param maxResults   maximum number of records to return
     * @param forwardersOnly if true, only FORWARDER/BOTH roles
     */
    public List<PeerRecord> lookupPeers(int maxResults, boolean forwardersOnly) {
        if (maxResults <= 0) {
            return Collections.emptyList();
        }
        lookups.incrementAndGet();
        List<PeerRecord> result = new ArrayList<>(Math.min(maxResults, byPubHex.size()));
        for (PeerRecord r : byPubHex.values()) {
            if (forwardersOnly && !r.canForward()) {
                continue;
            }
            result.add(r);
            if (result.size() >= maxResults) {
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Look up a single peer by public key.
     */
    public PeerRecord lookup(byte[] ed25519Pub) {
        if (ed25519Pub == null || ed25519Pub.length != 32) {
            return null;
        }
        return byPubHex.get(Hex.encode(ed25519Pub));
    }

    /**
     * Remove peers whose {@link PeerRecord#lastSeenMs()} is older than
     * {@code ttlMs}.
     *
     * @return number of records removed
     */
    public int evictStale(long ttlMs) {
        if (ttlMs <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - ttlMs;
        int removed = 0;
        for (Map.Entry<String, PeerRecord> e : byPubHex.entrySet()) {
            if (e.getValue().lastSeenMs() < cutoff) {
                if (byPubHex.remove(e.getKey(), e.getValue())) {
                    removed++;
                }
            }
        }
        evicted.addAndGet(removed);
        rateLimiter.evictIdle(Math.max(ttlMs, 60_000));
        return removed;
    }

    public int size() {
        return byPubHex.size();
    }

    public long registrations() {
        return registrations.get();
    }

    public long lookups() {
        return lookups.get();
    }

    public long evicted() {
        return evicted.get();
    }
}