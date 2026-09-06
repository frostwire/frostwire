/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class LifecycleManagerImplTest {
  @Test
  void shutdownStartsAllOwnersAndDrainsBeforeNativeStopExactlyOnce() {
    AtomicInteger stops = new AtomicInteger();
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger drains = new AtomicInteger();
    LifecycleManagerImpl manager =
        startedManager(
            () -> {
              assertEquals(2, drains.get());
              stops.incrementAndGet();
            });
    Thread first =
        item(
            () -> {
              entered.countDown();
              await(release);
              drains.incrementAndGet();
            });
    Thread second =
        item(
            () -> {
              // Reentrant registration must be rejected without blocking on a join-held monitor.
              assertThrows(
                  IllegalStateException.class, () -> manager.addShutdownItem(item(() -> {})));
              entered.countDown();
              await(release);
              drains.incrementAndGet();
            });
    manager.addShutdownItem(first);
    manager.addShutdownItem(first);
    manager.addShutdownItem(second);
    FutureTask<Void> shutdown = shutdown(manager);
    try {
      await(entered);
      assertEquals(0, stops.get());
      manager.shutdown();
      assertEquals(0, stops.get());
    } finally {
      release.countDown();
      get(shutdown);
      join(first);
      join(second);
    }
    assertEquals(1, stops.get());
    manager.shutdown();
    assertEquals(1, stops.get());
  }

  @Test
  void timedOutDrainRetainsNativeSessionAndDoesNotRetryDisposal() {
    AtomicInteger stops = new AtomicInteger();
    LifecycleManagerImpl manager = startedManager(stops::incrementAndGet);
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    Thread first =
        item(
            () -> {
              entered.countDown();
              await(release);
            });
    Thread second =
        item(
            () -> {
              entered.countDown();
              await(release);
            });
    manager.addShutdownItem(first);
    manager.addShutdownItem(second);
    long before = System.nanoTime();
    FutureTask<Void> shutdown = shutdown(manager);
    try {
      await(entered);
      get(shutdown);
      assertTrue(
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 9000,
          "The join budget is shared, not five seconds per owner");
      assertEquals(0, stops.get());
    } finally {
      release.countDown();
      join(first);
      join(second);
    }
    manager.shutdown();
    assertEquals(0, stops.get());
  }

  @Test
  void failedOwnerStillRevokesOtherOwnersAndRetainsNativeSession() {
    AtomicInteger stops = new AtomicInteger();
    AtomicBoolean revoked = new AtomicBoolean();
    LifecycleManagerImpl manager = startedManager(stops::incrementAndGet);
    manager.addShutdownItem(
        item(
            () -> {
              throw new IllegalStateException("fixture failure");
            }));
    manager.addShutdownItem(item(() -> revoked.set(true)));
    manager.shutdown();
    assertTrue(revoked.get());
    assertEquals(0, stops.get());
  }

  @Test
  void interruptedShutdownRetainsNativeSessionAndInterruptStatus() {
    AtomicInteger stops = new AtomicInteger();
    LifecycleManagerImpl manager = startedManager(stops::incrementAndGet);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread owner =
        item(
            () -> {
              entered.countDown();
              await(release);
            });
    manager.addShutdownItem(owner);
    AtomicBoolean interrupted = new AtomicBoolean();
    Thread shutdown =
        new Thread(
            () -> {
              manager.shutdown();
              interrupted.set(Thread.currentThread().isInterrupted());
            });
    shutdown.start();
    try {
      await(entered);
      shutdown.interrupt();
      join(shutdown);
      assertTrue(interrupted.get());
      assertEquals(0, stops.get());
    } finally {
      release.countDown();
      join(owner);
    }
  }

  @Test
  void shutdownBeforeCoreStartStillRevokesOwnersAndRejectsLateRegistration() {
    AtomicBoolean revoked = new AtomicBoolean();
    AtomicInteger stops = new AtomicInteger();
    LifecycleManagerImpl manager = new LifecycleManagerImpl(null, stops::incrementAndGet);
    manager.addShutdownItem(item(() -> revoked.set(true)));
    manager.shutdown();
    assertTrue(revoked.get());
    assertEquals(0, stops.get());
    assertThrows(IllegalStateException.class, () -> manager.addShutdownItem(item(() -> {})));
    manager.start();
    assertDoesNotThrow(
        () -> {
          var begin = LifecycleManagerImpl.class.getDeclaredField("startBegin");
          begin.setAccessible(true);
          assertFalse(((AtomicBoolean) begin.get(manager)).get());
        });
  }

  @Test
  void shutdownItemStartFailureDoesNotPreventOtherRevocationOrDisposeNativeSession() {
    AtomicInteger stops = new AtomicInteger();
    AtomicBoolean revoked = new AtomicBoolean();
    LifecycleManagerImpl manager = startedManager(stops::incrementAndGet);
    Thread failed =
        new Thread() {
          @Override
          public synchronized void start() {
            throw new IllegalStateException("fixture start failure");
          }
        };
    manager.addShutdownItem(failed);
    manager.addShutdownItem(item(() -> revoked.set(true)));
    manager.shutdown();
    assertTrue(revoked.get());
    assertEquals(0, stops.get());
  }

  @Test
  void registrationRejectsNullAndPreviouslyStartedThreads() {
    LifecycleManagerImpl manager = new LifecycleManagerImpl(null, () -> {});
    assertThrows(NullPointerException.class, () -> manager.addShutdownItem(null));
    Thread completed = item(() -> {});
    completed.start();
    join(completed);
    assertThrows(IllegalArgumentException.class, () -> manager.addShutdownItem(completed));
  }

  private static LifecycleManagerImpl startedManager(Runnable stopEngine) {
    LifecycleManagerImpl manager = new LifecycleManagerImpl(null, stopEngine);
    // Avoid unrelated app startup, settings, services and native session creation.
    assertDoesNotThrow(
        () -> {
          var begin = LifecycleManagerImpl.class.getDeclaredField("startBegin");
          begin.setAccessible(true);
          ((AtomicBoolean) begin.get(manager)).set(true);
          var latch = LifecycleManagerImpl.class.getDeclaredField("startLatch");
          latch.setAccessible(true);
          ((CountDownLatch) latch.get(manager)).countDown();
        });
    return manager;
  }

  private static Thread item(Runnable action) {
    Thread thread = new Thread(action, "shutdown-owner-test");
    thread.setDaemon(true);
    return thread;
  }

  private static FutureTask<Void> shutdown(LifecycleManagerImpl manager) {
    FutureTask<Void> task =
        new FutureTask<>(
            () -> {
              manager.shutdown();
              return null;
            });
    new Thread(task, "shutdown-manager-test").start();
    return task;
  }

  private static void await(CountDownLatch latch) {
    assertDoesNotThrow(() -> assertTrue(latch.await(10, TimeUnit.SECONDS)));
  }

  private static void get(FutureTask<Void> task) {
    assertDoesNotThrow(() -> task.get(10, TimeUnit.SECONDS));
  }

  private static void join(Thread thread) {
    assertDoesNotThrow(() -> thread.join(10000));
    assertFalse(thread.isAlive());
  }
}
