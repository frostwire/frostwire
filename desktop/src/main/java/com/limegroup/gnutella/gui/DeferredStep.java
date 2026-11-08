/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

/**
 * Work scheduled for a later EDT event that can also be run sooner, when something needs its result
 * before that event comes: whichever run comes first does the work, and later runs do nothing. Used
 * on the EDT only, so it needs no synchronization.
 */
final class DeferredStep implements Runnable {
  private Runnable work;

  DeferredStep(Runnable work) {
    this.work = work;
  }

  /** Does the work unless it was already done. */
  @Override
  public void run() {
    Runnable pending = work;
    if (pending != null) {
      work = null;
      pending.run();
    }
  }

  boolean isDone() {
    return work == null;
  }
}
