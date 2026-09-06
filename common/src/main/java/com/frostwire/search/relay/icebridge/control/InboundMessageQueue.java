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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final int MAX_CONSUMERS = 256;
    private static final int MAX_MESSAGES = 4096;
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Map<String, ArrayDeque<InboundMessage>> queues = new HashMap<>();
    private final Set<String> consumers = new HashSet<>();
    private final int maxSizePerQueue;
    private boolean sharedConsumerEnabled = true;
    private int messageCount;
    private long retainedBytes;

    public InboundMessageQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public InboundMessageQueue(int maxSize) {
        this.maxSizePerQueue = Math.max(1, maxSize);
    }

    /** Register the sole queue owner before accepting targeted work. Administrator API. */
    public synchronized boolean registerConsumer(byte[] pub) {
        if (pub == null || pub.length != 32) {
            return false;
        }
        String key = Hex.encode(pub);
        if (!consumers.contains(key) && consumers.size() >= MAX_CONSUMERS) {
            return false;
        }
        consumers.add(key);
        return true;
    }

    /** Refuses to orphan accepted work; drain before unregistering. */
    public synchronized boolean unregisterConsumer(byte[] pub) {
        if (pub == null || pub.length != 32) {
            return false;
        }
        String key = Hex.encode(pub);
        if (queues.containsKey(key)) {
            return false;
        }
        consumers.remove(key);
        return true;
    }

    /** Server lifecycle owner disables this unless an actual shared poller is installed. */
    public synchronized boolean setSharedConsumerEnabled(boolean enabled) {
        if (!enabled && queues.containsKey(SHARED_KEY)) {
            return false;
        }
        sharedConsumerEnabled = enabled;
        return true;
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
     * A registered identity owns delivery exclusively. Otherwise the installed
     * shared consumer owns it. No mirrors are retained or allowed to gate ACKs.
     */
    public synchronized boolean offerFromRudp(byte[] targetPub, byte[] sourcePub, byte[] payload) {
        String key = targetPub != null && targetPub.length == 32 ? Hex.encode(targetPub) : SHARED_KEY;
        return offerUnwrapped(consumers.contains(key) ? key : SHARED_KEY, sourcePub, payload);
    }

    /**
     * Control-plane / local-endpoint delivery: message is for a specific
     * registered client (USE_REMOTE peer whose host:port is this process).
     *
     * @param targetPub destination client Ed25519 public key (32 bytes)
     * @param sourcePub sender public key if known, else empty/null
     * @param wireOrAppPayload MeshEnvelope wire bytes or bare payload
     */
    public boolean offerForTarget(byte[] targetPub, byte[] sourcePub, byte[] wireOrAppPayload) {
        if (targetPub == null || targetPub.length != 32) {
            return false;
        }
        return offerUnwrapped(Hex.encode(targetPub), sourcePub, wireOrAppPayload);
    }

    private synchronized boolean offerUnwrapped(String targetKey, byte[] sourcePub, byte[] payload) {
        if ((SHARED_KEY.equals(targetKey) ? !sharedConsumerEnabled : !consumers.contains(targetKey))
                || payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES
                || (sourcePub != null && sourcePub.length != 0 && sourcePub.length != 32)
                || messageCount >= MAX_MESSAGES || retainedBytes > MAX_BYTES - payload.length - 32) {
            return false;
        }
        int protocolId;
        byte[] appPayload;
        try {
            MeshEnvelope env = MeshEnvelope.unwrap(payload);
            protocolId = env.protocolId();
            appPayload = env.payload();
        } catch (IllegalArgumentException t) {
            // Bare app payloads (some RELAY local paths) — treat as SEARCH.
            protocolId = MeshProtocolId.SEARCH;
            appPayload = payload;
            if (payload == null || payload.length == 0) {
                LOG.debug("Dropping empty inbound payload");
                return false;
            }
        }
        ArrayDeque<InboundMessage> queue = queues.get(targetKey);
        if (queue != null && queue.size() >= maxSizePerQueue) {
            return false;
        }
        if (queue == null) {
            queue = new ArrayDeque<>();
            queues.put(targetKey, queue);
        }
        byte[] source = sourcePub == null ? new byte[0] : sourcePub.clone();
        queue.offer(new InboundMessage(source, appPayload.clone(), System.currentTimeMillis(), protocolId));
        messageCount++;
        retainedBytes += source.length + appPayload.length;
        logSuccessfulProtocol(sourcePub, protocolId, appPayload, targetKey);
        return true;
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
        LOG.debug("IceBridge mesh: " + MeshProtocolId.name(id) + detail
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
     * Transfers ownership to the shared caller. HTTP polling is at-most-once,
     * not application delivery confirmation: a disconnected response can lose
     * this batch. Reliable HTTP delivery requires a separately negotiated lease.
     */
    public List<InboundMessage> poll(int max) {
        return pollKey(SHARED_KEY, max);
    }

    /**
     * Drain messages addressed to a control-plane client public key.
     */
    public List<InboundMessage> pollForTarget(byte[] targetPub, int max) {
        if (targetPub == null || targetPub.length != 32) {
            return new ArrayList<>();
        }
        return pollKey(Hex.encode(targetPub), max);
    }

    private synchronized List<InboundMessage> pollKey(String key, int max) {
        int n = Math.max(0, Math.min(max, 256));
        List<InboundMessage> result = new ArrayList<>();
        ArrayDeque<InboundMessage> queue = queues.get(key);
        if (queue == null) {
            return result;
        }
        int bytes = 0;
        for (int i = 0; i < n; i++) {
            InboundMessage m = queue.peek();
            if (m == null || bytes + m.payload().length > MAX_PAYLOAD_BYTES) {
                break;
            }
            queue.poll();
            messageCount--;
            retainedBytes -= m.sourcePub().length + m.payload().length;
            bytes += m.payload().length;
            result.add(m);
        }
        if (queue.isEmpty()) {
            queues.remove(key);
        }
        return result;
    }

    public synchronized int size() {
        return messageCount;
    }
}
