/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IceBridgeTrafficMeterTest {

    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    @Test
    void ratesAreBytesPerSecondBetweenSamples() {
        IceBridgeTrafficMeter meter = new IceBridgeTrafficMeter();
        meter.sample(1_000, 500, 0);
        assertEquals(0, meter.bytesInPerSecond());
        meter.sample(3_048, 1_524, SECOND);
        assertEquals(2_048, meter.bytesInPerSecond(), 0.001);
        assertEquals(1_024, meter.bytesOutPerSecond(), 0.001);
    }

    @Test
    void ratesAverageTheLastFewSeconds() {
        IceBridgeTrafficMeter meter = new IceBridgeTrafficMeter();
        meter.sample(0, 0, 0);
        meter.sample(10_000, 0, SECOND); // burst
        meter.sample(10_000, 0, 2 * SECOND); // idle
        assertEquals(5_000, meter.bytesInPerSecond(), 0.001);
        // Samples older than the window drop out: the burst stops counting.
        meter.sample(10_000, 0, 6 * SECOND);
        meter.sample(10_000, 0, 7 * SECOND);
        assertEquals(0, meter.bytesInPerSecond(), 0.001);
    }

    @Test
    void totalsIncludeTrafficBeforeTheFirstSample() {
        IceBridgeTrafficMeter meter = new IceBridgeTrafficMeter();
        meter.sample(4_096, 2_048, 0);
        assertEquals(4_096, meter.totalBytesIn());
        assertEquals(2_048, meter.totalBytesOut());
        meter.sample(5_000, 2_100, SECOND);
        assertEquals(5_000, meter.totalBytesIn());
        assertEquals(2_100, meter.totalBytesOut());
    }

    @Test
    void totalsSurviveADaemonRestart() {
        IceBridgeTrafficMeter meter = new IceBridgeTrafficMeter();
        meter.sample(10_000, 8_000, 0);
        // The daemon restarted: its counters began again from zero.
        meter.sample(300, 200, SECOND);
        assertEquals(10_300, meter.totalBytesIn());
        assertEquals(8_200, meter.totalBytesOut());
        assertEquals(300, meter.bytesInPerSecond(), 0.001);
        meter.sample(1_300, 200, 2 * SECOND);
        assertEquals(11_300, meter.totalBytesIn());
    }

    @Test
    void negativeReadingsAreIgnored() {
        IceBridgeTrafficMeter meter = new IceBridgeTrafficMeter();
        meter.sample(-1, -1, 0);
        assertEquals(0, meter.totalBytesIn());
        assertEquals(0, meter.totalBytesOut());
    }
}
