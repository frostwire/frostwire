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
import java.util.Random;
import java.util.Set;
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

    /**
     * Consecutive delivery failures after which a peer is treated as unreachable and dropped. A
     * reachable peer never accumulates strikes because any inbound frame or verified response
     * resets the counter.
     */
    public static final int MAX_FAILURES = 5;

    /**
     * How long an entry stays queryable after the last time we actually heard from the peer. Once
     * exceeded the entry is no longer selected for search forwarding and is pruned by {@link
     * #evictUnreachable(long)}.
     */
    public static final long CONTACT_TTL_MS = 10 * 60_000L;

    /**
     * How long an entry that has never been heard from stays queryable after it was last affirmed
     * by discovery/registry sync. Prevents a dead peer that keeps getting re-imported from being
     * selected forever.
     */
    public static final long AFFIRM_TTL_MS = 5 * 60_000L;

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
     * Add or update a peer in the directory. The entry is marked
     * as unverified; it can be used for trust-graph edges but is
     * not considered queryable by distributed search until it has
     * been authenticated with {@link #upsertVerified(byte[], String, int)}.
     */
    public void upsert(byte[] peerPub, String hostname, int utpPort) {
        upsert(peerPub, hostname, utpPort, 0, false);
    }

    public void upsert(byte[] peerPub, String hostname, int utpPort, int rudpPort) {
        upsert(peerPub, hostname, utpPort, rudpPort, false);
    }

    public void upsertVerified(byte[] peerPub, String hostname, int utpPort) {
        upsert(peerPub, hostname, utpPort, 0, true);
    }

    public void upsertVerified(byte[] peerPub, String hostname, int utpPort, int rudpPort) {
        upsert(peerPub, hostname, utpPort, rudpPort, true);
    }

    private void upsert(byte[] peerPub, String hostname, int utpPort, int rudpPort, boolean verified) {
        upsert(peerPub, hostname, utpPort, rudpPort, verified, null, null);
    }

    private synchronized void upsert(byte[] peerPub, String hostname, int utpPort, int rudpPort, boolean verified,
                        Long capabilitiesOrNull, String icebridgeVersionOrNull) {
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
        Entry existing = entries.get(key);
        // An unauthenticated hint must not replace a possession-verified route or metadata.
        if (existing != null && existing.verified && !verified) return;
        int effectiveRudpPort = rudpPort > 0 ? rudpPort : (existing != null ? existing.rudpPort : 0);
        long caps = capabilitiesOrNull != null
                ? capabilitiesOrNull
                : (existing != null ? existing.capabilities : NodeCapabilities.DEFAULT_PEER);
        String ibVer = coalesceIcebridgeVersion(icebridgeVersionOrNull,
                existing != null ? existing.icebridgeVersion : null);
        Entry refreshed = new Entry(peerPub, hostname, utpPort, effectiveRudpPort,
                System.currentTimeMillis(), existing != null ? existing.localKarmaDelta : 0L,
                existing != null && existing.spam, verified, caps, ibVer);
        if (existing != null) {
            refreshed.endorsers.addAll(existing.endorsers);
            refreshed.indexDigest = existing.indexDigest;
            refreshed.lastContactMs = existing.lastContactMs;
            refreshed.failures = existing.failures;
        }
        entries.put(key, refreshed);
        evictIfNeeded();
        version.incrementAndGet();
    }

    /**
     * Upsert a verified peer with explicit capability bitflags.
     */
    public void upsertVerified(byte[] peerPub, String hostname, int utpPort, int rudpPort,
                               long capabilities) {
        upsertVerified(peerPub, hostname, utpPort, rudpPort, capabilities, null);
    }

    /**
     * Upsert a verified peer with capabilities and IceBridge software version.
     */
    public void upsertVerified(byte[] peerPub, String hostname, int utpPort, int rudpPort,
                               long capabilities, String icebridgeVersion) {
        upsert(peerPub, hostname, utpPort, rudpPort, true, capabilities, icebridgeVersion);
    }

    private static String coalesceIcebridgeVersion(String incoming, String existing) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return "";
    }

    /**
     * Update only the capability bitflags for a known peer.
     */
    public synchronized void setCapabilities(byte[] peerPub, long capabilities) {
        if (peerPub == null || peerPub.length != 32) {
            return;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e != null) {
            e.capabilities = capabilities;
            version.incrementAndGet();
        }
    }

    /**
     * Add an endorser trust edge: this peer trusts {@code target}.
     * Used to build the web of trust over time. If the target is
     * not yet known, it is registered as an unverified peer.
     */
    public synchronized void addEndorser(byte[] targetPub, byte[] endorserPub) {
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
            e = new Entry(targetPub, "", 0, 0, System.currentTimeMillis(), 0L, false, false,
                    NodeCapabilities.NONE, "");
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
    public synchronized void markSpam(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return;
        }
        String key = com.frostwire.util.Hex.encode(peerPub);
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(peerPub, "", 0, 0, System.currentTimeMillis(), 0L, true, false,
                    NodeCapabilities.NONE, "");
            entries.put(key, e);
        } else {
            e.spam = true;
        }
        // Decrement karma via the cache's local score; we don't have
        // a method to write back to the remote chain, so this is a
        // local-only signal. A future change could publish a
        // negative endorsement to the remote chain.
        e.localKarmaDelta -= 5;
        evictIfNeeded();
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
        long karma = karmaCache.getCachedKarma(peerPub) + e.localKarmaDelta;
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
        return Optional.of(toPeerInfo(e));
    }

    /**
     * Returns up to {@code limit} verified entries sorted by trust score descending.
     * Unverified placeholder entries are excluded.
     */
    public List<PeerInfo> topByTrustVerified(int limit) {
        return topByTrustVerified(limit, NodeCapabilities.NONE);
    }

    /**
     * Verified peers with at least {@code requiredCaps} capability flags set.
     * Pass {@link NodeCapabilities#NONE} to skip capability filtering.
     */
    public List<PeerInfo> topByTrustVerified(int limit, long requiredCaps) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        List<Entry> snapshot = new ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (Entry e : entries.values()) {
            if (e.verified && NodeCapabilities.has(e.capabilities, requiredCaps)
                    && isLive(e, nowMs)) {
                snapshot.add(e);
            }
        }
        List<ScoredEntry> scored = new ArrayList<>(snapshot.size());
        for (Entry e : snapshot) scored.add(new ScoredEntry(e, trustScore(e.peerPub)));
        scored.sort((a, b) -> Double.compare(b.key, a.key));
        List<PeerInfo> out = new ArrayList<>(Math.min(limit, snapshot.size()));
        for (int i = 0; i < Math.min(limit, snapshot.size()); i++) {
            out.add(toPeerInfo(scored.get(i).entry));
        }
        return out;
    }

    /** Returns up to {@code limit} entries sorted by trust score descending. */
    public List<PeerInfo> topByTrust(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        List<Entry> snapshot = new ArrayList<>(entries.values());
        List<ScoredEntry> scored = new ArrayList<>(snapshot.size());
        for (Entry e : snapshot) scored.add(new ScoredEntry(e, trustScore(e.peerPub)));
        scored.sort((a, b) -> Double.compare(b.key, a.key));
        List<PeerInfo> out = new ArrayList<>(Math.min(limit, snapshot.size()));
        for (int i = 0; i < Math.min(limit, snapshot.size()); i++) {
            out.add(toPeerInfo(scored.get(i).entry));
        }
        return out;
    }

    /**
     * Trust-weighted random sample of verified peers for search forwarding
     * (Efraimidis-Spirakis: key = uniform()^(1/weight), weight = 1 + positive
     * trust). High-trust peers are preferred, but every verified non-spam
     * peer keeps a baseline chance, so repeated forwards spread load across
     * the directory instead of hammering the same top-M peers, and newcomers
     * still get explored. Spam entries are never sampled.
     *
     * @param limit maximum peers to return
     * @param excludeHex hex-encoded pubs to skip (request path, sender, self)
     * @param requiredCaps capability flags, {@link NodeCapabilities#NONE} to skip
     * @param random source of randomness (pass a seed in tests)
     */
    public List<PeerInfo> sampleVerified(int limit, Set<String> excludeHex, long requiredCaps,
                                         Random random) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (random == null) {
            throw new IllegalArgumentException("random is null");
        }
        return sampleEntries(liveEligible(excludeHex, requiredCaps), limit, random);
    }

    private static final class ScoredEntry {
        final Entry entry;
        final double key;

        ScoredEntry(Entry entry, double key) {
            this.entry = entry;
            this.key = key;
        }
    }

    private static PeerInfo toPeerInfo(Entry e) {
        return new PeerInfo(e.peerPub.clone(), e.hostname, e.utpPort, e.rudpPort,
                e.lastUpdatedMs, e.endorsers.size(), e.spam, e.verified, e.capabilities,
                e.icebridgeVersion);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Drop an entry by pubkey. No-op if the entry is not present.
     * Returns true if an entry was removed.
     */
    public synchronized boolean evict(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return false;
        }
        boolean removed = entries.remove(com.frostwire.util.Hex.encode(peerPub)) != null;
        if (removed) {
            karmaCache.evict(peerPub);
            version.incrementAndGet();
        }
        return removed;
    }

    /**
     * Record the content fingerprint a peer announced. Passing {@code null} clears it. Malformed
     * frames are ignored so a peer cannot poison routing with an oversized announcement.
     */
    public synchronized void setIndexDigest(byte[] peerPub, byte[] digest) {
        if (peerPub == null || peerPub.length != 32) {
            return;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e == null) {
            // The announcement can arrive before the relay has imported the peer as verified
            // (registry import runs on its own cadence). Keep the digest on an unverified
            // placeholder so the later upsert promotes it with the fingerprint intact.
            e = new Entry(peerPub, "", 0, 0, System.currentTimeMillis(), 0L, false, false,
                    NodeCapabilities.NONE, "");
            entries.put(com.frostwire.util.Hex.encode(peerPub), e);
            evictIfNeeded();
        }
        if (digest == null) {
            e.indexDigest = null;
        } else {
            IndexDigest parsed = IndexDigest.fromBytes(digest);
            if (parsed == null) {
                return;
            }
            e.indexDigest = parsed.toBytes();
        }
        version.incrementAndGet();
    }

    /**
     * Record positive proof of contact with a peer (an authenticated inbound frame or a verified
     * response). Resets the failure streak so a peer that came back is queryable again.
     */
    public synchronized void markContact(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e == null) {
            return;
        }
        e.lastContactMs = System.currentTimeMillis();
        e.failures = 0;
    }

    /**
     * Record one consecutive delivery failure. The peer is dropped once it reaches {@link
     * #MAX_FAILURES} so searches stop being routed to an unreachable node. Returns true if the
     * entry was evicted.
     */
    public synchronized boolean markFailure(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return false;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        if (e == null) {
            return false;
        }
        e.failures++;
        if (e.failures >= MAX_FAILURES) {
            return evict(peerPub);
        }
        return false;
    }

    /**
     * Drop peers proven unreachable: too many consecutive failures, silent past {@link
     * #CONTACT_TTL_MS}, or never heard from and no longer affirmed within {@link #AFFIRM_TTL_MS}.
     * Returns the number of entries removed.
     */
    public synchronized int evictUnreachable(long nowMs) {
        List<byte[]> doomed = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!isLive(e, nowMs)) {
                doomed.add(e.peerPub);
            }
        }
        int evicted = 0;
        for (byte[] pub : doomed) {
            if (evict(pub)) {
                evicted++;
            }
        }
        return evicted;
    }

    /** True while an entry is recent enough and has not failed repeatedly. */
    public boolean isLive(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return false;
        }
        Entry e = entries.get(com.frostwire.util.Hex.encode(peerPub));
        return e != null && isLive(e, System.currentTimeMillis());
    }

    private static boolean isLive(Entry e, long nowMs) {
        if (e.failures >= MAX_FAILURES) {
            return false;
        }
        if (e.lastContactMs > 0) {
            return nowMs - e.lastContactMs <= CONTACT_TTL_MS;
        }
        return nowMs - e.lastUpdatedMs <= AFFIRM_TTL_MS;
    }

    /**
     * Holder-aware sample: peers whose announced {@link IndexDigest} reports the query tokens come
     * first (ranked by match count, then trust). Remaining slots are filled from live peers so
     * content on peers with an unknown digest is still discoverable.
     *
     * <p>{@code exploreSlots} reserves slots for that exploration; when nothing matches, the whole
     * budget explores so recall never collapses.
     */
    public List<PeerInfo> sampleHolders(String keywords, int limit, Set<String> excludeHex,
                                        long requiredCaps, Random random, int exploreSlots) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (random == null) {
            throw new IllegalArgumentException("random is null");
        }
        List<String> queryTokens = IndexDigest.tokenize(keywords);
        List<Entry> live = liveEligible(excludeHex, requiredCaps);
        List<Entry> matched = new ArrayList<>();
        List<Entry> rest = new ArrayList<>();
        for (Entry e : live) {
            if (digestMatchScore(e, queryTokens) > 0) {
                matched.add(e);
            } else {
                rest.add(e);
            }
        }
        matched.sort((a, b) -> {
            int sa = digestMatchScore(a, queryTokens);
            int sb = digestMatchScore(b, queryTokens);
            if (sa != sb) {
                return Integer.compare(sb, sa);
            }
            return Double.compare(trustScore(b.peerPub), trustScore(a.peerPub));
        });
        int explore = matched.isEmpty()
                ? limit
                : Math.max(0, Math.min(exploreSlots, limit - 1));
        int holderSlots = Math.max(1, limit - explore);
        List<PeerInfo> out = new ArrayList<>(limit);
        for (int i = 0; i < matched.size() && i < holderSlots; i++) {
            out.add(toPeerInfo(matched.get(i)));
        }
        if (out.size() < limit) {
            out.addAll(sampleEntries(rest, limit - out.size(), random));
        }
        return out;
    }

    /**
     * Stable reorder of an existing candidate list: peers whose announced digest may hold the
     * query tokens first (by match count, then incoming order), remaining peers keep their
     * incoming (e.g. keyspace/trust) order.
     */
    public List<PeerInfo> rankByHoldership(String keywords, List<PeerInfo> peers) {
        if (peers == null || peers.isEmpty()) {
            return peers == null ? List.of() : peers;
        }
        List<String> queryTokens = IndexDigest.tokenize(keywords);
        if (queryTokens.isEmpty()) {
            return peers;
        }
        List<PeerInfo> matched = new ArrayList<>();
        List<PeerInfo> rest = new ArrayList<>();
        java.util.Map<String, Integer> scores = new java.util.HashMap<>();
        for (PeerInfo p : peers) {
            String key = com.frostwire.util.Hex.encode(p.peerPub());
            Entry e = entries.get(key);
            int score = e == null ? 0 : digestMatchScore(e, queryTokens);
            if (score > 0) {
                scores.put(key, score);
                matched.add(p);
            } else {
                rest.add(p);
            }
        }
        if (matched.isEmpty()) {
            return peers;
        }
        matched.sort((a, b) -> Integer.compare(
                scores.getOrDefault(com.frostwire.util.Hex.encode(b.peerPub()), 0),
                scores.getOrDefault(com.frostwire.util.Hex.encode(a.peerPub()), 0)));
        List<PeerInfo> out = new ArrayList<>(peers.size());
        out.addAll(matched);
        out.addAll(rest);
        return out;
    }

    private List<Entry> liveEligible(Set<String> excludeHex, long requiredCaps) {
        long nowMs = System.currentTimeMillis();
        List<Entry> eligible = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!e.verified || e.spam || !NodeCapabilities.has(e.capabilities, requiredCaps)) {
                continue;
            }
            if (!isLive(e, nowMs)) {
                continue;
            }
            if (excludeHex != null
                    && excludeHex.contains(com.frostwire.util.Hex.encode(e.peerPub))) {
                continue;
            }
            eligible.add(e);
        }
        return eligible;
    }

    private static int digestMatchScore(Entry e, List<String> queryTokens) {
        byte[] digest = e.indexDigest;
        if (digest == null || queryTokens.isEmpty()) {
            return 0;
        }
        IndexDigest parsed = IndexDigest.fromBytes(digest);
        return parsed == null ? 0 : parsed.matchCount(queryTokens);
    }

    private List<PeerInfo> sampleEntries(List<Entry> candidates, int limit, Random random) {
        if (limit <= 0 || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScoredEntry> keyed = new ArrayList<>(candidates.size());
        for (Entry e : candidates) {
            double weight = 1.0 + Math.max(0.0, trustScore(e.peerPub));
            keyed.add(new ScoredEntry(e, Math.pow(random.nextDouble(), 1.0 / weight)));
        }
        keyed.sort((a, b) -> Double.compare(b.key, a.key));
        List<PeerInfo> out = new ArrayList<>(Math.min(limit, keyed.size()));
        for (int i = 0; i < Math.min(limit, keyed.size()); i++) {
            out.add(toPeerInfo(keyed.get(i).entry));
        }
        return out;
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
            evict(oldest.peerPub);
        }
    }

    /** Internal entry. */
    private static final class Entry {
        final byte[] peerPub;
        final String hostname;
        final int utpPort;
        final int rudpPort;
        final long lastUpdatedMs;
        volatile long localKarmaDelta;
        volatile boolean spam;
        final boolean verified;
        volatile long capabilities;
        final String icebridgeVersion;
        volatile byte[] indexDigest;
        volatile long lastContactMs;
        volatile int failures;
        final java.util.Set<String> endorsers = ConcurrentHashMap.newKeySet();

        Entry(byte[] peerPub, String hostname, int utpPort, int rudpPort, long lastUpdatedMs,
              long localKarmaDelta, boolean spam, boolean verified, long capabilities,
              String icebridgeVersion) {
            this.peerPub = peerPub.clone();
            this.hostname = hostname;
            this.utpPort = utpPort;
            this.rudpPort = rudpPort;
            this.lastUpdatedMs = lastUpdatedMs;
            this.localKarmaDelta = localKarmaDelta;
            this.spam = spam;
            this.verified = verified;
            this.capabilities = capabilities;
            this.icebridgeVersion = icebridgeVersion != null ? icebridgeVersion : "";
        }

        void addEndorser(byte[] endorserPub) {
            if (endorsers.size() < DEFAULT_MAX_ENTRIES) {
                endorsers.add(com.frostwire.util.Hex.encode(endorserPub));
            }
        }
    }

    /** Read-only view of a directory entry. */
    public static final class PeerInfo {
        private final byte[] peerPub;
        private final String hostname;
        private final int utpPort;
        private final int rudpPort;
        private final long lastUpdatedMs;
        private final int endorserCount;
        private final boolean spam;
        private final boolean verified;
        private final long capabilities;
        private final String icebridgeVersion;

        PeerInfo(byte[] peerPub, String hostname, int utpPort, int rudpPort, long lastUpdatedMs,
                 int endorserCount, boolean spam, boolean verified) {
            this(peerPub, hostname, utpPort, rudpPort, lastUpdatedMs, endorserCount, spam, verified,
                    NodeCapabilities.DEFAULT_PEER, "");
        }

        PeerInfo(byte[] peerPub, String hostname, int utpPort, int rudpPort, long lastUpdatedMs,
                 int endorserCount, boolean spam, boolean verified, long capabilities) {
            this(peerPub, hostname, utpPort, rudpPort, lastUpdatedMs, endorserCount, spam, verified,
                    capabilities, "");
        }

        PeerInfo(byte[] peerPub, String hostname, int utpPort, int rudpPort, long lastUpdatedMs,
                 int endorserCount, boolean spam, boolean verified, long capabilities,
                 String icebridgeVersion) {
            this.peerPub = peerPub.clone();
            this.hostname = hostname;
            this.utpPort = utpPort;
            this.rudpPort = rudpPort;
            this.lastUpdatedMs = lastUpdatedMs;
            this.endorserCount = endorserCount;
            this.spam = spam;
            this.verified = verified;
            this.capabilities = capabilities;
            this.icebridgeVersion = icebridgeVersion != null ? icebridgeVersion : "";
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

        public int rudpPort() {
            return rudpPort;
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

        public boolean isVerified() {
            return verified;
        }

        public long capabilities() {
            return capabilities;
        }

        /** IceBridge software version (empty if unknown / pre-announce peer). */
        public String icebridgeVersion() {
            return icebridgeVersion;
        }
    }
}
