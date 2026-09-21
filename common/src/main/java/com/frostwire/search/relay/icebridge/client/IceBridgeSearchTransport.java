/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient.InboundMessage;
import com.frostwire.util.Logger;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final int POLL_BATCH_SIZE = 256;
    private static final int MAX_DRAIN_BATCHES = 16;
    private static final int MAX_REQUEST_BYTES = 16 * 1024;
    private static final int MAX_QUEUED_REQUESTS = 64;
    /** Bounded lane for delivering responses/control frames off the poller thread. */
    private static final int MAX_QUEUED_DELIVERIES = 512;

    private final IceBridgeClient client;
    private final CopyOnWriteArrayList<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    /**
     * Bounded lane for inbound request work (index query, signing, forwards). Deliberately small
     * with {@link ThreadPoolExecutor.AbortPolicy}: dropping unadmitted inbound search work is the
     * amplification control, not a bug. Saturation is observable via the {@code requestWorkRejected}
     * and {@code requestWorkQueueDepth} metrics. Workers never run on the poller thread.
     */
    private final ThreadPoolExecutor requestWorkers;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ThreadPoolExecutor deliveryWorkers;
    private volatile IceBridgeMetrics metrics;
    private final Set<SendOperation> activeSends = new HashSet<>();
    private volatile boolean closed;

    public IceBridgeSearchTransport(IceBridgeClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }
        this.client = client;
        this.requestWorkers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS), r -> {
                    Thread t = new Thread(r, "icebridge-request-worker");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.deliveryWorkers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_DELIVERIES), r -> {
                    Thread t = new Thread(r, "icebridge-delivery-worker");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-transport-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /** Control-plane client (for host refresh / mesh TELEMETRY warm). */
    public IceBridgeClient client() {
        return client;
    }

    /**
     * Attach the owning server's metrics so pipeline saturation (poll lag, drain volume, request
     * queue depth, rejections) is visible on {@code /metrics}. Optional; null disables recording.
     */
    public void setMetrics(IceBridgeMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Start the background poller thread.
     */
    public synchronized void start() {
        if (closed || !started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch,
                POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> {
            for (PayloadListener listener : listeners) {
                if (listener instanceof IncomingSearchRequestHandler) {
                    ((IncomingSearchRequestHandler) listener).evictIdle();
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
        LOG.info("IceBridgeSearchTransport poller started");
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
        if (closed || targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            return false;
        }
        return createSend(targetPub, protocolId, payload, System.nanoTime() + 30_000_000_000L).execute();
    }

    @Override
    public SendOperation createSend(byte[] targetPub, int protocolId, byte[] payload, long deadlineNanos) {
        SendOperation delegate = client.createSend(targetPub, protocolId, payload, deadlineNanos);
        return new SendOperation() {
            private final AtomicBoolean executed = new AtomicBoolean();

            @Override
            public boolean execute() {
                if (!executed.compareAndSet(false, true)) {
                    return false;
                }
                synchronized (activeSends) {
                    if (closed || activeSends.size() >= 64 || System.nanoTime() - deadlineNanos >= 0) {
                        delegate.cancel();
                        return false;
                    }
                    activeSends.add(delegate);
                }
                try {
                    return delegate.execute();
                } finally {
                    synchronized (activeSends) {
                        activeSends.remove(delegate);
                    }
                }
            }

            @Override
            public void cancel() {
                delegate.cancel();
            }
        };
    }

    @Override
    public synchronized void addListener(PayloadListener listener) {
        if (!closed && listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    @Override
    public void removeListener(PayloadListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
        for (PayloadListener listener : listeners) {
            if (listener instanceof IncomingSearchRequestHandler) {
                ((IncomingSearchRequestHandler) listener).stop();
            }
        }
        requestWorkers.shutdownNow();
        deliveryWorkers.shutdownNow();
        synchronized (activeSends) {
            for (SendOperation operation : activeSends) {
                operation.cancel();
            }
        }
        listeners.clear();
        LOG.info("IceBridgeSearchTransport poller stopped");
    }

    private void pollAndDispatch() {
        IceBridgeMetrics m = metrics;
        if (m != null) {
            m.incrementTransportPollRuns(1);
        }
        try {
            for (int batch = 0; batch < MAX_DRAIN_BATCHES && !closed; batch++) {
                List<InboundMessage> messages = client.poll(POLL_BATCH_SIZE);
                if (m != null) {
                    m.incrementTransportPollDrainBatches(1);
                    m.incrementTransportMessagesDrained(messages.size());
                }
                for (InboundMessage msg : messages) {
                    if (closed) {
                        return;
                    }
                    int protocolId = msg.protocolId() == 0 ? MeshProtocolId.SEARCH : msg.protocolId();
                    byte[] payload = msg.payload();
                    byte[] source = msg.sourcePub();
                    boolean request = isRequest(payload, protocolId);
                    for (PayloadListener listener : listeners) {
                        try {
                            if (listener instanceof IncomingSearchRequestHandler) {
                                if (!request) {
                                    continue;
                                }
                                // SQL/JNI, signing and sends cannot hold the response demultiplexer.
                                IncomingSearchRequestHandler handler = (IncomingSearchRequestHandler) listener;
                                long generation = handler.generation();
                                long queuedAt = System.nanoTime();
                                requestWorkers.execute(() -> {
                                    if (!closed && listeners.contains(listener)
                                            && System.nanoTime() - queuedAt < 30_000_000_000L) {
                                        handler.onPayloadBefore(source, payload, protocolId,
                                                queuedAt + 30_000_000_000L, generation);
                                    }
                                });
                                continue;
                            }
                            // Responses and control frames go out on a bounded lane so a slow
                            // listener cannot stall polling for everyone (head-of-line blocking).
                            deliverOffPoller(listener, msg, protocolId);
                        } catch (RejectedExecutionException e) {
                            if (m != null) {
                                m.incrementRequestWorkRejected(1);
                            }
                            LOG.debug("Incoming request queue full; dropping unadmitted work");
                        } catch (Throwable t) {
                            LOG.warn("Payload listener threw", t);
                        }
                    }
                }
                if (messages.size() < POLL_BATCH_SIZE) {
                    break;
                }
            }
        } catch (Throwable t) {
            if (m != null) {
                m.incrementTransportPollErrors(1);
            }
            LOG.warn("IceBridgeSearchTransport poll failed", t);
        } finally {
            if (m != null) {
                m.setRequestWorkQueueDepthGauge(requestWorkers.getQueue().size());
            }
        }
    }

    /**
     * Deliver one non-request frame on the bounded delivery lane. Saturation applies backpressure
     * via {@code CallerRunsPolicy} (runs on the poller) rather than dropping the frame: search and
     * metadata responses are one-shot and are not retransmitted by the holder.
     *
     * <p>Unlike the rUDP fragment ingest lane (which must stay strictly FIFO), reordering here is
     * safe: consumers demultiplex by nonce and reassemble chunked payloads by explicit chunk index,
     * so a frame that overtakes a queued one is still placed correctly.
     */
    private void deliverOffPoller(PayloadListener listener, InboundMessage msg, int protocolId) {
        deliveryWorkers.execute(() -> {
            try {
                listener.onPayload(msg.sourcePub(), msg.payload(), msg.receivedMs(), protocolId);
            } catch (Throwable t) {
                LOG.warn("Payload listener threw", t);
            }
        });
    }

    /** Bounded shape demux only; workers still decode and authenticate every request. */
    private static boolean isRequest(byte[] payload, int protocolId) {
        if (payload.length == 0 || payload.length > MAX_REQUEST_BYTES) {
            return false;
        }
        if (protocolId == MeshProtocolId.INDEX_DIGEST) {
            // Opaque content fingerprint; the handler validates its shape before storing it.
            return true;
        }
        if (protocolId != MeshProtocolId.SEARCH && protocolId != MeshProtocolId.METADATA) {
            return false;
        }
        boolean requester = false;
        boolean query = false;
        try (JsonReader reader = new JsonReader(new StringReader(new String(payload, StandardCharsets.UTF_8)))) {
            reader.beginObject();
            int fields = 0;
            while (reader.hasNext()) {
                if (++fields > 32) {
                    return false;
                }
                String name = reader.nextName();
                requester |= "pub".equals(name);
                query |= protocolId == MeshProtocolId.METADATA ? "ih".equals(name)
                        : "k".equals(name) || "target".equals(name);
                reader.skipValue();
            }
            reader.endObject();
            return requester && query;
        } catch (Exception e) {
            return false;
        }
    }
}
