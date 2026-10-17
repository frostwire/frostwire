/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Most DesktopParallelExecutor callers run on the EDT without handling rejection, so a busy
 * pool must queue extra tasks instead of throwing RejectedExecutionException.
 */
class DesktopParallelExecutorQueueTest {

    @Test
    void busyPoolQueuesTasksInsteadOfRejecting() throws InterruptedException {
        int tasks = 40; // far beyond 4 threads + the old 4-slot queue
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(tasks);
        try {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < tasks; i++) {
                    DesktopParallelExecutor.execute(() -> {
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    });
                }
            });
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS), "every queued task must eventually run");
    }
}
