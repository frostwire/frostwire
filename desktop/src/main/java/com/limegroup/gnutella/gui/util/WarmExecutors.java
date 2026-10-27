/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import com.frostwire.service.ErrorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-size executors whose workers are all started when the executor is built and never time out.
 *
 * <p>The GUI's shared background executors are fed from the EDT, much of it by periodic refreshes.
 * Built by {@code ExecutorsHelper}, their workers died after 5 idle seconds, and a {@code
 * ThreadPoolExecutor} below its core size starts a new worker on every {@code execute}, so the next
 * submission from the EDT had to start an OS thread ({@code Thread.start}) before it returned:
 * every 20 s for the VPN status poll, and after any pause in the 1 s refresh. Thread creation can
 * wait on the JVM (e.g. while a GC holds the thread list) and stalled the EDT (Icebase
 * #146d5b141956). A few parked daemon threads cost nothing, so these executors keep theirs.
 */
final class WarmExecutors {

  private WarmExecutors() {}

  /** A single-worker FIFO queue: tasks run one at a time, in submission order. */
  static ExecutorService serialQueue(String name) {
    return pool(1, name);
  }

  /** A pool of {@code threads} daemon workers named {@code name} with an unbounded queue. */
  static ExecutorService pool(int threads, String name) {
    return pool(
        threads,
        r -> {
          Thread t = new Thread(r, name);
          t.setDaemon(true);
          t.setUncaughtExceptionHandler(
              (thread, e) -> ErrorService.error(e, "Uncaught thread error: " + thread.getName()));
          return t;
        });
  }

  /** Builds the pool and starts all its workers now, on the calling thread. */
  static ExecutorService pool(int threads, ThreadFactory factory) {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
    executor.prestartAllCoreThreads();
    return Executors.unconfigurableExecutorService(executor);
  }
}
