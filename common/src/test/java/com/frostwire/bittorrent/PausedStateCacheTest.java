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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * BTEngine read the session's paused state in a background thread after start and stored it over
 * whatever pause() or resume() had stored meanwhile: a pause right after start read as not paused
 * (BTEngineSessionTest.pausingIsVisibleWithoutAskingTheSession, under a loaded full test run).
 */
class PausedStateCacheTest {

  @Test
  void aLateStartValueDoesNotUndoAPause() {
    PausedStateCache cache = new PausedStateCache();

    cache.set(true); // pause() right after start
    cache.initialize(false); // the start thread's read of the session, from before the pause

    assertTrue(cache.get());
  }

  @Test
  void aLateStartValueDoesNotUndoAResume() {
    PausedStateCache cache = new PausedStateCache();

    cache.set(false);
    cache.initialize(true);

    assertFalse(cache.get());
  }

  @Test
  void theStartValueCountsWhenNothingElseWasSet() {
    PausedStateCache cache = new PausedStateCache();
    assertFalse(cache.get(), "unknown reads as not paused");

    cache.initialize(true);

    assertTrue(cache.get());
  }
}
