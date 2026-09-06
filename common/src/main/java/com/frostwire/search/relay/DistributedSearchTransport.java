/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Transport-agnostic interface for sending and receiving opaque payloads
 * between peers on the IceBridge mesh (and any local equivalent).
 *
 * <p>Application protocols (distributed search is Protocol #1) ride as opaque
 * bytes. Prefer {@link #send(byte[], int, byte[])} with an explicit mesh
 * protocol id for multi-protocol demux.
 *
 * <p>Implementations run an internal poller thread that feeds registered
 * listeners; callers never poll directly.
 */
public interface DistributedSearchTransport {

    /**
     * Send an opaque payload with protocol SEARCH (Protocol #1).
     */
    default boolean send(byte[] targetPub, byte[] payload) {
        return send(targetPub, com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, payload);
    }

    /**
     * Send an opaque payload to a peer identified by raw Ed25519 public key.
     *
     * @param targetPub  32-byte Ed25519 public key of the recipient
     * @param protocolId mesh protocol id (see MeshProtocolId)
     * @param payload    opaque application payload
     * @return {@code true} if the payload was accepted for delivery
     */
    boolean send(byte[] targetPub, int protocolId, byte[] payload);

    /**
     * Creates a single-use send with an absolute {@link System#nanoTime()} deadline.
     * The caller owns cancellation. Blocking transports must override this method
     * to cancel their I/O; this fallback only prevents a not-yet-started send.
     */
    default SendOperation createSend(byte[] targetPub, int protocolId, byte[] payload, long deadlineNanos) {
        byte[] target = targetPub.clone();
        byte[] data = payload.clone();
        return new SendOperation() {
            private final java.util.concurrent.atomic.AtomicBoolean started =
                    new java.util.concurrent.atomic.AtomicBoolean();
            private volatile boolean cancelled;

            @Override
            public boolean execute() {
                if (!started.compareAndSet(false, true) || cancelled
                        || Thread.currentThread().isInterrupted()
                        || deadlineNanos - System.nanoTime() <= 0) {
                    return false;
                }
                return send(target, protocolId, data);
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        };
    }

    interface SendOperation {
        /** Returns acceptance for delivery, not proof of remote processing. */
        boolean execute();

        /** Idempotent cancellation; production I/O implementations must unblock execution. */
        void cancel();
    }

    void addListener(PayloadListener listener);

    void removeListener(PayloadListener listener);

    /**
     * Functional callback for inbound payloads.
     *
     * <p>The three-argument form is the SAM for lambdas. Override
     * {@link #onPayload(byte[], byte[], long, int)} when protocol demux matters.
     */
    @FunctionalInterface
    interface PayloadListener {
        /**
         * @param sourcePub  raw Ed25519 public key of the sender
         * @param payload    opaque application payload (envelope demuxed)
         * @param receivedMs epoch millis when the transport received the payload
         */
        void onPayload(byte[] sourcePub, byte[] payload, long receivedMs);

        /**
         * Protocol-aware delivery. Default ignores {@code protocolId} and
         * delegates to {@link #onPayload(byte[], byte[], long)}.
         */
        default void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
            onPayload(sourcePub, payload, receivedMs);
        }
    }
}
