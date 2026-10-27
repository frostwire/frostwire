/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves the libtorrent session state off the thread that changed a setting.
 *
 * <p>Saving waits on the libtorrent network thread to serialize the whole session and then writes
 * it to disk, so it must not run on the thread that applied the setting (often the Swing EDT).
 * Requests made while a save is still queued share that save: an Apply that changes several
 * settings writes the state once. A queued save runs after the settings that requested it were
 * posted to libtorrent, so it sees them.
 */
public final class SessionStateSaver {
  private final Runnable save;
  private final Executor executor;
  private final AtomicBoolean pending = new AtomicBoolean();
  private final Object lock = new Object();
  // guarded by lock
  private boolean stopped;

  /**
   * @param save writes the session state; it runs on {@code executor}, or on the stopping thread
   * @param executor runs queued saves, one at a time
   */
  public SessionStateSaver(Runnable save, Executor executor) {
    this.save = save;
    this.executor = executor;
  }

  /** Queues a save, unless one is already queued, and returns without waiting for it. */
  public void requestSave() {
    if (pending.compareAndSet(false, true)) {
      executor.execute(this::runQueuedSave);
    }
  }

  /** Lets queued saves run again after {@link #saveAndStop()}. */
  public void start() {
    synchronized (lock) {
      stopped = false;
    }
  }

  /**
   * Waits for a save in progress, saves once more on the calling thread and ignores the saves still
   * queued, so none of them touches the session while it is being destroyed.
   */
  public void saveAndStop() {
    synchronized (lock) {
      stopped = true;
      save.run();
    }
  }

  private void runQueuedSave() {
    // Cleared before saving: a setting applied while this save runs queues another one.
    pending.set(false);
    synchronized (lock) {
      if (!stopped) {
        save.run();
      }
    }
  }
}
