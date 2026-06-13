/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background daemon task that keeps the node visible to other
 * peers on the DHT. Each tick:
 * <ol>
 *   <li>Re-publishes our {@link IdentityRecord} as a BEP 46 mutable
 *       item so peers can fetch our full identity by our Ed25519
 *       pub key.</li>
 *   <li>Announces our peer port under the BEP 5 peer topic so
 *       others doing a {@code dhtGetPeers} discover our TCP/UTP
 *       endpoint.</li>
 * </ol>
 *
 * <p>The interval is
 * {@link RelayConstants#IDENTITY_REPUBLISH_INTERVAL_SEC} which
 * is also used as the throttle inside
 * {@link IdentityRecordPublisher#publishIfNeeded}. The
 * announcement is idempotent so re-running on every tick is
 * safe.
 *
 * <p>Daemon-threaded, no explicit shutdown hook needed.
 */
public final class DhtAdvertiser {

    private static final Logger LOG = Logger.getLogger(DhtAdvertiser.class);

    private static final String THREAD_NAME = "dht-advertiser";

    private final IdentityRecordPublisher identityPublisher;
    private final long intervalSec;
    private final AtomicLong lastTickEpochSec = new AtomicLong();
    private final AtomicLong identityPublishes = new AtomicLong();
    private final AtomicLong announceCalls = new AtomicLong();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private volatile boolean running;

    public DhtAdvertiser(IdentityRecordPublisher identityPublisher, long intervalSec) {
        if (identityPublisher == null) {
            throw new IllegalArgumentException("identityPublisher is null");
        }
        if (intervalSec <= 0) {
            throw new IllegalArgumentException("intervalSec must be > 0");
        }
        this.identityPublisher = identityPublisher;
        this.intervalSec = intervalSec;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor = ExecutorsHelper.newScheduledThreadPool(1, THREAD_NAME);
        task = executor.scheduleAtFixedRate(this::scheduledTick, 0, intervalSec,
                TimeUnit.SECONDS);
        LOG.info("DhtAdvertiser started, interval=" + intervalSec + "s");
    }

    /**
     * The scheduled task entry point. Resolves the SessionManager
     * from {@link BTEngine#getInstance()} and delegates. If the
     * engine is not yet running, the tick is a no-op.
     */
    private void scheduledTick() {
        SessionManager session = BTEngine.getInstance();
        if (session == null) {
            return;
        }
        tick(session);
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
            executor = null;
        }
        LOG.info("DhtAdvertiser stopped");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Run a single tick against the given session. The publisher's
     * own throttle decides whether the identity is actually
     * re-sent; the BEP 5 announcement is always re-issued. Returns
     * true if at least one operation completed.
     */
    public boolean tick(SessionManager session) {
        if (session == null) {
            return false;
        }
        try {
            int published = identityPublisher.publishIfNeeded(session);
            if (published > 0) {
                identityPublishes.incrementAndGet();
            }
            DhtRendezvous.announcePeer(session, identityPublisher.utpPort());
            announceCalls.incrementAndGet();
            lastTickEpochSec.set(System.currentTimeMillis() / 1000L);
            return true;
        } catch (Throwable t) {
            LOG.warn("DhtAdvertiser tick failed", t);
            return false;
        }
    }

    public long lastTickEpochSec() {
        return lastTickEpochSec.get();
    }

    public long identityPublishCount() {
        return identityPublishes.get();
    }

    public long announceCount() {
        return announceCalls.get();
    }
}
