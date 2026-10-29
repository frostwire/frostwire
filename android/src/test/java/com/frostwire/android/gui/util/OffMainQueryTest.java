/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * OffMainQuery runs handle queries off the main thread, one at a time (frostwire/frostwire#1373).
 */
public class OffMainQueryTest {

  private final List<Runnable> background = new ArrayList<>();
  private final List<Runnable> ui = new ArrayList<>();
  private final OffMainQuery<String> query = new OffMainQuery<>(background::add, ui::add);

  @Test
  public void queriesInTheBackgroundAndDeliversOnTheUiThread() {
    List<String> delivered = new ArrayList<>();
    List<String> queried = new ArrayList<>();
    query.run(
        () -> {
          queried.add("q");
          return "result";
        },
        delivered::add);
    assertTrue("queried on the calling thread", queried.isEmpty());
    background.remove(0).run();
    assertEquals(1, queried.size());
    assertTrue("delivered off the UI executor", delivered.isEmpty());
    ui.remove(0).run();
    assertEquals(List.of("result"), delivered);
  }

  @Test
  public void dropsRequestsWhileOneIsInFlight() {
    List<String> delivered = new ArrayList<>();
    query.run(() -> "first", delivered::add);
    query.run(() -> "second", delivered::add);
    assertEquals(1, background.size());
    background.remove(0).run();
    ui.remove(0).run();
    query.run(() -> "third", delivered::add);
    assertEquals(1, background.size());
    background.remove(0).run();
    ui.remove(0).run();
    assertEquals(List.of("first", "third"), delivered);
  }

  @Test
  public void aFailedQueryAllowsTheNextOne() {
    query.run(
        () -> {
          throw new IllegalStateException("boom");
        },
        r -> {});
    background.remove(0).run();
    assertTrue(ui.isEmpty());
    query.run(() -> "next", r -> {});
    assertEquals(1, background.size());
  }
}
