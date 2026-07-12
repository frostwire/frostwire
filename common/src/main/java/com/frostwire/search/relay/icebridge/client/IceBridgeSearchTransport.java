/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient.InboundMessage;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bridges the IceBridge HTTP control API to the {@link DistributedSearchTransport}
 * interface used by application protocols (first consumer: distributed search).
 *
 * <p>Runs a single daemon poller thread that periodically calls
 * {@link IceBridgeClient#poll(int)} and dispatches every received payload to
 * all registered {@link PayloadListener} instances.
 */
public final class IceBridgeSearchTransport implements DistributedSearchTransport, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeSearchTransport.class);
    private static final long POLL_INTERVAL_MS = 300;

    private final IceBridgeClient client;
    private final CopyOnWriteArrayList<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    public IceBridgeSearchTransport(IceBridgeClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }
        this.client = client;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-transport-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the background poller thread.
     */
    public void start() {
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch,
                POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOG.info("IceBridgeSearchTransport poller started");
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
        return client.send(targetPub, protocolId, payload);
    }

    @Override
    public void addListener(PayloadListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(PayloadListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        listeners.clear();
        LOG.info("IceBridgeSearchTransport poller stopped");
    }

    private void pollAndDispatch() {
        try {
            List<InboundMessage> messages = client.poll(64);
            for (InboundMessage msg : messages) {
                int protocolId = msg.protocolId() == 0 ? MeshProtocolId.SEARCH : msg.protocolId();
                for (PayloadListener listener : listeners) {
                    try {
                        listener.onPayload(msg.sourcePub(), msg.payload(), msg.receivedMs(), protocolId);
                    } catch (Throwable t) {
                        LOG.warn("Payload listener threw", t);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("IceBridgeSearchTransport poll failed", t);
        }
    }
}
