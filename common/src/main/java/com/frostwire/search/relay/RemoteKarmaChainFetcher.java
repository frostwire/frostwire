/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches a remote peer's karma chain manifest, verifies it with
 * {@link KarmaChain#verify(List)}, and caches the verified chain.
 *
 * <p>The actual transport is abstracted behind a {@link KarmaChainSource}
 * (composition over inheritance). The default DHT-backed source
 * is {@link DhtKarmaChainSource}. Tests can inject a fake source
 * to avoid spinning up a real DHT cluster.
 *
 * <p>BEP 46 authentication: the source is responsible for verifying
 * the publisher's signature against the manifest hash before
 * returning. We trust that the manifest really came from the named
 * peer pubkey.
 *
 * <p>Chain integrity: after reconstruction, the entries are
 * passed through {@link KarmaChain#verify(List)} which checks
 * hash links, signatures, energy budgets, and ordering. A
 * failed verification is treated as "no chain" (cached as
 * absent, returns null on subsequent lookups).
 *
 * <p><b>Caching:</b> the first call for a given peer triggers a
 * remote lookup. Subsequent calls return the cached chain until
 * {@link #evict(byte[])} is called or the cache is cleared.
 *
 * <p>Fail-closed: any source or parse error returns null.
 */
public final class RemoteKarmaChainFetcher {

    private static final Logger LOG = Logger.getLogger(RemoteKarmaChainFetcher.class);

    private final KarmaChainSource source;
    private final ConcurrentHashMap<String, List<KarmaChainEntry>> verified = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> absent = new ConcurrentHashMap<>();

    public RemoteKarmaChainFetcher(KarmaChainSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        this.source = source;
    }

    /**
     * Fetch and verify a peer's karma chain. Returns the verified
     * chain entries, or null if the peer has no chain, the lookup
     * failed, or verification failed.
     */
    public List<KarmaChainEntry> fetchChain(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return null;
        }
        String key = cacheKey(peerPub);
        List<KarmaChainEntry> cached = verified.get(key);
        if (cached != null) {
            return cached;
        }
        if (absent.containsKey(key)) {
            return null;
        }
        try {
            Entry manifest = source.fetchManifest(peerPub);
            if (manifest == null) {
                absent.put(key, Boolean.TRUE);
                return null;
            }
            List<KarmaChainEntry> entries = parseAndVerify(manifest);
            if (entries == null) {
                absent.put(key, Boolean.TRUE);
                return null;
            }
            verified.put(key, entries);
            return entries;
        } catch (Throwable t) {
            LOG.debug("Remote karma fetch failed for peer " + com.frostwire.util.Hex.encode(peerPub), t);
            return null;
        }
    }

    /**
     * Drop a peer from the cache. Forces the next fetchChain call
     * to re-query the source.
     */
    public void evict(byte[] peerPub) {
        if (peerPub == null) {
            return;
        }
        String key = cacheKey(peerPub);
        verified.remove(key);
        absent.remove(key);
    }

    /** Clear all cached chains. */
    public void clear() {
        verified.clear();
        absent.clear();
    }

    /** Number of peers with verified chains in the cache. */
    public int cacheSize() {
        return verified.size();
    }

    private List<KarmaChainEntry> parseAndVerify(Entry manifest) {
        Map<String, Entry> dict;
        try {
            dict = manifest.dictionary();
        } catch (Throwable t) {
            return null;
        }
        Entry entriesEntry = dict.get("entries");
        if (entriesEntry == null) {
            return null;
        }
        List<Entry> entryDicts;
        try {
            entryDicts = entriesEntry.list();
        } catch (Throwable t) {
            return null;
        }
        List<KarmaChainEntry> chain = new ArrayList<>(entryDicts.size());
        for (Entry e : entryDicts) {
            Map<String, Entry> entryDict;
            try {
                entryDict = e.dictionary();
            } catch (Throwable t) {
                return null;
            }
            KarmaChainEntry entry = KarmaChainEntry.reconstruct(entryDict);
            if (entry == null) {
                return null;
            }
            chain.add(entry);
        }
        if (!KarmaChain.verify(chain)) {
            return null;
        }
        return Collections.unmodifiableList(chain);
    }

    private static String cacheKey(byte[] peerPub) {
        return com.frostwire.util.Hex.encode(peerPub);
    }
}
