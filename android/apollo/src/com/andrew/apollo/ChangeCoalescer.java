/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Limits how often each kind of change is reported without losing the last one. The first change of
 * a kind goes out at once. Changes within its interval after that collapse into one that runs when
 * the interval ends. A change submitted while one is still waiting to run is dropped, because that
 * one reads the service's state when it runs and so reports this change too.
 */
final class ChangeCoalescer {

  /** Runs a task after a delay, e.g. a Handler's postDelayed; false if it can't. */
  interface Scheduler {
    boolean schedule(Runnable task, long delayMillis);
  }

  private final Scheduler scheduler;
  private final LongSupplier clock;
  private final Map<String, Long> lastRun = new HashMap<>();
  private final Set<String> waiting = new HashSet<>();

  ChangeCoalescer(Scheduler scheduler, LongSupplier clock) {
    this.scheduler = scheduler;
    this.clock = clock;
  }

  void submit(final String kind, final long intervalMillis, final Runnable task) {
    final long delay;
    synchronized (this) {
      if (!waiting.add(kind)) {
        return;
      }
      final Long last = lastRun.get(kind);
      delay = last == null ? 0 : Math.max(0, last + intervalMillis - clock.getAsLong());
    }
    final boolean scheduled =
        scheduler.schedule(
            () -> {
              synchronized (ChangeCoalescer.this) {
                waiting.remove(kind);
                lastRun.put(kind, clock.getAsLong());
              }
              task.run();
            },
            delay);
    if (!scheduled) {
      synchronized (this) {
        waiting.remove(kind);
      }
    }
  }
}
