/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches aggregate karma scores per peer, computed from the most
 * recently fetched remote chain (via {@link RemoteKarmaChainFetcher})
 * or the local karma table.
 *
 * <p>Scoring model: a peer's karma score is the number of
 * {@code ENDORSEMENT} entries in their verified chain tail that
 * target other peers. This is a rough participation signal — a
 * peer that has actively endorsed other peers' downloads has a
 * higher score than one that only mines epoch commitments.
 *
 * <p>This is intentionally a proxy. A more robust score would
 * aggregate endorsements <em>received</em> by this peer from
 * other peers' chains, but that requires crawling remote
 * chains, which is out of scope for this build.
 *
 * <p>The fetcher owns one bounded, expiring positive/negative cache.
 * Aggregation is bounded by its maximum verified chain size, avoiding
 * a second memoization cache with a different lifetime.
 */
public class PeerKarmaCache implements AutoCloseable {

    private final RemoteKarmaChainFetcher fetcher;
    private final AtomicLong fetches = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    public PeerKarmaCache(RemoteKarmaChainFetcher fetcher) {
        if (fetcher == null) {
            throw new IllegalArgumentException("fetcher is null");
        }
        this.fetcher = fetcher;
    }

    /**
     * Returns a karma score for the given peer. 0 means "no chain
     * or no endorsements in the tail". Never negative.
     * May wait up to five seconds; routing/UI callers must use {@link #getCachedKarma(byte[])}.
     */
    public long getKarma(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return 0;
        }
        if (fetcher.isCached(peerPub)) {
            cacheHits.incrementAndGet();
        } else {
            fetches.incrementAndGet();
        }
        return computeScore(fetcher.fetchChain(peerPub));
    }

    /** Nonblocking routing/UI lookup; coalesces bounded refresh work on cache misses. */
    public long getCachedKarma(byte[] peerPub) {
        return computeScore(fetcher.getCachedChain(peerPub));
    }

    /** Drop the cached score for a peer; the next lookup re-fetches. */
    public void evict(byte[] peerPub) {
        if (peerPub == null) {
            return;
        }
        fetcher.evict(peerPub);
    }

    /** Drop all cached scores. */
    public void clear() {
        fetcher.clear();
    }

    /** This cache owns its fetcher; close cancels its refreshes, not the shared DHT session. */
    @Override
    public void close() {
        fetcher.close();
    }

    /** Diagnostic counters. */
    public long fetchCount() {
        return fetches.get();
    }

    public long cacheHitCount() {
        return cacheHits.get();
    }

    /** Compute the score from a chain without caching. */
    public static long computeScore(List<KarmaChainEntry> chain) {
        if (chain == null || chain.isEmpty()) {
            return 0;
        }
        long count = 0;
        for (KarmaChainEntry e : chain) {
            if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
                count++;
            }
        }
        return count;
    }
}
