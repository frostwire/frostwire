/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.util.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background task that keeps the karma chain advancing and visible
 * to other peers even when no downloads are happening.
 *
 * <p>On each tick the scheduler:
 * <ol>
 *   <li>Calls {@link KarmaChainWriter#commitEpochIfNeeded()} so the
 *       chain crosses epoch boundaries without waiting for a
 *       download event.</li>
 *   <li>Calls {@link KarmaChainPublisher#publishIfNeeded(com.frostwire.jlibtorrent.SessionManager)}
 *       so peers can fetch the latest chain tail from the DHT.</li>
 * </ol>
 *
 * <p>Both steps fail-closed: any error is logged and swallowed.
 * The chain never blocks on transient network or DHT failures.
 *
 * <p>The scheduler uses a daemon {@link ScheduledExecutorService}
 * from {@link ExecutorsHelper} so it does not prevent JVM exit.
 *
 * <p><b>Thread-safety:</b> the scheduler's tasks run on the executor
 * thread, which is independent of the BTEngine thread that fires
 * download completions. Both paths call the writer, which serializes
 * its own mutations through an internal lock.
 */
public final class KarmaChainCommitScheduler {

    private static final Logger LOG = Logger.getLogger(KarmaChainCommitScheduler.class);

    private static final String THREAD_NAME = "karma-commit-scheduler";

    private final KarmaChainWriter writer;
    private final KarmaChainPublisher publisher;
    private final long intervalSec;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    public KarmaChainCommitScheduler(KarmaChainWriter writer,
                                     KarmaChainPublisher publisher,
                                     long intervalSec) {
        if (writer == null) {
            throw new IllegalArgumentException("writer is null");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher is null");
        }
        if (intervalSec <= 0) {
            throw new IllegalArgumentException("intervalSec must be > 0");
        }
        this.writer = writer;
        this.publisher = publisher;
        this.intervalSec = intervalSec;
    }

    /**
     * Start the periodic commit-and-publish task. No-op if already
     * started.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = ExecutorsHelper.newScheduledThreadPool(1, THREAD_NAME);
        task = executor.scheduleAtFixedRate(this::tick, intervalSec, intervalSec, TimeUnit.SECONDS);
        LOG.info("Karma commit scheduler started, interval=" + intervalSec + "s");
    }

    /**
     * Stop the periodic task. Safe to call multiple times. Does not
     * interrupt an in-flight tick (lets the current Bitcoin fetch
     * finish so the executor can shut down cleanly).
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
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
        LOG.info("Karma commit scheduler stopped");
    }

    /** True between a successful start and stop. */
    public boolean isRunning() {
        return running.get();
    }

    private void tick() {
        try {
            writer.commitEpochIfNeeded();
        } catch (Throwable t) {
            LOG.warn("Karma commit tick: commitEpochIfNeeded failed", t);
        }
        try {
            com.frostwire.bittorrent.BTEngine engine = com.frostwire.bittorrent.BTEngine.getInstance();
            if (engine != null) {
                publisher.publishIfNeeded(engine);
            }
        } catch (Throwable t) {
            LOG.warn("Karma commit tick: publishIfNeeded failed", t);
        }
    }
}
