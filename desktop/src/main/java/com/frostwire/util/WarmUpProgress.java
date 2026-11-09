/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Whether the startup warm-up has finished and how long it took, for Icebase reports
 * (frostwire/frostwire#1490). A stall on a path the warm-up covers means something else when the
 * warm-up was still running: the EDT got there first, or waited on the warm-up's own work.
 *
 * <p>The warm-up runs as several threads; it is done when every one of them has finished.
 */
public final class WarmUpProgress {

  /**
   * The warm-up's state at one moment: {@code elapsedMs} is its total time when {@code done}, and
   * how long it had been running otherwise.
   */
  public record Snapshot(boolean done, long elapsedMs) {}

  private final AtomicInteger pending = new AtomicInteger();
  private volatile long startNanos;
  private volatile long doneNanos;
  private volatile boolean started;

  /** Starts timing a warm-up of {@code steps} threads; a second call is ignored. */
  public synchronized void start(int steps, long nowNanos) {
    if (started) {
      return;
    }
    startNanos = nowNanos;
    pending.set(steps);
    started = true;
    if (steps <= 0) {
      doneNanos = nowNanos;
    }
  }

  /** One warm-up thread has finished, whether or not its work succeeded. */
  public void stepFinished(long nowNanos) {
    if (started && pending.decrementAndGet() == 0) {
      doneNanos = nowNanos;
    }
  }

  /** The state now, or null before the warm-up started. */
  public Snapshot snapshot(long nowNanos) {
    if (!started) {
      return null;
    }
    boolean done = pending.get() <= 0;
    long end = done ? doneNanos : nowNanos;
    return new Snapshot(done, Math.max(0, TimeUnit.NANOSECONDS.toMillis(end - startNanos)));
  }
}
