/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.MeshEnvelope;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.udp.RudpMessageListener;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory queues of application payloads received over rUDP or control-plane
 * delivery for USE_REMOTE clients.
 *
 * <p>Unwraps optional {@link MeshEnvelope} framing so the control API
 * exposes demuxed {@code protocolId} + bare application payload.
 *
 * <p>Multiple USE_REMOTE clients can share one IceBridge control plane. Each
 * registers with this node's rUDP host:port; outbound {@code /send} to those
 * peers is demuxed into a <em>per-target-pub</em> queue so {@code /poll?pub=}
 * only returns that client's messages (avoids race-stealing between desktop
 * and Android on the same forwarder).
 */
public final class InboundMessageQueue implements RudpMessageListener {

    private static final Logger LOG = Logger.getLogger(InboundMessageQueue.class);
    private static final int DEFAULT_MAX_SIZE = 512;
    /** Shared queue for messages without an explicit control-plane target. */
    private static final String SHARED_KEY = "";

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<InboundMessage>> queues =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final int maxSizePerQueue;

    public InboundMessageQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public InboundMessageQueue(int maxSize) {
        this.maxSizePerQueue = Math.max(1, maxSize);
    }

    /**
     * rUDP path: payload arrived for this process (no multi-tenant target).
     * Goes to the shared queue (legacy {@code /poll} without {@code pub=}).
     */
    @Override
    public void onMessage(byte[] sourcePub, byte[] payload) {
        offerUnwrapped(SHARED_KEY, sourcePub, payload);
    }

    /**
     * Control-plane / local-endpoint delivery: message is for a specific
     * registered client (USE_REMOTE peer whose host:port is this process).
     *
     * @param targetPub destination client Ed25519 public key (32 bytes)
     * @param sourcePub sender public key if known, else empty/null
     * @param wireOrAppPayload MeshEnvelope wire bytes or bare payload
     */
    public void offerForTarget(byte[] targetPub, byte[] sourcePub, byte[] wireOrAppPayload) {
        if (targetPub == null || targetPub.length != 32) {
            onMessage(sourcePub, wireOrAppPayload);
            return;
        }
        offerUnwrapped(Hex.encode(targetPub), sourcePub, wireOrAppPayload);
    }

    private void offerUnwrapped(String targetKey, byte[] sourcePub, byte[] payload) {
        int protocolId;
        byte[] appPayload;
        try {
            MeshEnvelope env = MeshEnvelope.unwrap(payload);
            protocolId = env.protocolId();
            appPayload = env.payload();
        } catch (Throwable t) {
            // Bare app payloads (some RELAY local paths) — treat as SEARCH.
            protocolId = MeshProtocolId.SEARCH;
            appPayload = payload;
            if (payload == null || payload.length == 0) {
                LOG.debug("Dropping empty inbound payload");
                return;
            }
        }
        ConcurrentLinkedQueue<InboundMessage> queue =
                queues.computeIfAbsent(targetKey, k -> new ConcurrentLinkedQueue<>());
        AtomicInteger count = counts.computeIfAbsent(targetKey, k -> new AtomicInteger(0));
        while (count.get() >= maxSizePerQueue) {
            if (queue.poll() != null) {
                count.decrementAndGet();
            } else {
                break;
            }
        }
        queue.offer(new InboundMessage(sourcePub, appPayload, System.currentTimeMillis(), protocolId));
        count.incrementAndGet();
        logSuccessfulProtocol(sourcePub, protocolId, appPayload, targetKey);
    }

    private static void logSuccessfulProtocol(byte[] sourcePub, int protocolId, byte[] appPayload,
                                              String targetKey) {
        int id = MeshProtocolId.effective(protocolId);
        if (!MeshProtocolId.isKnown(id)) {
            LOG.info("IceBridge mesh: unknown protocol id=" + id
                    + " from=" + shortPub(sourcePub)
                    + " target=" + shortTarget(targetKey)
                    + " bytes=" + (appPayload == null ? 0 : appPayload.length));
            return;
        }
        String detail = "";
        if (id == MeshProtocolId.TELEMETRY
                && appPayload != null
                && appPayload.length > 0
                && appPayload.length <= 8) {
            detail = appPayload.length == 1 && appPayload[0] == 0x01
                    ? " PING"
                    : " probe";
        }
        LOG.info("IceBridge mesh: " + MeshProtocolId.name(id) + detail
                + " ok from=" + shortPub(sourcePub)
                + " target=" + shortTarget(targetKey)
                + " bytes=" + (appPayload == null ? 0 : appPayload.length));
    }

    private static String shortPub(byte[] sourcePub) {
        if (sourcePub == null || sourcePub.length < 4) {
            return "?";
        }
        return Hex.encode(sourcePub).substring(0, 12) + "…";
    }

    private static String shortTarget(String targetKey) {
        if (targetKey == null || targetKey.isEmpty()) {
            return "shared";
        }
        return targetKey.length() > 12 ? targetKey.substring(0, 12) + "…" : targetKey;
    }

    /**
     * Legacy poll: drain the shared (non-targeted) queue.
     */
    public List<InboundMessage> poll(int max) {
        return pollKey(SHARED_KEY, max);
    }

    /**
     * Drain messages addressed to a control-plane client public key.
     */
    public List<InboundMessage> pollForTarget(byte[] targetPub, int max) {
        if (targetPub == null || targetPub.length != 32) {
            return poll(max);
        }
        return pollKey(Hex.encode(targetPub), max);
    }

    private List<InboundMessage> pollKey(String key, int max) {
        int n = Math.max(0, max);
        List<InboundMessage> result = new ArrayList<>();
        ConcurrentLinkedQueue<InboundMessage> queue = queues.get(key);
        if (queue == null) {
            return result;
        }
        AtomicInteger count = counts.computeIfAbsent(key, k -> new AtomicInteger(0));
        for (int i = 0; i < n; i++) {
            InboundMessage m = queue.poll();
            if (m == null) {
                break;
            }
            count.decrementAndGet();
            result.add(m);
        }
        return result;
    }

    public int size() {
        int total = 0;
        for (AtomicInteger c : counts.values()) {
            total += Math.max(0, c.get());
        }
        return total;
    }
}
