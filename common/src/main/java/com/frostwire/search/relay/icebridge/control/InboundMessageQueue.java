/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.udp.RudpMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory queue of payloads received over rUDP.
 *
 * <p>The local FrostWire process polls this queue through the IceBridge control
 * API to collect remote search requests and results.
 */
public final class InboundMessageQueue implements RudpMessageListener {

    private static final int DEFAULT_MAX_SIZE = 512;

    private final ConcurrentLinkedQueue<InboundMessage> queue = new ConcurrentLinkedQueue<>();
    private final int maxSize;

    public InboundMessageQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public InboundMessageQueue(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    @Override
    public void onMessage(byte[] sourcePub, byte[] payload) {
        while (queue.size() >= maxSize) {
            queue.poll();
        }
        queue.offer(new InboundMessage(sourcePub, payload, System.currentTimeMillis()));
    }

    /**
     * Remove and return up to {@code max} oldest queued messages.
     */
    public List<InboundMessage> poll(int max) {
        int n = Math.max(0, max);
        List<InboundMessage> result = new ArrayList<>(Math.min(n, queue.size()));
        for (int i = 0; i < n; i++) {
            InboundMessage m = queue.poll();
            if (m == null) {
                break;
            }
            result.add(m);
        }
        return result;
    }

    public int size() {
        return queue.size();
    }
}
