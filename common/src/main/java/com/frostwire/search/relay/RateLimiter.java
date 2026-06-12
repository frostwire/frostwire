/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.util.concurrent.ConcurrentHashMap;
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
 * touched in {@code idleMs} milliseconds to bound memory.
 *
 * <p>Thread-safe: backed by a {@link ConcurrentHashMap}; bucket
 * state mutations are atomic via CAS.
 */
public final class RateLimiter {

    private static final Logger LOG = Logger.getLogger(RateLimiter.class);

    private final double capacity;
    private final double refillPerSec;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong totalAllowed = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();

    public RateLimiter(double capacity, double refillPerSec) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillPerSec <= 0) {
            throw new IllegalArgumentException("refillPerSec must be > 0");
        }
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
    }

    /**
     * Try to consume 1 token for {@code peerPub}. Returns true if
     * allowed, false if rate-limited.
     */
    public boolean tryAcquire(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return false;
        }
        String key = com.frostwire.util.Hex.encode(peerPub);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        long now = System.currentTimeMillis();
        boolean allowed = bucket.tryConsume(now, capacity, refillPerSec);
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
    public int evictIdle(long idleMs) {
        if (idleMs < 0) {
            throw new IllegalArgumentException("idleMs must be >= 0");
        }
        long now = System.currentTimeMillis();
        int removed = 0;
        for (java.util.Map.Entry<String, Bucket> e : buckets.entrySet()) {
            Bucket b = e.getValue();
            if (now - b.lastSeenMs() > idleMs) {
                if (buckets.remove(e.getKey(), b)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public int bucketCount() {
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

        Bucket(double initial) {
            this.tokens = initial;
            this.lastSeenMs = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(long now, double capacity, double refillPerSec) {
            double elapsedSec = Math.max(0, (now - lastSeenMs)) / 1000.0;
            lastSeenMs = now;
            // Refill based on elapsed time
            this.tokens = Math.min(capacity, this.tokens + elapsedSec * refillPerSec);
            if (this.tokens >= 1.0) {
                this.tokens -= 1.0;
                return true;
            }
            return false;
        }

        long lastSeenMs() {
            return lastSeenMs;
        }
    }
}
