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
public final class DhtAdvertiser {

    private static final Logger LOG = Logger.getLogger(DhtAdvertiser.class);

    private static final String THREAD_NAME = "dht-advertiser";

    private final IdentityRecordPublisher identityPublisher;
    private final IndexAnnouncementPublisher indexPublisher;
    private final long intervalSec;
    private final Supplier<SessionManager> sessionSupplier;
    private final boolean announcePeerTopic;
    private final boolean announceBootstrapTopic;
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

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor = ExecutorsHelper.newScheduledThreadPool(1, THREAD_NAME);
        task = executor.scheduleAtFixedRate(this::scheduledTick, 0, intervalSec,
                TimeUnit.SECONDS);
        LOG.info("DhtAdvertiser started, interval=" + intervalSec + "s"
                + " peerTopic=" + announcePeerTopic
                + " bootstrapTopic=" + announceBootstrapTopic);
    }

    private void scheduledTick() {
        SessionManager session;
        try {
            session = sessionSupplier.get();
        } catch (Throwable t) {
            LOG.debug("DhtAdvertiser session supplier failed", t);
            return;
        }
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
     * Run a single tick against the given session. Returns true if at least
     * one operation completed without throwing.
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
            if (indexPublisher != null) {
                int rows = indexPublisher.publishIfNeeded(session);
                if (rows > 0) {
                    indexPublishes.incrementAndGet();
                }
            }
            int announcePort = identityPublisher.utpPort();
            if (announcePeerTopic) {
                DhtRendezvous.announcePeer(session, announcePort);
            }
            String role = identityPublisher.role();
            boolean connectable = ConnectivityDetector.instance().isConnectable();
            if ("FORWARDER".equals(role) || "BOTH".equals(role)
                    || (connectable && "CLIENT".equals(role))) {
                DhtRendezvous.announceRelay(session, announcePort);
                if (connectable && "CLIENT".equals(role)) {
                    LOG.info("DhtAdvertiser: auto-electing as forwarder (connectable, was CLIENT)");
                }
            }
            if (announceBootstrapTopic) {
                DhtRendezvous.announceBootstrap(session, announcePort);
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
}
