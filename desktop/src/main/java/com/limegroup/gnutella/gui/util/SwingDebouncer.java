/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import javax.swing.Timer;

/**
 * Runs an action once on the EDT after calls to {@link #trigger()} have stopped for {@code
 * delayMillis}. Use it for event storms (window resizes, drags) where only the final state matters.
 * Call {@link #trigger()} from the EDT.
 */
public final class SwingDebouncer {
  private final Timer timer;

  public SwingDebouncer(int delayMillis, Runnable action) {
    timer = new Timer(delayMillis, e -> action.run());
    timer.setRepeats(false);
  }

  /** (Re)starts the quiet period; the action runs when it elapses without another trigger. */
  public void trigger() {
    timer.restart();
  }
}
