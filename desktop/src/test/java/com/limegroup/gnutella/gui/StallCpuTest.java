/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Stall reports say where the CPU went while the EDT was stalled (frostwire/frostwire#1512): six
 * reports from one Windows 7 machine stopped in cheap frames with no GC, and couldn't tell a busy
 * EDT from a starved one or a paused process.
 */
class StallCpuTest {
  private static final long MS = TimeUnit.MILLISECONDS.toNanos(1);

  @Test
  void busiestGroupsThreadsByNameMostCpuFirst() {
    String[] names = {
      "SearchManager-crawlExecutor-thread-1",
      "SearchManager-crawlExecutor-thread-2",
      "AWT-EventQueue-0",
      "SuggestionsThread: a private search",
      "idle",
      null
    };
    long[] cpu = {1_200 * MS, 1_000 * MS, 40 * MS, 300 * MS, 0, 9_000 * MS};

    List<StallCpu.ThreadCpu> busiest = StallCpu.busiest(names, cpu);

    assertEquals(3, busiest.size());
    assertEquals("SearchManager-crawlExecutor-thread-N", busiest.get(0).name());
    assertEquals(2_200, busiest.get(0).cpuMs());
    // Names are cut like the thread census, so no search text leaves the machine.
    assertEquals("SuggestionsThread", busiest.get(1).name());
    assertEquals("AWT-EventQueue-N", busiest.get(2).name());
  }

  @Test
  void atMostFiveGroups() {
    String[] names = {"a", "b", "c", "d", "e", "f", "g"};
    long[] cpu = {7 * MS, 6 * MS, 5 * MS, 4 * MS, 3 * MS, 2 * MS, 1 * MS};
    List<StallCpu.ThreadCpu> busiest = StallCpu.busiest(names, cpu);
    assertEquals(5, busiest.size());
    assertEquals("e", busiest.get(4).name());
  }

  @Test
  void aMissingReadingIsLeftOut() {
    assertNull(StallCpu.deltaMs(-1, 5 * MS));
    assertNull(StallCpu.deltaMs(5 * MS, -1));
    assertEquals(2_500, StallCpu.deltaMs(1_000 * MS, 3_500 * MS));
  }

  @Test
  void measuresTheCpuThisThreadBurnsInTheWindow() {
    Thread self = Thread.currentThread();
    StallCpu.Snapshot start = StallCpu.take(self);
    assertNotNull(start);
    long until = System.nanoTime() + 300 * MS;
    long spin = 0;
    while (System.nanoTime() < until) {
      spin++;
    }
    StallCpu.Window window = StallCpu.since(start, self);
    assertNotNull(window);
    assertTrue(spin > 0);
    assertNotNull(window.edtCpuMs(), "thread CPU time is supported on HotSpot");
    assertTrue(window.edtCpuMs() >= 100, "spun ~300 ms, measured " + window.edtCpuMs());
    assertTrue(window.processCpuMs() >= window.edtCpuMs() - 20);
    assertTrue(
        window.busiestThreads().stream().anyMatch(t -> t.cpuMs() >= 100),
        "the spinning thread is among the busiest");
  }

  @Test
  void noStartNoWindow() {
    assertNull(StallCpu.since(null, Thread.currentThread()));
  }
}
