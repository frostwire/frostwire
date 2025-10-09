/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.benchmarks;

import com.frostwire.util.ThreadPool;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for ThreadPool performance improvements.
 *
 * Measures:
 * - Task submission throughput
 * - Allocation pressure from thread naming (via -prof gc)
 *
 * Run: ./gradlew jmh --include=ThreadPoolBench
 *
 * Expected improvement after optimization:
 * - 70-80% reduction in alloc/op (baseline: 3-4 string objects per task)
 * - Minimal impact on task execution latency
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ThreadPoolBench {

    private ExecutorService pool;

    @Setup
    public void setup() {
        // Simulate SearchManager crawler pool: 6 threads
        pool = ThreadPool.newThreadPool("BenchPool", 6, true);
    }

    @TearDown
    public void teardown() {
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
    }

    /**
     * Benchmark task submission throughput.
     * Each task triggers beforeExecute() which builds thread name.
     * After optimization, this should show reduced allocations.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    public void submitNoOpTasks(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            pool.execute(() -> {
                bh.consume(Thread.currentThread().getName());
                latch.countDown();
            });
        }
        latch.await(10, TimeUnit.SECONDS);
    }

    /**
     * Benchmark single task submission (measures per-task overhead).
     */
    @Benchmark
    public void submitSingleTask(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        pool.execute(() -> {
            bh.consume(Thread.currentThread().getName());
            latch.countDown();
        });
        latch.await(1, TimeUnit.SECONDS);
    }
}
