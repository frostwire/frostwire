/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The UI refreshes every 2 s in Lite and every second in Full (frostwire/frostwire#1555). */
class RefreshTimerTest {
  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
  }

  @Test
  void fullRefreshesEverySecond() {
    assertEquals(1000, new RefreshTimer(() -> {}).delay());
  }

  @Test
  void liteRefreshesEveryTwoSeconds() {
    PerformanceMode.resolve(Choice.LITE, null);
    assertEquals(2000, new RefreshTimer(() -> {}).delay());
  }

  @Test
  void aModeChangeAppliesFromTheNextTick() {
    AtomicInteger refreshes = new AtomicInteger();
    RefreshTimer timer = new RefreshTimer(refreshes::incrementAndGet);
    PerformanceMode.resolve(Choice.LITE, null);
    timer.tick();
    assertEquals(2000, timer.delay());
    PerformanceMode.resolve(Choice.FULL, null);
    timer.tick();
    assertEquals(1000, timer.delay());
    assertEquals(2, refreshes.get());
  }
}
