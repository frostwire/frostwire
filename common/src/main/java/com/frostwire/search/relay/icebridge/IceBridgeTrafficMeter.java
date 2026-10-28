/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/**
 * Turns samples of the IceBridge daemon's cumulative byte counters ({@code rudpBytesIn},
 * {@code rudpBytesOut} from {@code GET /metrics}) into transfer rates and totals.
 *
 * <p>Rates are averaged over the last {@link #RATE_WINDOW_NANOS} of samples, so one bursty poll
 * doesn't make the display jump. Totals keep growing across a daemon restart: when a counter goes
 * down, the daemon started over from zero, and its new value is all new traffic.
 *
 * <p>Not thread-safe: one poller feeds it and reads it.
 */
public final class IceBridgeTrafficMeter {

    /** Rates average the samples taken within this long of the newest one. */
    static final long RATE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(3);

    /** Intervals kept for the graph: five minutes of one-second polls. */
    public static final int HISTORY_SIZE = 300;

    /** Bytes per second in each interval between two samples, oldest first once full. */
    private final double[] historyIn = new double[HISTORY_SIZE];
    private final double[] historyOut = new double[HISTORY_SIZE];
    private int historyNext;
    private int historyCount;
    private Sample previous;

    private final ArrayDeque<Sample> window = new ArrayDeque<>();
    private long lastIn = -1;
    private long lastOut = -1;
    private long totalIn;
    private long totalOut;

    /**
     * Adds one reading of the daemon's counters.
     *
     * @param bytesIn   the daemon's cumulative bytes received
     * @param bytesOut  the daemon's cumulative bytes sent
     * @param nanoTime  {@link System#nanoTime()} when the reading was taken
     */
    public void sample(long bytesIn, long bytesOut, long nanoTime) {
        totalIn += delta(lastIn, bytesIn);
        totalOut += delta(lastOut, bytesOut);
        lastIn = bytesIn;
        lastOut = bytesOut;
        Sample current = new Sample(totalIn, totalOut, nanoTime);
        if (previous != null && nanoTime > previous.nanoTime) {
            double seconds = (nanoTime - previous.nanoTime) / (double) TimeUnit.SECONDS.toNanos(1);
            historyIn[historyNext] = (current.totalIn - previous.totalIn) / seconds;
            historyOut[historyNext] = (current.totalOut - previous.totalOut) / seconds;
            historyNext = (historyNext + 1) % HISTORY_SIZE;
            historyCount = Math.min(historyCount + 1, HISTORY_SIZE);
        }
        previous = current;
        window.addLast(current);
        while (window.size() > 2 && nanoTime - window.peekFirst().nanoTime > RATE_WINDOW_NANOS) {
            window.removeFirst();
        }
    }

    /** Bytes received per second, averaged over the rate window; 0 until two samples. */
    public double bytesInPerSecond() {
        return rate(true);
    }

    /** Bytes sent per second, averaged over the rate window; 0 until two samples. */
    public double bytesOutPerSecond() {
        return rate(false);
    }

    /** Bytes received since the meter's first sample, including the daemon's earlier traffic. */
    public long totalBytesIn() {
        return totalIn;
    }

    /** Bytes sent since the meter's first sample, including the daemon's earlier traffic. */
    public long totalBytesOut() {
        return totalOut;
    }

    /** Bytes received per second in each of the last {@link #HISTORY_SIZE} intervals, oldest first. */
    public double[] receiveHistory() {
        return history(historyIn);
    }

    /** Bytes sent per second in each of the last {@link #HISTORY_SIZE} intervals, oldest first. */
    public double[] sendHistory() {
        return history(historyOut);
    }

    private double[] history(double[] ring) {
        double[] out = new double[historyCount];
        int start = (historyNext - historyCount + HISTORY_SIZE) % HISTORY_SIZE;
        for (int i = 0; i < historyCount; i++) {
            out[i] = ring[(start + i) % HISTORY_SIZE];
        }
        return out;
    }

    /**
     * New traffic between two readings of a cumulative counter. The first reading counts in full
     * (what the daemon moved before anyone looked), and a smaller reading means the daemon restarted.
     */
    private static long delta(long previous, long current) {
        if (current < 0) {
            return 0;
        }
        return previous < 0 || current < previous ? current : current - previous;
    }

    private double rate(boolean in) {
        if (window.size() < 2) {
            return 0;
        }
        Sample first = window.peekFirst();
        Sample last = window.peekLast();
        long nanos = last.nanoTime - first.nanoTime;
        if (nanos <= 0) {
            return 0;
        }
        long bytes = in ? last.totalIn - first.totalIn : last.totalOut - first.totalOut;
        return bytes * (double) TimeUnit.SECONDS.toNanos(1) / nanos;
    }

    private record Sample(long totalIn, long totalOut, long nanoTime) {}
}
