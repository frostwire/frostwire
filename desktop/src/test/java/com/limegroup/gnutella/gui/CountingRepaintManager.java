/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/**
 * Counts layout requests: {@code JComponent.revalidate()} invalidates the component (and, in a
 * shown window, every ancestor up to the frame) and queues its validate root here. Headless tests
 * have no native peers, so ancestors never become valid and can't be watched for invalidation
 * themselves; the queued requests are what a window-wide layout pass starts from.
 */
public final class CountingRepaintManager extends RepaintManager {
  private static int requests;

  private CountingRepaintManager() {}

  @Override
  public void addInvalidComponent(JComponent invalidComponent) {
    requests++;
    super.addInvalidComponent(invalidComponent);
  }

  /** Forgets the requests counted so far; call from inside {@link #layoutRequestsDuring}. */
  public static void reset() {
    requests = 0;
  }

  /**
   * Runs {@code edtWork} on the EDT with this manager installed and returns the layout requests
   * made after its last {@link #reset}, including those of the events it queued.
   */
  public static int layoutRequestsDuring(Runnable edtWork) throws Exception {
    RepaintManager[] previous = new RepaintManager[1];
    SwingUtilities.invokeAndWait(
        () -> {
          previous[0] = RepaintManager.currentManager((JComponent) null);
          RepaintManager.setCurrentManager(new CountingRepaintManager());
          reset();
        });
    int[] result = new int[1];
    try {
      SwingUtilities.invokeAndWait(edtWork);
      // the events edtWork queued (a deferred revalidate(), say) run before this one
      SwingUtilities.invokeAndWait(() -> result[0] = requests);
    } finally {
      SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(previous[0]));
    }
    return result[0];
  }
}
