/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A copy of the session's {@link SessionLimits} that is read once, off the UI thread, and kept
 * current by the setters that change them, so reading it never calls into libtorrent (the way
 * BTEngine's cached paused state works).
 *
 * <p>Thread-safe. {@link #get()} before the first {@link #refresh()} reads from the session on the
 * calling thread, as the Options window used to do, rather than returning made-up values.
 */
final class SessionLimitsCache {
  private final Supplier<SessionLimits> reader;
  private volatile SessionLimits limits;

  SessionLimitsCache(Supplier<SessionLimits> reader) {
    this.reader = reader;
  }

  /** The limits. Does not call the session once {@link #refresh()} has run. */
  SessionLimits get() {
    SessionLimits current = limits;
    if (current == null) {
      current = reader.get();
      limits = current;
    }
    return current;
  }

  /** Reads the session again. Blocks on libtorrent's session thread: never call it on the EDT. */
  void refresh() {
    limits = reader.get();
  }

  /**
   * Applies a change that was just made to the session. Nothing to update before the first read:
   * that read will see the change.
   */
  synchronized void update(UnaryOperator<SessionLimits> change) {
    SessionLimits current = limits;
    if (current != null) {
      limits = change.apply(current);
    }
  }
}
