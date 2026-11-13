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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Whether the session is paused, for readers that must not ask the session (the EDT). The value
 * read from the session in the background right after it starts must not overwrite a pause or a
 * resume that happened in the meantime: it used to, and a pause made right after start was then
 * reported as not paused (BTEngineSessionTest.pausingIsVisibleWithoutAskingTheSession failed under
 * load).
 *
 * <p>Thread-safe.
 */
final class PausedStateCache {
  /** null until the first value is known: then reads say "not paused". */
  private final AtomicReference<Boolean> paused = new AtomicReference<>();

  boolean get() {
    return Boolean.TRUE.equals(paused.get());
  }

  /** A pause or a resume: always wins. */
  void set(boolean value) {
    paused.set(value);
  }

  /** The state read from the session at start: only kept if nothing was set since. */
  void initialize(boolean value) {
    paused.compareAndSet(null, value);
  }
}
