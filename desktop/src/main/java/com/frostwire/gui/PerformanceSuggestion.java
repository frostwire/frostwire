/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Suggests Lite, once a session, when the UI keeps freezing in Full (frostwire/frostwire#1555). It
 * never switches the mode itself: it shows a notice the user can follow to Options or dismiss for
 * good.
 *
 * <p>It counts the stalls StrictEdtMode and the watchdog record (the ones LivePresenceHeartbeat
 * counts for Icebase), with their detectors and thresholds unchanged: an EDT event of 5 s or more,
 * at most one a minute. {@link #STALLS} of them within {@link #WINDOW_MS} is a machine that keeps
 * freezing, like the ones in #1512 (4 reports an hour from one 2-CPU machine, for hours); a healthy
 * machine's occasional stall doesn't add up to it.
 */
public final class PerformanceSuggestion {
  static final int STALLS = 3;
  static final long WINDOW_MS = TimeUnit.HOURS.toMillis(1);

  /**
   * Stalls in the first two minutes don't count: startup has its own one-time costs (class loading,
   * the warm-up) that a machine pays once, and it doesn't make the rest of the session slow.
   */
  static final long STARTUP_GRACE_MS = TimeUnit.MINUTES.toMillis(2);

  private static volatile PerformanceSuggestion instance;

  private final LongSupplier uptimeMs;
  private final BooleanSupplier dismissed;
  private final Runnable suggest;
  private final Deque<Long> stalls = new ArrayDeque<>();
  private boolean suggested;

  /**
   * @param uptimeMs how long the app has been running
   * @param dismissed whether the user dismissed the suggestion for good
   * @param suggest shows the notice; called at most once, on the thread that recorded the stall
   */
  PerformanceSuggestion(LongSupplier uptimeMs, BooleanSupplier dismissed, Runnable suggest) {
    this.uptimeMs = uptimeMs;
    this.dismissed = dismissed;
    this.suggest = suggest;
  }

  /** Starts counting this session's stalls; {@code suggest} shows the notice. */
  public static void install(BooleanSupplier dismissed, Runnable suggest) {
    instance =
        new PerformanceSuggestion(
            () -> ManagementFactory.getRuntimeMXBean().getUptime(), dismissed, suggest);
  }

  /** An EDT stall was recorded; any thread. Never throws into the stall detectors. */
  public static void stallRecorded() {
    PerformanceSuggestion suggestion = instance;
    if (suggestion != null) {
      try {
        suggestion.onStall();
      } catch (Throwable ignored) {
        // A suggestion is best effort.
      }
    }
  }

  void onStall() {
    long now = uptimeMs.getAsLong();
    synchronized (this) {
      if (suggested || now < STARTUP_GRACE_MS || PerformanceMode.isLite()) {
        return;
      }
      stalls.addLast(now);
      while (!stalls.isEmpty() && now - stalls.peekFirst() > WINDOW_MS) {
        stalls.removeFirst();
      }
      if (stalls.size() < STALLS || dismissed.getAsBoolean()) {
        return;
      }
      suggested = true;
    }
    suggest.run();
  }
}
