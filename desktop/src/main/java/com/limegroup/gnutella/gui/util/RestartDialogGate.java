/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui.util;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lets one restart notice through, and drops duplicates until that notice is dismissed. */
public final class RestartDialogGate {
  private final AtomicBoolean scheduled = new AtomicBoolean(false);

  /**
   * @return true when this caller should show the dialog.
   */
  public boolean schedule() {
    return scheduled.compareAndSet(false, true);
  }

  public void finished() {
    scheduled.set(false);
  }
}
