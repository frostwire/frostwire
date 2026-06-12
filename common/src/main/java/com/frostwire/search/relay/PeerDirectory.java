/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory directory of known peers, each annotated with metadata
 * and a karma-weighted trust score. Supports web-of-trust queries
 * up to {@link RelayConstants#WOT_MAX_DEPTH} hops.
 *
 * <p>Trust model (WOT depth-N traversal):
 * <pre>
 *   directTrust(p)             = 1 if p is in the directory, else 0
 *   transitiveTrust(p, depth)  = directTrust(p)
 *                             + sum over endorsers e of p:
 *                                 transitiveTrust(e, depth-1) * DECAY
 *   finalScore(p)              = max(0, transitiveTrust(p, MAX_DEPTH)
 *                                 + karmaDelta(p))
 * </pre>
 *
 * <p>{@code DECAY} (typically 0.5) means trust decays by half per
 * hop, so a peer trusted only by 4 strangers 3 hops away is less
 * trusted than a peer trusted by 1 friend directly.
 *
 * <p>{@code karmaDelta} is the peer's karma score from
 * {@link PeerKarmaCache} (positive for participation, negative for
 * {@link #markSpam(byte[])}). Karma is an additive offset to the
 * structural trust, with the same exponential-decay shape.
 *
 * <p>The directory is bounded: when the entry count exceeds
 * {@code maxEntries}, the oldest-stale entries are evicted.
 *
 * <p><b>Thread-safety:</b> backed by a {@link ConcurrentHashMap};
 * the trust computation reads snapshots of the map and is
 * idempotent per snapshot.
 */
public final class PeerDirectory {

    private static final Logger LOG = Logger.getLogger(PeerDirectory.class);

    /** Per-hop decay for transitive trust. 0.5 is a common choice. */
    public static final double DECAY = 0.5;

    /** Bounded entry count; oldest-stale evicted when exceeded. */
    public static final int DEFAULT_MAX_ENTRIES = 1024;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final PeerKarmaCache karmaCache;
    private final int maxEntries;
    private final AtomicLong version = new AtomicLong();

    public PeerDirectory(PeerKarmaCache karmaCache) {
        this(karmaCache, DEFAULT_MAX_ENTRIES);
    }

    public PeerDirectory(PeerKarmaCache karmaCache, int maxEntries) {
        if (karmaCache == null) {
            throw new IllegalArgumentException("karmaCache is null");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.karmaCache = karmaCache;
        this.maxEntries = maxEntries;
    }

    /**
     * Add or update a peer in the directory. If the new entry pushes
     * us past {@code maxEntries}, evicts the least-recently-updated
     * entry.
     */
    public void upsert(byte[] peerPub, String hostname, int utpPort) {
        if (peerPub == null || peerPub.length != 32) {
            throw new IllegalArgumentException("peerPub must be 32 bytes");
        }
        if (hostname == null) {
            throw new IllegalArgumentException("hostname is null");
        }
        if (utpPort < 0 || utpPort > 65535) {
            throw new IllegalArgumentException("utpPort out of range");
        }
        String key = com.frostwire.util.Hex.encode(peerPub);
        entries.put(key, new Entry(peerPub, hostname, utpPort,
                System.currentTimeMillis(), 0L, false));
        evictIfNeeded();
        version.incrementAndGet();
    }

    /**
     * Add an endorser trust edge: this peer trusts {@code target}.
     * Used to build the web of trust over time.
     */
    public void addEndorser(byte[] targetPub, byte[] endorserPub) {
        if (targetPub == null || targetPub.length != 32) {
            throw new IllegalArgumentException("targetPub must be 32 bytes");
        }
        if (endorserPub == null || endorserPub.length != 32) {
            throw new IllegalArgumentException("endorserPub must be 32 bytes");
        }
        String key = com.frostwire.util.Hex.encode(targetPub);
        Entry e = entries.get(key);
        if (e == null) {
            // Implicit registration: target becomes a known peer with no hostname.
            e = new Entry(targetPub, "", 0, System.currentTimeMillis(), 0L, false);
            entries.put(key, e);
        }
        e.addEndorser(endorserPub);
        evictIfNeeded();
        version.incrementAndGet();
    }

    /**
     * Mark a peer as a spammer. Subtracts from their karma via the
     * karma cache and tags the entry so future trust queries return
     * a strongly negative score.
     */
    public void markSpam(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return;
        }
        String key = com.frostwire.util.Hex.encode(peerPub);
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(peerPub, "", 0, System.currentTimeMillis(), 0L, true);
            entries.put(key, e);
        } else {
            e.spam = true;
        }
        // Decrement karma via the cache's local score; we don't have
        // a method to write back to the remote chain, so this is a
        // local-only signal. A future change could publish a
        // negative endorsement to the remote chain.
        e.localKarmaDelta -= 5;
        version.incrementAndGet();
    }

    /**
     * Returns the trust score for {@code peerPub}. Computed as the
     * WOT depth-N traversal plus the peer's karma offset. Returns
     * a negative value for marked spammers. Returns 0 for unknown
     * peers (and below 0 if the karma is very negative).
     */
    public double trustScore(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return 0;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e == null) {
            return 0;
        }
        if (e.spam) {
            return -1.0;
        }
        // Karma offset: count of endorsements in the chain tail
        // (already a participation proxy) plus any local delta
        // (e.g. from markSpam).
        long karma = karmaCache.getKarma(peerPub) + e.localKarmaDelta;
        // Structural WOT trust up to MAX_DEPTH
        double transitive = transitiveTrust(peerPub, RelayConstants.WOT_MAX_DEPTH, new java.util.HashSet<>());
        return Math.max(-1.0, transitive + karma);
    }

    private double transitiveTrust(byte[] peerPub, int depth, java.util.Set<String> visited) {
        // BFS over the trust graph: each level contributes 1.0 per
        // newly-seen peer, weighted by DECAY^level. Cycles are
        // broken by the visited set.
        Entry target = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (target == null) {
            return 0;
        }
        double score = 1.0; // direct trust for the target itself
        java.util.List<byte[]> currentLevel = new java.util.ArrayList<>();
        for (String e : target.endorsers) {
            currentLevel.add(com.frostwire.util.Hex.decode(e));
        }
        int level = 1;
        while (level <= depth) {
            java.util.List<byte[]> nextLevel = new java.util.ArrayList<>();
            double levelSum = 0;
            for (byte[] e : currentLevel) {
                String key = com.frostwire.util.Hex.encode(e);
                if (visited.contains(key)) {
                    continue;
                }
                visited.add(key);
                Entry ee = entries.get(key);
                if (ee == null) {
                    continue;
                }
                levelSum += 1.0;
                for (String nested : ee.endorsers) {
                    nextLevel.add(com.frostwire.util.Hex.decode(nested));
                }
            }
            score += levelSum * Math.pow(DECAY, level);
            currentLevel = nextLevel;
            level++;
        }
        return score;
    }

    /** Look up an entry by pubkey. */
    public Optional<PeerInfo> get(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return Optional.empty();
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e == null) {
            return Optional.empty();
        }
        return Optional.of(new PeerInfo(e.peerPub.clone(), e.hostname, e.utpPort,
                e.lastUpdatedMs, e.endorsers.size(), e.spam));
    }

    /** Returns up to {@code limit} entries sorted by trust score descending. */
    public List<PeerInfo> topByTrust(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        List<Entry> snapshot = new ArrayList<>(entries.values());
        snapshot.sort((a, b) -> Double.compare(trustScore(b.peerPub), trustScore(a.peerPub)));
        List<PeerInfo> out = new ArrayList<>(Math.min(limit, snapshot.size()));
        for (int i = 0; i < Math.min(limit, snapshot.size()); i++) {
            Entry e = snapshot.get(i);
            out.add(new PeerInfo(e.peerPub.clone(), e.hostname, e.utpPort,
                    e.lastUpdatedMs, e.endorsers.size(), e.spam));
        }
        return out;
    }

    public int size() {
        return entries.size();
    }

    /** Monotonic version counter; bumps on any write. */
    public long version() {
        return version.get();
    }

    private void evictIfNeeded() {
        if (entries.size() <= maxEntries) {
            return;
        }
        // Find the entry with the oldest lastUpdatedMs
        Entry oldest = null;
        for (Entry e : entries.values()) {
            if (oldest == null || e.lastUpdatedMs < oldest.lastUpdatedMs) {
                oldest = e;
            }
        }
        if (oldest != null) {
            entries.remove(com.frostwire.util.Hex.encode(oldest.peerPub));
        }
    }

    /** Internal entry. */
    private static final class Entry {
        final byte[] peerPub;
        String hostname;
        int utpPort;
        long lastUpdatedMs;
        long localKarmaDelta;
        boolean spam;
        final java.util.Set<String> endorsers = ConcurrentHashMap.newKeySet();

        Entry(byte[] peerPub, String hostname, int utpPort, long lastUpdatedMs,
              long localKarmaDelta, boolean spam) {
            this.peerPub = peerPub.clone();
            this.hostname = hostname;
            this.utpPort = utpPort;
            this.lastUpdatedMs = lastUpdatedMs;
            this.localKarmaDelta = localKarmaDelta;
            this.spam = spam;
        }

        void addEndorser(byte[] endorserPub) {
            endorsers.add(com.frostwire.util.Hex.encode(endorserPub));
        }
    }

    /** Read-only view of a directory entry. */
    public static final class PeerInfo {
        private final byte[] peerPub;
        private final String hostname;
        private final int utpPort;
        private final long lastUpdatedMs;
        private final int endorserCount;
        private final boolean spam;

        PeerInfo(byte[] peerPub, String hostname, int utpPort, long lastUpdatedMs,
                 int endorserCount, boolean spam) {
            this.peerPub = peerPub.clone();
            this.hostname = hostname;
            this.utpPort = utpPort;
            this.lastUpdatedMs = lastUpdatedMs;
            this.endorserCount = endorserCount;
            this.spam = spam;
        }

        public byte[] peerPub() {
            return peerPub.clone();
        }

        public String hostname() {
            return hostname;
        }

        public int utpPort() {
            return utpPort;
        }

        public long lastUpdatedMs() {
            return lastUpdatedMs;
        }

        public int endorserCount() {
            return endorserCount;
        }

        public boolean isSpam() {
            return spam;
        }
    }
}
