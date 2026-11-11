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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
  void shutdownGoesOnWhenTheShutdownWindowCannotBeShown() throws Exception {
    // Icebase #087422f6a048: Windows refused a new native window while FrostWire was closing.
    List<String> steps = new CopyOnWriteArrayList<>();
    Thread thread =
        Finalizer.shutdown(
            () -> {
              throw new InternalError("fixture: no window handle");
            },
            () -> steps.add("tray"),
            () -> steps.add("core"),
            () -> steps.add("exit"));
    thread.join(10_000);

    assertFalse(thread.isAlive());
    assertEquals(List.of("tray", "core", "exit"), steps);
  }

  @Test
  void theShutdownWindowIsShownBeforeTheShutdownThreadStarts() throws Exception {
    List<String> steps = new CopyOnWriteArrayList<>();
    Thread thread =
        Finalizer.shutdown(
            () -> steps.add("window on " + Thread.currentThread().getName()),
            () -> steps.add("tray"),
            () -> steps.add("core"),
            () -> steps.add("exit"));
    thread.join(10_000);

    assertEquals(
        List.of("window on " + Thread.currentThread().getName(), "tray", "core", "exit"), steps);
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

  /**
   * The setup wizard's Cancel and the fatal error dialog called System.exit on the EDT, which runs
   * the shutdown hooks and waits for them there (Icebase #2503b0a74dce, GitHub #1437). The exit
   * must run on its own thread, and the EDT event must return while it is still running.
   */
  @Test
  void exitRunsOffTheEdtWithoutBlockingIt() throws Exception {
    AtomicReference<Thread> edt = new AtomicReference<>();
    AtomicReference<Thread> exiter = new AtomicReference<>();
    CountDownLatch exitStarted = new CountDownLatch(1);
    CountDownLatch hooksDone = new CountDownLatch(1);
    AtomicReference<Thread> started = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          edt.set(Thread.currentThread());
          started.set(
              Finalizer.startExitThread(
                  () -> {
                    exiter.set(Thread.currentThread());
                    exitStarted.countDown();
                    try {
                      // A slow shutdown hook.
                      hooksDone.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  }));
        });

    assertTrue(exitStarted.await(5, TimeUnit.SECONDS));
    assertTrue(started.get().isAlive(), "the EDT event returned while the exit was still running");
    assertNotSame(edt.get(), exiter.get(), "System.exit must not run on the EDT");
    assertEquals("Exit Thread", exiter.get().getName());
    hooksDone.countDown();
    started.get().join(10_000);
  }

  @Test
  void dialogsThatQuitDoNotCallSystemExitOnTheEdt() throws Exception {
    Path gui =
        Path.of(System.getProperty("user.dir")).resolve("src/main/java/com/limegroup/gnutella/gui");
    for (String file : List.of("init/SetupManager.java", "bugs/FatalBugManager.java")) {
      String source = Files.readString(gui.resolve(file), StandardCharsets.UTF_8);
      assertFalse(
          source.replaceAll("\\s+", "").contains("System.exit("),
          file + " must quit through Finalizer.exitOffEdt");
    }
  }
}
