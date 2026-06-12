/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

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
 * <p>Scores are cached in-memory by the fetcher; this class
 * additionally memoizes the aggregate result so we don't
 * re-iterate the chain on every score lookup.
 */
public class PeerKarmaCache {

    private static final Logger LOG = Logger.getLogger(PeerKarmaCache.class);

    private final RemoteKarmaChainFetcher fetcher;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> scoreCache = new java.util.concurrent.ConcurrentHashMap<>();
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
     */
    public long getKarma(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return 0;
        }
        String key = com.frostwire.util.Hex.encode(peerPub);
        Long cached = scoreCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        fetches.incrementAndGet();
        List<KarmaChainEntry> chain = fetcher.fetchChain(peerPub);
        long score = computeScore(chain);
        scoreCache.put(key, score);
        return score;
    }

    /** Drop the cached score for a peer; the next lookup re-fetches. */
    public void evict(byte[] peerPub) {
        if (peerPub == null) {
            return;
        }
        scoreCache.remove(com.frostwire.util.Hex.encode(peerPub));
        fetcher.evict(peerPub);
    }

    /** Drop all cached scores. */
    public void clear() {
        scoreCache.clear();
        fetcher.clear();
    }

    /** Diagnostic counters. */
    public long fetchCount() {
        return fetches.get();
    }

    public long cacheHitCount() {
        return cacheHits.get();
    }

    /** Visible for tests: compute the score from a chain without caching. */
    static long computeScore(List<KarmaChainEntry> chain) {
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
