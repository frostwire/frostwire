/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.util.Logger;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Background daemon task that keeps the node visible on the DHT. Each tick:
 * <ol>
 *   <li>Re-publishes our {@link IdentityRecord} as a BEP 46 mutable item.</li>
 *   <li>Optionally announces under the BEP 5 peer topic.</li>
 *   <li>Announces under the BEP 5 relay topic when role is FORWARDER/BOTH
 *       (or CLIENT auto-elected as connectable).</li>
 *   <li>Optionally announces under the BEP 5 bootstrap topic.</li>
 * </ol>
 *
 * <p>Session resolution is pluggable via {@link Supplier}{@code <SessionManager>} so
 * desktop can use {@link BTEngine} while standalone IceBridge uses an embedded
 * DHT-only session. A null session makes the tick a no-op.
 */
public final class DhtAdvertiser implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DhtAdvertiser.class);

    private static final String THREAD_NAME = "dht-advertiser";

    private final IdentityRecordPublisher identityPublisher;
    private final IndexAnnouncementPublisher indexPublisher;
    private final long intervalSec;
    private final Supplier<SessionManager> sessionSupplier;
    private final boolean announcePeerTopic;
    private final boolean announceBootstrapTopic;
    private final Lifecycle lifecycle;
    private final AtomicLong lastTickEpochSec = new AtomicLong();
    private final AtomicLong identityPublishes = new AtomicLong();
    private final AtomicLong indexPublishes = new AtomicLong();
    private final AtomicLong announceCalls = new AtomicLong();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private volatile boolean running;

    public DhtAdvertiser(IdentityRecordPublisher identityPublisher, long intervalSec) {
        this(identityPublisher, null, intervalSec);
    }

    public DhtAdvertiser(IdentityRecordPublisher identityPublisher,
                         IndexAnnouncementPublisher indexPublisher,
                         long intervalSec) {
        this(identityPublisher, indexPublisher, intervalSec, null, true, false);
    }

    /**
     * @param sessionSupplier           resolves the DHT session each tick; null means
     *                                  {@link BTEngine#getInstance()} when available
     * @param announcePeerTopic         announce under {@code frostwire-peers-v1}
     * @param announceBootstrapTopic    announce under {@code frostwire-bootstrap-v1}
     */
    public DhtAdvertiser(IdentityRecordPublisher identityPublisher,
                         IndexAnnouncementPublisher indexPublisher,
                         long intervalSec,
                         Supplier<SessionManager> sessionSupplier,
                         boolean announcePeerTopic,
                         boolean announceBootstrapTopic) {
        this(identityPublisher, indexPublisher, intervalSec, sessionSupplier,
                announcePeerTopic, announceBootstrapTopic, () -> true);
    }

    /**
     * The permission predicate must be nonblocking and bound to this identity generation.
     * False (or a predicate failure) permanently revokes this instance.
     */
    public DhtAdvertiser(IdentityRecordPublisher identityPublisher,
                         IndexAnnouncementPublisher indexPublisher,
                         long intervalSec,
                         Supplier<SessionManager> sessionSupplier,
                         boolean announcePeerTopic,
                         boolean announceBootstrapTopic,
                         BooleanSupplier permitted) {
        if (identityPublisher == null) {
            throw new IllegalArgumentException("identityPublisher is null");
        }
        if (intervalSec <= 0) {
            throw new IllegalArgumentException("intervalSec must be > 0");
        }
        this.identityPublisher = identityPublisher;
        this.indexPublisher = indexPublisher;
        this.intervalSec = intervalSec;
        this.sessionSupplier = sessionSupplier != null
                ? sessionSupplier
                : DhtAdvertiser::defaultBtEngineSession;
        this.announcePeerTopic = announcePeerTopic;
        this.announceBootstrapTopic = announceBootstrapTopic;
        this.lifecycle = new Lifecycle(permitted);
    }

    private static SessionManager defaultBtEngineSession() {
        try {
            // Avoid blocking forever on BTEngine's setup latch when no app
            // context exists (unit tests, standalone paths without BTEngine).
            if (BTEngine.ctx == null) {
                return null;
            }
            return BTEngine.getInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Start once; a stopped identity-bound instance cannot be restarted. */
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
            task = executor.scheduleAtFixedRate(this::scheduledTick, 0, intervalSec,
                    TimeUnit.SECONDS);
        }
        LOG.info("DhtAdvertiser started, interval=" + intervalSec + "s"
                + " peerTopic=" + announcePeerTopic
                + " bootstrapTopic=" + announceBootstrapTopic);
    }

    private void scheduledTick() {
        if (!lifecycle.enter()) {
            stop();
            return;
        }
        try {
            SessionManager session = sessionSupplier.get();
            tickStages(session);
        } catch (Throwable t) {
            LOG.debug("DhtAdvertiser session supplier failed", t);
        } finally {
            lifecycle.leave();
            if (lifecycle.isClosed()) {
                stop();
            }
        }
    }

    /**
     * Permanently revoke this advertiser without waiting for JNI/provider work.
     * An already admitted stage may finish; no later tick stage is admitted.
     * Create a new instance for a new generation. Borrowed sessions are not closed.
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

    /**
     * Worker-only bounded drain, including direct {@link #tick} calls. Call stop first.
     * False means borrowed resources may still be in use. This drains Java calls,
     * not already submitted native DHT operations, and does not interrupt JNI.
     */
    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return lifecycle.awaitStopped(timeout, unit);
    }

    public boolean isRunning() {
        return running && !lifecycle.isClosed();
    }

    /**
     * Run one tick on the calling worker, also allowed before start but never after stop.
     * Returns true only if all enabled stages finish without revocation or error.
     */
    public boolean tick(SessionManager session) {
        if (!lifecycle.enter()) {
            return false;
        }
        try {
            return tickStages(session);
        } finally {
            lifecycle.leave();
        }
    }

    private boolean tickStages(SessionManager session) {
        try {
            if (session == null || !lifecycle.getAsBoolean()) {
                return false;
            }
            session = new PublicationSession(session, lifecycle);
            int published = identityPublisher.publishIfNeeded(session);
            if (published > 0) {
                identityPublishes.incrementAndGet();
            }
            if (!lifecycle.getAsBoolean()) {
                return false;
            }
            if (indexPublisher != null) {
                int rows = indexPublisher.publishIfNeeded(session);
                if (rows > 0) {
                    indexPublishes.incrementAndGet();
                }
            }
            int announcePort = identityPublisher.utpPort();
            if (!lifecycle.getAsBoolean()) {
                return false;
            }
            if (announcePeerTopic) {
                DhtRendezvous.announcePeer(session, announcePort);
            }
            String role = identityPublisher.role();
            boolean connectable = ConnectivityDetector.instance().isConnectable();
            if (!lifecycle.getAsBoolean()) {
                return false;
            }
            if ("FORWARDER".equals(role) || "BOTH".equals(role)
                    || (connectable && "CLIENT".equals(role))) {
                DhtRendezvous.announceRelay(session, announcePort);
                if (connectable && "CLIENT".equals(role)) {
                    LOG.info("DhtAdvertiser: auto-electing as forwarder (connectable, was CLIENT)");
                }
            }
            if (!lifecycle.getAsBoolean()) {
                return false;
            }
            if (announceBootstrapTopic) {
                DhtRendezvous.announceBootstrap(session, announcePort);
            }
            if (!lifecycle.getAsBoolean()) {
                return false;
            }
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

    public long indexPublishCount() {
        return indexPublishes.get();
    }

    public long announceCount() {
        return announceCalls.get();
    }

    /**
     * Narrow adapter for the existing publisher APIs, not a new native session.
     * In jlibtorrent 2.0.12.9 construction starts no session or alert thread. Guard
     * at the actual put/announce boundary as publishers can block while building
     * a manifest. The delegated mutable put owns its native callback; it returns
     * no cancellation handle, so accepted native work cannot be revoked here.
     */
    static final class PublicationSession extends SessionManager {
        private final SessionManager delegate;
        private final BooleanSupplier permitted;

        PublicationSession(SessionManager delegate, BooleanSupplier permitted) {
            this.delegate = delegate;
            this.permitted = permitted;
        }

        @Override
        public void dhtPutItem(byte[] publicKey, byte[] privateKey, Entry entry, byte[] salt) {
            if (!permitted.getAsBoolean()) {
                // Do not let publishers mark an unsubmitted manifest as published.
                throw new CancellationException("Publication generation stopped");
            }
            delegate.dhtPutItem(publicKey, privateKey, entry, salt);
        }

        @Override
        public void dhtAnnounce(Sha1Hash topic, int port, int flags) {
            if (!permitted.getAsBoolean()) {
                throw new CancellationException("Publication generation stopped");
            }
            delegate.dhtAnnounce(topic, port, flags);
        }
    }

    /** Shared admission/drain state for identity-bound relay workers; never locks over providers. */
    static final class Lifecycle implements BooleanSupplier {
        private final BooleanSupplier permitted;
        private volatile boolean closed;
        private int active;

        Lifecycle(BooleanSupplier permitted) {
            if (permitted == null) {
                throw new IllegalArgumentException("permitted is null");
            }
            this.permitted = permitted;
        }

        @Override
        public boolean getAsBoolean() {
            if (closed) {
                return false;
            }
            try {
                if (permitted.getAsBoolean()) {
                    return !closed;
                }
            } catch (Throwable t) {
                LOG.debug("Relay generation permission failed", t);
            }
            close();
            return false;
        }

        boolean enter() {
            if (!getAsBoolean()) {
                return false;
            }
            synchronized (this) {
                if (closed) {
                    return false;
                }
                active++;
                return true;
            }
        }

        synchronized void leave() {
            active--;
            notifyAll();
        }

        boolean isClosed() {
            return closed;
        }

        synchronized void close() {
            closed = true;
            notifyAll();
        }

        synchronized boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout must be >= 0");
            }
            long remaining = unit.toNanos(timeout);
            long start = System.nanoTime();
            while (!closed || active != 0) {
                if (remaining <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
                remaining = unit.toNanos(timeout) - (System.nanoTime() - start);
            }
            return true;
        }
    }
}
