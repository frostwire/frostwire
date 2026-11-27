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

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Sorting the Time column: a shorter ETA sorts as larger, whatever the distance between them. */
class TimeRemainingHolderTest {

  private static final long FAR = 4_294_967_296L * 2; // 2^33 seconds: more than an int can hold

  @Test
  void ordersByDescendingSeconds() {
    assertTrue(new TimeRemainingHolder(10).compareTo(new TimeRemainingHolder(20)) > 0);
    assertTrue(new TimeRemainingHolder(20).compareTo(new TimeRemainingHolder(10)) < 0);
    assertEquals(0, new TimeRemainingHolder(10).compareTo(new TimeRemainingHolder(10)));
  }

  @Test
  void aDifferenceBeyondIntRangeDoesNotOverflowTheOrder() {
    TimeRemainingHolder soon = new TimeRemainingHolder(5);
    TimeRemainingHolder never = new TimeRemainingHolder(FAR);
    assertTrue(soon.compareTo(never) > 0);
    assertTrue(never.compareTo(soon) < 0);
    // 2^32 apart: a truncated difference would be 0, claiming they are equal
    TimeRemainingHolder a = new TimeRemainingHolder(1);
    TimeRemainingHolder b = new TimeRemainingHolder(1 + 4_294_967_296L);
    assertTrue(a.compareTo(b) > 0);
    assertTrue(b.compareTo(a) < 0);
  }
}
