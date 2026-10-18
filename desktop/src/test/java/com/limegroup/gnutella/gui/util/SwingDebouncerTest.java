/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SwingDebouncerTest {

  @Test
  void aBurstOfTriggersRunsTheActionOnceOnTheEdt() throws Exception {
    AtomicInteger runs = new AtomicInteger();
    AtomicBoolean ranOnEdt = new AtomicBoolean();
    CountDownLatch firstRun = new CountDownLatch(1);
    SwingDebouncer debouncer =
        new SwingDebouncer(
            50,
            () -> {
              runs.incrementAndGet();
              ranOnEdt.set(EventQueue.isDispatchThread());
              firstRun.countDown();
            });

    SwingUtilities.invokeAndWait(
        () -> {
          for (int i = 0; i < 10; i++) {
            debouncer.trigger();
          }
        });

    assertTrue(firstRun.await(5, TimeUnit.SECONDS));
    Thread.sleep(200); // no further runs may follow the burst
    assertEquals(1, runs.get());
    assertTrue(ranOnEdt.get());

    SwingUtilities.invokeAndWait(debouncer::trigger);
    Thread.sleep(300);
    assertEquals(2, runs.get(), "a later trigger runs the action again");
  }
}
