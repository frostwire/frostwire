/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-peer token-bucket rate limiter. Each peer has its own
 * independent bucket; the global cost is O(1) memory per peer
 * plus a periodic sweep to remove idle buckets.
 *
 * <p>Algorithm: each bucket holds at most {@code capacity} tokens.
 * Tokens refill at {@code refillPerSec} per second. Each
 * {@link #tryAcquire(byte[])} consumes 1 token. If the bucket
 * is empty, the call returns false.
 *
 * <p>The bucket "last seen" timestamp is updated on every
 * successful or failed acquisition. A periodic sweep (call
 * {@link #evictIdle(long)}) removes buckets that haven't been
 * touched in {@code idleMs} milliseconds. Admission also expires idle keys
 * and rejects new keys at the hard cardinality limit, without evicting quotas.
 *
 * <p>Thread-safe: admission and eviction share a short, I/O-free lock.
 */
public final class RateLimiter {

    public static final int DEFAULT_MAX_BUCKETS = 4096;
    private static final long DEFAULT_IDLE_MS = 10 * 60_000L;

    private final double capacity;
    private final double refillPerSec;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final int maxBuckets;
    private final long idleMs;
    private long nextCleanupMs = System.nanoTime() / 1_000_000L;
    private final AtomicLong totalAllowed = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();

    public RateLimiter(double capacity, double refillPerSec) {
        this(capacity, refillPerSec, DEFAULT_MAX_BUCKETS, DEFAULT_IDLE_MS);
    }

    public RateLimiter(double capacity, double refillPerSec, int maxBuckets, long idleMs) {
        if (!Double.isFinite(capacity) || capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (!Double.isFinite(refillPerSec) || refillPerSec <= 0) {
            throw new IllegalArgumentException("refillPerSec must be > 0");
        }
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        if (maxBuckets <= 0 || idleMs <= 0) {
            throw new IllegalArgumentException("maxBuckets and idleMs must be > 0");
        }
        this.maxBuckets = maxBuckets;
        this.idleMs = idleMs;
    }

    /**
     * Try to consume 1 token for {@code peerPub}. Returns true if
     * allowed, false if rate-limited.
     */
    public boolean tryAcquire(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return false;
        }
        return tryAcquire(com.frostwire.util.Hex.encode(peerPub));
    }

    /**
     * Try to consume 1 token for an already-string key (e.g. a sender IP
     * literal). Buckets are keyed by String internally, so this avoids the
     * byte[]-encode round trip. Invalid keys and exhausted cardinality fail closed.
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /** Atomically consume a positive token cost, for example an encoded byte budget. */
    public synchronized boolean tryAcquire(String key, int tokens) {
        if (key == null || key.isEmpty() || key.length() > 256 || tokens <= 0 || tokens > capacity) {
            return false;
        }
        long now = System.nanoTime() / 1_000_000L;
        if (now - nextCleanupMs >= 0) {
            evictIdle(idleMs);
            nextCleanupMs = now + Math.min(idleMs, 60_000L);
        }
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxBuckets) {
                totalRejected.incrementAndGet();
                return false;
            }
            bucket = new Bucket(capacity, now);
            buckets.put(key, bucket);
        }
        boolean allowed = bucket.tryConsume(now, capacity, refillPerSec, tokens);
        if (allowed) {
            totalAllowed.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
        }
        return allowed;
    }

    /**
     * Remove buckets that haven't been touched in {@code idleMs}
     * milliseconds. Returns the number of buckets removed.
     */
    public synchronized int evictIdle(long idleMs) {
        if (idleMs < 0) {
            throw new IllegalArgumentException("idleMs must be >= 0");
        }
        long now = System.nanoTime() / 1_000_000L;
        int removed = 0;
        java.util.Iterator<Bucket> iterator = buckets.values().iterator();
        while (iterator.hasNext()) {
            Bucket b = iterator.next();
            if (now - b.lastSeenMs() > idleMs) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized int bucketCount() {
        return buckets.size();
    }

    public long totalAllowed() {
        return totalAllowed.get();
    }

    public long totalRejected() {
        return totalRejected.get();
    }

    /** Internal bucket state. */
    private static final class Bucket {
        private double tokens;
        private volatile long lastSeenMs;

        Bucket(double initial, long now) {
            this.tokens = initial;
            this.lastSeenMs = now;
        }

        boolean tryConsume(long now, double capacity, double refillPerSec, int cost) {
            double elapsedSec = Math.max(0, (now - lastSeenMs)) / 1000.0;
            lastSeenMs = now;
            // Refill based on elapsed time
            this.tokens = Math.min(capacity, this.tokens + elapsedSec * refillPerSec);
            if (this.tokens >= cost) {
                this.tokens -= cost;
                return true;
            }
            return false;
        }

        long lastSeenMs() {
            return lastSeenMs;
        }
    }
}
