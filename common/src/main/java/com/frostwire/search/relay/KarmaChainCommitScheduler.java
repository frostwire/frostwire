/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
public final class KarmaChainCommitScheduler implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(KarmaChainCommitScheduler.class);

    private static final String THREAD_NAME = "karma-commit-scheduler";

    private final KarmaChainWriter writer;
    private final KarmaChainPublisher publisher;
    private final long intervalSec;
    private final DhtAdvertiser.Lifecycle lifecycle;
    private final Supplier<SessionManager> sessionSupplier;
    private volatile boolean running;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    public KarmaChainCommitScheduler(KarmaChainWriter writer,
                                     KarmaChainPublisher publisher,
                                     long intervalSec) {
        this(writer, publisher, intervalSec, () -> true);
    }

    /** The nonblocking predicate must identify this generation, not general network availability. */
    public KarmaChainCommitScheduler(KarmaChainWriter writer,
                                     KarmaChainPublisher publisher,
                                     long intervalSec,
                                     BooleanSupplier permitted) {
        this(writer, publisher, intervalSec, () -> {
            if (com.frostwire.bittorrent.BTEngine.ctx == null) {
                return null;
            }
            return com.frostwire.bittorrent.BTEngine.getInstance();
        }, permitted);
    }

    /** Resolve the borrowed session on the worker, checking permission again after resolution. */
    public KarmaChainCommitScheduler(KarmaChainWriter writer,
                                     KarmaChainPublisher publisher,
                                     long intervalSec,
                                     Supplier<SessionManager> sessionSupplier,
                                     BooleanSupplier permitted) {
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
        if (sessionSupplier == null) {
            throw new IllegalArgumentException("sessionSupplier is null");
        }
        this.sessionSupplier = sessionSupplier;
        this.lifecycle = new DhtAdvertiser.Lifecycle(permitted);
    }

    /**
     * Start the periodic commit-and-publish task. No-op if already
     * started or permanently stopped. Use a fresh instance for a new generation.
     */
    public void start() {
        if (!lifecycle.getAsBoolean()) {
            return;
        }
        synchronized (lifecycle) {
            if (running || lifecycle.isClosed()) {
                return;
            }
            running = true;
            executor = ExecutorsHelper.newScheduledThreadPool(1, THREAD_NAME);
            task = executor.scheduleAtFixedRate(this::tick, 0, intervalSec, TimeUnit.SECONDS);
        }
        LOG.info("Karma commit scheduler started, interval=" + intervalSec + "s");
    }

    /**
     * Permanently revoke the task without waiting. An admitted provider call may
     * finish, but the writer and later publication stages recheck permission.
     * This does not close the borrowed writer, publisher, store or session.
     */
    public void stop() {
        synchronized (lifecycle) {
            lifecycle.close();
            running = false;
            if (task != null) {
                task.cancel(false);
                task = null;
            }
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    /** Worker-only bounded drain after stop; false means a provider may still be using resources. */
    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return lifecycle.awaitStopped(timeout, unit);
    }

    /** True between a successful start and stop. */
    public boolean isRunning() {
        return running && !lifecycle.isClosed();
    }

    private void tick() {
        if (!lifecycle.enter()) {
            stop();
            return;
        }
        try {
            writer.commitEpochIfNeeded(lifecycle);
            if (!lifecycle.getAsBoolean()) {
                return;
            }
            SessionManager session = sessionSupplier.get();
            if (session != null && lifecycle.getAsBoolean()) {
                publisher.publishIfNeeded(new DhtAdvertiser.PublicationSession(session, lifecycle));
            }
        } catch (Throwable t) {
            LOG.warn("Karma commit tick failed", t);
        } finally {
            lifecycle.leave();
            if (lifecycle.isClosed()) {
                stop();
            }
        }
    }
}
