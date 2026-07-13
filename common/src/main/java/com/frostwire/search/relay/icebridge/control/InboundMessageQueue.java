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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory queue of application payloads received over rUDP.
 *
 * <p>Unwraps optional {@link MeshEnvelope} framing so the control API
 * exposes demuxed {@code protocolId} + bare application payload.
 *
 * <p>Uses an {@link AtomicInteger} counter instead of
 * {@link ConcurrentLinkedQueue#size()} (O(n)) for overflow checks.
 */
public final class InboundMessageQueue implements RudpMessageListener {

    private static final Logger LOG = Logger.getLogger(InboundMessageQueue.class);
    private static final int DEFAULT_MAX_SIZE = 512;

    private final ConcurrentLinkedQueue<InboundMessage> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final int maxSize;

    public InboundMessageQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public InboundMessageQueue(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    @Override
    public void onMessage(byte[] sourcePub, byte[] payload) {
        int protocolId;
        byte[] appPayload;
        try {
            MeshEnvelope env = MeshEnvelope.unwrap(payload);
            protocolId = env.protocolId();
            appPayload = env.payload();
        } catch (Throwable t) {
            LOG.debug("Dropping malformed mesh envelope", t);
            return;
        }
        while (count.get() >= maxSize) {
            if (queue.poll() != null) {
                count.decrementAndGet();
            } else {
                break;
            }
        }
        queue.offer(new InboundMessage(sourcePub, appPayload, System.currentTimeMillis(), protocolId));
        count.incrementAndGet();
        logSuccessfulProtocol(sourcePub, protocolId, appPayload);
    }

    /**
     * Ops-visible trail for standalone forwarders (e.g. AWS
     * {@code icebridge-run-local.sh}): every demuxed known protocol and
     * short TELEMETRY/PING probes.
     */
    private static void logSuccessfulProtocol(byte[] sourcePub, int protocolId, byte[] appPayload) {
        int id = MeshProtocolId.effective(protocolId);
        if (!MeshProtocolId.isKnown(id)) {
            LOG.info("IceBridge mesh: unknown protocol id=" + id
                    + " from=" + shortPub(sourcePub)
                    + " bytes=" + (appPayload == null ? 0 : appPayload.length));
            return;
        }
        String detail = "";
        if (id == MeshProtocolId.TELEMETRY
                && appPayload != null
                && appPayload.length > 0
                && appPayload.length <= 8) {
            // Mesh warm / health probes (e.g. single-byte PING).
            detail = appPayload.length == 1 && appPayload[0] == 0x01
                    ? " PING"
                    : " probe";
        }
        LOG.info("IceBridge mesh: " + MeshProtocolId.name(id) + detail
                + " ok from=" + shortPub(sourcePub)
                + " bytes=" + (appPayload == null ? 0 : appPayload.length));
    }

    private static String shortPub(byte[] sourcePub) {
        if (sourcePub == null || sourcePub.length < 4) {
            return "?";
        }
        return Hex.encode(sourcePub).substring(0, 12) + "…";
    }

    /**
     * Remove and return up to {@code max} oldest queued messages.
     */
    public List<InboundMessage> poll(int max) {
        int n = Math.max(0, max);
        List<InboundMessage> result = new ArrayList<>();
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
        return count.get();
    }
}
