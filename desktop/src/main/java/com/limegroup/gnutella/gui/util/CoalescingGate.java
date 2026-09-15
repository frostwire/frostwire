/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collapses a burst of identical requests into a single running item plus at most one follow-up.
 *
 * <p>Call {@link #begin()} before running: it grants the run only when nothing is running,
 * otherwise it records that another run is wanted. Call {@link #finish()} when the run completes;
 * it reports whether a collapsed follow-up should be run. Latest-wins by construction: any number
 * of requests during a run produce exactly one follow-up, so a bounded executor is never flooded.
 */
public final class CoalescingGate {
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean pending = new AtomicBoolean(false);

  /**
   * @return true when the caller should start the work now.
   */
  public boolean begin() {
    if (running.compareAndSet(false, true)) {
      return true;
    }
    pending.set(true);
    return false;
  }

  /**
   * @return true when the caller should run the work once more (a request arrived meanwhile).
   */
  public boolean finish() {
    running.set(false);
    return pending.compareAndSet(true, false);
  }
}
