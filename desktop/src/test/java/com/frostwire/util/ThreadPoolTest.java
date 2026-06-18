/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolTest {

    @Test
    void threadPoolExecutesTasks() throws Exception {
        ThreadPool pool = new ThreadPool("test-pool", 2, new LinkedBlockingQueue<>(), true);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);

        pool.execute(() -> {
            ran.set(true);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "task should execute");
        assertTrue(ran.get());
        pool.shutdown();
    }

    @Test
    void threadStackSizeIsAtLeast512KB() throws Exception {
        // Verify the stack size constant is large enough to prevent
        // StackOverflowError on deep call chains (regex, TLS, jlibtorrent).
        // The old value was 1024 * 4 = 4 KB which caused SOE.
        // We can't read the private field directly, but we can verify
        // that a deeply nested call doesn't overflow.
        ThreadPool pool = new ThreadPool("test-stack", 1, new LinkedBlockingQueue<>(), true);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger depth = new java.util.concurrent.atomic.AtomicInteger(0);

        pool.execute(() -> {
            try {
                // Recursive call to ~500 depth — would SOE with 4KB stack.
                recursiveDepth(500, depth);
            } catch (StackOverflowError e) {
                // fail silently — depth will be < 500
            }
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(depth.get() >= 400,
                "should support at least 400 recursion depth without SOE; got " + depth.get());
        pool.shutdown();
    }

    private void recursiveDepth(int remaining, AtomicInteger depth) {
        if (remaining <= 0) {
            return;
        }
        depth.incrementAndGet();
        recursiveDepth(remaining - 1, depth);
    }
}
