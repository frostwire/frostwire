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

import com.frostwire.util.Logger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Benchmark for Logger performance improvements.
 *
 * Measures:
 * - Log calls with level checks (optimized)
 * - Log calls without stack trace capture (showCallingMethodInfo=false)
 * - Allocation pressure via -prof gc
 *
 * Run: ./gradlew jmh --include=LoggerBench
 *
 * Expected improvement after optimization:
 * - 50-100x ops/sec increase when logging is disabled
 * - 0 alloc/op (baseline: ~4 allocs/op with unconditional stack trace)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class LoggerBench {

    private Logger logger;

    @Setup
    public void setup() {
        logger = Logger.getLogger(LoggerBench.class);
        // Disable logging to simulate hot path where logs are off
        logger.getName(); // force initialization
        java.util.logging.Logger.getLogger(LoggerBench.class.getSimpleName()).setLevel(Level.OFF);
    }

    /**
     * Baseline: Log with disabled level (tests isLoggable() guard effectiveness).
     * After optimization, this should be extremely fast (just a level check).
     */
    @Benchmark
    public void logInfoDisabled(Blackhole bh) {
        logger.info("Test message that won't be logged");
        bh.consume(logger);
    }

    /**
     * Test logging without stack trace capture.
     * This is the common case (showCallingMethodInfo=false).
     */
    @Benchmark
    public void logInfoWithoutStackTrace(Blackhole bh) {
        logger.info("Test message", false);
        bh.consume(logger);
    }

    /**
     * Test logging with stack trace capture (expensive, but rare).
     * This tests the showCallingMethodInfo=true path.
     */
    @Benchmark
    public void logInfoWithStackTrace(Blackhole bh) {
        logger.info("Test message with stack", true);
        bh.consume(logger);
    }

    /**
     * Test warn level (different code path).
     */
    @Benchmark
    public void logWarnDisabled(Blackhole bh) {
        logger.warn("Warning message that won't be logged");
        bh.consume(logger);
    }

    /**
     * Test error level (different code path).
     */
    @Benchmark
    public void logErrorDisabled(Blackhole bh) {
        logger.error("Error message that won't be logged");
        bh.consume(logger);
    }
}
