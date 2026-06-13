/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background daemon task that periodically runs BEP 5 peer
 * discovery via a {@link PeerDiscovery} and registers newly
 * discovered endpoints in the local {@link PeerDirectory}.
 *
 * <p>Daemon-threaded, no explicit shutdown hook needed.
 */
public final class PeerDiscoveryScheduler {

    private static final Logger LOG = Logger.getLogger(PeerDiscoveryScheduler.class);

    private static final String THREAD_NAME = "peer-discovery";

    private final PeerDiscovery discovery;
    private final long intervalSec;
    private final AtomicLong totalDiscovered = new AtomicLong();
    private final AtomicLong totalTicks = new AtomicLong();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private volatile boolean running;

    public PeerDiscoveryScheduler(PeerDiscovery discovery, long intervalSec) {
        if (discovery == null) {
            throw new IllegalArgumentException("discovery is null");
        }
        if (intervalSec <= 0) {
            throw new IllegalArgumentException("intervalSec must be > 0");
        }
        this.discovery = discovery;
        this.intervalSec = intervalSec;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor = ExecutorsHelper.newScheduledThreadPool(1, THREAD_NAME);
        task = executor.scheduleAtFixedRate(this::tick,
                0, intervalSec, TimeUnit.SECONDS);
        LOG.info("PeerDiscoveryScheduler started, interval=" + intervalSec + "s");
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
        LOG.info("PeerDiscoveryScheduler stopped");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Run one discovery pass. Returns the list of newly
     * discovered endpoints.
     */
    public List<DiscoveredEndpoint> tick() {
        try {
            totalTicks.incrementAndGet();
            List<DiscoveredEndpoint> discovered = discovery.discoverAndRegister();
            if (!discovered.isEmpty()) {
                totalDiscovered.addAndGet(discovered.size());
                LOG.info("Discovered " + discovered.size() + " new peer(s)");
            }
            return discovered;
        } catch (Throwable t) {
            LOG.warn("PeerDiscoveryScheduler tick failed", t);
            return List.of();
        }
    }

    public long totalDiscovered() {
        return totalDiscovered.get();
    }

    public long totalTicks() {
        return totalTicks.get();
    }
}
