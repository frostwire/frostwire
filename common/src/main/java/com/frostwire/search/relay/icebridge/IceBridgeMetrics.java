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

    public void reset() {
        rudpPacketsIn.set(0);
        rudpPacketsOut.set(0);
        rudpBytesIn.set(0);
        rudpBytesOut.set(0);
        controlRequests.set(0);
        controlErrors.set(0);
    }
}