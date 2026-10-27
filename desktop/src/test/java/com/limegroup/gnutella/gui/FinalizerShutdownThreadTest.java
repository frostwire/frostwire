/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Finalizer.shutdown removed the tray icon natively (WTrayIconPeer._dispose) on the EDT before
 * starting the shutdown thread. The removal must run on the shutdown thread, before the core shuts
 * down, and the process must still exit when a step fails.
 */
@Timeout(15)
class FinalizerShutdownThreadTest {

  @Test
  void trayIconIsRemovedOnTheShutdownThreadBeforeTheCoreStops() throws Exception {
    AtomicReference<Thread> edt = new AtomicReference<>();
    AtomicReference<Thread> trayRemover = new AtomicReference<>();
    AtomicReference<Thread> started = new AtomicReference<>();
    List<String> steps = new CopyOnWriteArrayList<>();

    SwingUtilities.invokeAndWait(
        () -> {
          edt.set(Thread.currentThread());
          started.set(
              Finalizer.startShutdownThread(
                  () -> {
                    trayRemover.set(Thread.currentThread());
                    steps.add("tray");
                  },
                  () -> steps.add("core"),
                  () -> steps.add("exit")));
        });
    started.get().join(10_000);

    assertFalse(started.get().isAlive());
    assertNotSame(edt.get(), trayRemover.get(), "the tray icon must not be removed on the EDT");
    assertEquals("Shutdown Thread", trayRemover.get().getName());
    assertEquals(List.of("tray", "core", "exit"), steps);
  }

  @Test
  void failingStepsStillExit() throws Exception {
    List<String> steps = new CopyOnWriteArrayList<>();
    Thread thread =
        Finalizer.startShutdownThread(
            () -> {
              throw new IllegalStateException("fixture tray failure");
            },
            () -> steps.add("core"),
            () -> steps.add("exit"));
    thread.join(10_000);
    assertEquals(List.of("core", "exit"), steps);

    steps.clear();
    thread =
        Finalizer.startShutdownThread(
            () -> {},
            () -> {
              throw new IllegalStateException("fixture core failure");
            },
            () -> steps.add("exit"));
    thread.join(10_000);
    assertEquals(List.of("exit"), steps);
  }
}
