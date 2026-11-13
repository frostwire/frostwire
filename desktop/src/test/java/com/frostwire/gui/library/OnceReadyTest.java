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

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The header added the Library's search field before LibrarySearch had created it, so it added null
 * (Icebase #c90f930e756a, NullPointerException in ApplicationHeader.initializeSearchFields).
 */
class OnceReadyTest {

  @Test
  void workRegisteredBeforeTheValueExistsWaitsForItAndNeverSeesNull() {
    OnceReady<String> field = new OnceReady<>();
    List<String> seen = new ArrayList<>();

    field.whenReady(seen::add);
    assertNull(field.get());
    assertEquals(List.of(), seen, "nothing runs while the value does not exist");

    field.set("search field");

    assertEquals(List.of("search field"), seen);
    assertNotNull(field.get());
  }

  @Test
  void workRegisteredAfterTheValueExistsRunsAtOnce() {
    OnceReady<String> field = new OnceReady<>();
    field.set("search field");
    List<String> seen = new ArrayList<>();

    field.whenReady(seen::add);

    assertEquals(List.of("search field"), seen);
  }

  @Test
  void waitingWorkRunsOnceAndInTheOrderItWasRegistered() {
    OnceReady<Integer> field = new OnceReady<>();
    List<String> order = new ArrayList<>();
    field.whenReady(v -> order.add("first " + v));
    field.whenReady(v -> order.add("second " + v));

    field.set(1);
    field.set(2);

    assertEquals(List.of("first 1", "second 1"), order, "set() twice must not rerun old work");
  }
}
