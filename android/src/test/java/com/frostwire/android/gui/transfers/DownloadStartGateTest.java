/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadStartGateTest {
    @Test
    public void cancellationWhileWaitingDeniesMetadataAndFallbackStarts() {
        DownloadStartGate gate = new DownloadStartGate();
        assertTrue(gate.cancel());
        assertFalse(gate.tryStart());
        assertFalse(gate.tryStart());
    }

    @Test
    public void committedDownloadIsNotInterruptedByFetcherCleanup() {
        DownloadStartGate gate = new DownloadStartGate();
        assertTrue(gate.tryStart());
        assertFalse(gate.cancel());
        assertFalse(gate.tryStart());
    }

    @Test
    public void concurrentCancelAndStartHaveOneWinner() throws Exception {
        for (int i = 0; i < 100; i++) {
            DownloadStartGate gate = new DownloadStartGate();
            CountDownLatch ready = new CountDownLatch(1);
            AtomicInteger winners = new AtomicInteger();
            Thread cancel = new Thread(() -> {
                try {
                    ready.await();
                    if (gate.cancel()) winners.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            cancel.start();
            ready.countDown();
            if (gate.tryStart()) winners.incrementAndGet();
            cancel.join(1000);
            assertFalse(cancel.isAlive());
            assertEquals(1, winners.get());
        }
    }
}
