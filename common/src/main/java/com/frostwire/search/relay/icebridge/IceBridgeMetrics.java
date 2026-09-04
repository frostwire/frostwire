/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory counters for the IceBridge servent.
 *
 * <p>No disk I/O. Counters are exposed through the HTTP control interface and
 * can be reset remotely during development.
 */
public final class IceBridgeMetrics {

    private final AtomicLong rudpPacketsIn = new AtomicLong();
    private final AtomicLong rudpPacketsOut = new AtomicLong();
    private final AtomicLong rudpBytesIn = new AtomicLong();
    private final AtomicLong rudpBytesOut = new AtomicLong();
    private final AtomicLong controlRequests = new AtomicLong();
    private final AtomicLong controlErrors = new AtomicLong();
    private final AtomicLong helloRejectedCount = new AtomicLong();
    private final AtomicLong relayRateLimitedCount = new AtomicLong();
    private final AtomicLong searchRateLimitedCount = new AtomicLong();
    private final AtomicLong powRejectedCount = new AtomicLong();
    private final AtomicLong spamMarkedDroppedCount = new AtomicLong();

    public void rudpPacketIn(int bytes) {
        rudpPacketsIn.incrementAndGet();
        rudpBytesIn.addAndGet(bytes);
    }

    public void rudpPacketOut(int bytes) {
        rudpPacketsOut.incrementAndGet();
        rudpBytesOut.addAndGet(bytes);
    }

    public void controlRequest() {
        controlRequests.incrementAndGet();
    }

    public void controlError() {
        controlErrors.incrementAndGet();
    }

    public void helloRejected() {
        helloRejectedCount.incrementAndGet();
    }

    public void relayRateLimited() {
        relayRateLimitedCount.incrementAndGet();
    }

    public void searchRateLimited() {
        searchRateLimitedCount.incrementAndGet();
    }

    public void powRejected() {
        powRejectedCount.incrementAndGet();
    }

    public void spamMarkedDropped() {
        spamMarkedDroppedCount.incrementAndGet();
    }

    public long rudpPacketsIn() {
        return rudpPacketsIn.get();
    }

    public long rudpPacketsOut() {
        return rudpPacketsOut.get();
    }

    public long rudpBytesIn() {
        return rudpBytesIn.get();
    }

    public long rudpBytesOut() {
        return rudpBytesOut.get();
    }

    public long controlRequests() {
        return controlRequests.get();
    }

    public long controlErrors() {
        return controlErrors.get();
    }

    public long helloRejectedCount() {
        return helloRejectedCount.get();
    }

    public long relayRateLimitedCount() {
        return relayRateLimitedCount.get();
    }

    public long searchRateLimitedCount() {
        return searchRateLimitedCount.get();
    }

    public long powRejectedCount() {
        return powRejectedCount.get();
    }

    public long spamMarkedDroppedCount() {
        return spamMarkedDroppedCount.get();
    }

    public void reset() {
        rudpPacketsIn.set(0);
        rudpPacketsOut.set(0);
        rudpBytesIn.set(0);
        rudpBytesOut.set(0);
        controlRequests.set(0);
        controlErrors.set(0);
        helloRejectedCount.set(0);
        relayRateLimitedCount.set(0);
        searchRateLimitedCount.set(0);
        powRejectedCount.set(0);
        spamMarkedDroppedCount.set(0);
    }
}