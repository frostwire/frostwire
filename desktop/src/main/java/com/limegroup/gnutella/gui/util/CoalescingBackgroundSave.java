/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import com.frostwire.util.Logger;
import java.util.concurrent.Executor;

/**
 * Runs a settings save on a background executor, coalescing bursts: requests that arrive while a
 * save runs collapse into one more save afterwards. Use it for saves triggered from the EDT by
 * frequent UI events (dragging or resizing table columns), so the props file is written off the EDT
 * and at most twice per burst.
 */
public final class CoalescingBackgroundSave {
  private static final Logger LOG = Logger.getLogger(CoalescingBackgroundSave.class);

  private final Runnable save;
  private final Executor executor;
  private final CoalescingGate gate = new CoalescingGate();

  public CoalescingBackgroundSave(Runnable save, Executor executor) {
    this.save = save;
    this.executor = executor;
  }

  /** Saves soon on the executor; returns immediately. */
  public void request() {
    if (gate.begin()) {
      executor.execute(this::run);
    }
  }

  private void run() {
    do {
      try {
        save.run();
      } catch (Throwable t) {
        LOG.warn("Background settings save failed", t);
      }
    } while (gate.finish() && gate.begin());
  }
}
