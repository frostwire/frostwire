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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

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
 * remote lookup. Positive and negative results expire, and simultaneous
 * requests for one peer coalesce. Routing uses {@link #getCachedChain(byte[])}.
 *
 * <p>Fail-closed: any source or parse error returns null.
 */
public final class RemoteKarmaChainFetcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RemoteKarmaChainFetcher.class);

    private final KarmaChainSource source;
    // A shared, bounded pool prevents per-directory or per-key thread growth. Idle threads
    // retire. close()/evict() cancel only this fetcher's work, never another owner's pool.
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
                Thread thread = new Thread(r, "relay-karma-refresh");
                thread.setDaemon(true);
                return thread;
            });
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "relay-karma-deadlines");
        thread.setDaemon(true);
        return thread;
    });
    static {
        WORKERS.allowCoreThreadTimeOut(true);
        DEADLINES.setRemoveOnCancelPolicy(true);
    }
    private final Map<String, CachedChain> cache = new LinkedHashMap<>();
    private final int maxEntries;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final long lookupTimeoutNanos;
    private final ThreadPoolExecutor workers;
    private boolean closed;

    public RemoteKarmaChainFetcher(KarmaChainSource source) {
        this(source, 1024, 60_000, System::nanoTime);
    }

    RemoteKarmaChainFetcher(KarmaChainSource source, int maxEntries, long ttlMs, LongSupplier clock) {
        this(source, maxEntries, ttlMs, clock, 5000, WORKERS);
    }

    RemoteKarmaChainFetcher(KarmaChainSource source, int maxEntries, long ttlMs, LongSupplier clock,
                           int lookupTimeoutMs, ThreadPoolExecutor workers) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        this.source = source;
        if (maxEntries <= 0 || ttlMs <= 0 || lookupTimeoutMs <= 0 || clock == null || workers == null) {
            throw new IllegalArgumentException("invalid cache bounds");
        }
        this.maxEntries = maxEntries;
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMs);
        this.clock = clock;
        this.lookupTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(lookupTimeoutMs);
        this.workers = workers;
    }

    /**
     * Fetch and verify a peer's karma chain. Returns the verified
     * chain entries, or null if the peer has no chain, the lookup
     * failed, or verification failed. Must run off UI/event-loop threads: waits at most
     * five seconds, including queue time. Concurrent requests share the same lookup.
     */
    public List<KarmaChainEntry> fetchChain(byte[] peerPub) {
        return fetch(peerPub, false);
    }

    /** Never waits for network/native work; cold and expired entries return no trust. */
    public List<KarmaChainEntry> getCachedChain(byte[] peerPub) {
        return fetch(peerPub, true);
    }

    private List<KarmaChainEntry> fetch(byte[] peerPub, boolean asynchronous) {
        if (peerPub == null || peerPub.length != 32 || Thread.currentThread().isInterrupted()) {
            return null;
        }
        String key = cacheKey(peerPub);
        CachedChain state;
        synchronized (this) {
            if (closed) return null;
            state = cached(key);
            if (state != null && (asynchronous || state.complete)) return state.entries;
            if (state == null) {
                if (cache.size() >= maxEntries) prune();
                if (cache.size() >= maxEntries) {
                    Iterator<CachedChain> it = cache.values().iterator();
                    while (it.hasNext()) {
                        if (it.next().complete) {
                            it.remove();
                            break;
                        }
                    }
                    if (cache.size() >= maxEntries) return null;
                }
                state = new CachedChain();
                state.lookupDeadline = System.nanoTime() + lookupTimeoutNanos;
                CachedChain loading = state;
                byte[] pub = peerPub.clone();
                state.task = new FutureTask<>(() -> {
                    load(key, pub, loading);
                    return null;
                });
                cache.put(key, state);
                try {
                    // Admission and timer installation are atomic against close/evict/completion.
                    workers.execute(state.task);
                    state.expiry = DEADLINES.schedule(() -> {
                        synchronized (RemoteKarmaChainFetcher.this) {
                            if (!loading.complete && cache.remove(key, loading)) cancel(loading);
                        }
                    }, Math.max(0, state.lookupDeadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (RejectedExecutionException e) {
                    cache.remove(key, state);
                    cancel(state);
                    return null;
                }
            }
        }
        if (asynchronous) return null;
        try {
            long remaining = state.lookupDeadline - System.nanoTime();
            if (remaining > 0) state.task.get(remaining, TimeUnit.NANOSECONDS);
            synchronized (this) {
                if (closed || cache.get(key) != state) return null;
                if (System.nanoTime() - state.lookupDeadline >= 0) {
                    cache.remove(key);
                    cancel(state);
                    return null;
                }
                return state.entries;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // The shared timer owns cancellation; one interrupted waiter must not cancel others.
            return null;
        }
    }

    private void load(String key, byte[] pub, CachedChain state) {
        List<KarmaChainEntry> entries = null;
        try {
            synchronized (this) {
                if (closed || cache.get(key) != state || state.task.isCancelled()
                        || System.nanoTime() - state.lookupDeadline >= 0) return;
            }
            Entry manifest = source instanceof DhtKarmaChainSource
                    ? ((DhtKarmaChainSource) source).fetchManifest(pub, state.lookupDeadline)
                    : source.fetchManifest(pub);
            if (manifest != null && !state.task.isCancelled()
                    && System.nanoTime() - state.lookupDeadline < 0) entries = parseAndVerify(manifest, pub);
        } catch (Throwable t) {
            LOG.debug("Remote karma fetch failed for peer " + key, t);
        } finally {
            synchronized (this) {
                if (!closed && cache.get(key) == state && !state.task.isCancelled()) {
                    state.entries = System.nanoTime() - state.lookupDeadline < 0 ? entries : null;
                    state.expiresAt = clock.getAsLong() + ttlNanos;
                    state.complete = true;
                    state.expiry.cancel(false);
                }
            }
        }
    }

    /**
     * Drop a peer from the cache. Forces the next fetchChain call
     * to re-query the source.
     */
    public synchronized void evict(byte[] peerPub) {
        if (peerPub == null) {
            return;
        }
        String key = cacheKey(peerPub);
        CachedChain removed = cache.remove(key);
        if (removed != null) cancel(removed);
    }

    /** Clear all cached chains. */
    public synchronized void clear() {
        for (CachedChain state : cache.values()) cancel(state);
        cache.clear();
    }

    /** Number of peers with verified chains in the cache. */
    public synchronized int cacheSize() {
        prune();
        int count = 0;
        for (CachedChain state : cache.values()) if (state.entries != null) count++;
        return count;
    }

    public synchronized int retainedPeerCount() {
        prune();
        return cache.size();
    }

    synchronized boolean isCached(byte[] pub) {
        CachedChain state = cached(cacheKey(pub));
        return state != null && state.complete;
    }

    private CachedChain cached(String key) {
        CachedChain state = cache.get(key);
        if (state != null && (state.complete ? clock.getAsLong() - state.expiresAt >= 0
                : System.nanoTime() - state.lookupDeadline >= 0)) {
            cancel(state);
            cache.remove(key);
            return null;
        }
        return state;
    }

    @Override
    public synchronized void close() {
        closed = true;
        clear();
    }

    private void prune() {
        long now = clock.getAsLong();
        Iterator<CachedChain> it = cache.values().iterator();
        while (it.hasNext()) {
            CachedChain state = it.next();
            if (state.complete && now - state.expiresAt >= 0) {
                it.remove();
            } else if (!state.complete && System.nanoTime() - state.lookupDeadline >= 0) {
                cancel(state);
                it.remove();
            }
        }
    }

    private void cancel(CachedChain state) {
        state.task.cancel(true);
        workers.remove(state.task);
        if (state.expiry != null) state.expiry.cancel(false);
    }

    private static final class CachedChain {
        volatile List<KarmaChainEntry> entries;
        FutureTask<Void> task;
        ScheduledFuture<?> expiry;
        long expiresAt;
        long lookupDeadline;
        boolean complete;
    }

    private List<KarmaChainEntry> parseAndVerify(Entry manifest, byte[] owner) {
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
        if (entryDicts.size() > 256 || manifest.bencode().length > 64 * 1024) return null;
        List<KarmaChainEntry> chain = new ArrayList<>(entryDicts.size());
        for (Entry e : entryDicts) {
            Map<String, Entry> entryDict;
            try {
                entryDict = e.dictionary();
            } catch (Throwable t) {
                return null;
            }
            KarmaChainEntry entry = KarmaChainEntry.reconstruct(entryDict, owner);
            if (entry == null || !Arrays.equals(owner, entry.endorserPub())) {
                return null;
            }
            chain.add(entry);
        }
        Entry baseEntry = dict.get("base");
        if (baseEntry != null) {
            Map<String, Entry> baseDict;
            try {
                baseDict = baseEntry.dictionary();
            } catch (Throwable t) {
                return null;
            }
            Entry seqEntry = baseDict.get("seq");
            Entry phEntry = baseDict.get("ph");
            if (seqEntry == null || phEntry == null) {
                return null;
            }
            byte[] basePrevHash;
            long baseSeq;
            try {
                basePrevHash = com.frostwire.util.Hex.decode(phEntry.string());
                baseSeq = seqEntry.integer();
            } catch (Throwable t) {
                return null;
            }
            if (!KarmaChain.verifyTail(chain, basePrevHash, baseSeq)) {
                return null;
            }
        } else if (!KarmaChain.verify(chain)) {
            return null;
        }
        return Collections.unmodifiableList(chain);
    }

    private static String cacheKey(byte[] peerPub) {
        return com.frostwire.util.Hex.encode(peerPub);
    }
}
