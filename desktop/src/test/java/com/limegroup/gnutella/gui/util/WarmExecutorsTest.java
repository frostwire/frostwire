/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The GUI's shared background executors are fed from the EDT every refresh tick. Their workers must
 * already exist and must survive idle gaps, otherwise {@code schedule()} starts an OS thread on the
 * EDT (Icebase #146d5b141956: StatusLine.updateBandwidth stalled in Thread.start).
 */
class WarmExecutorsTest {

  /** Longer than the 5 s idle timeout of the {@code ExecutorsHelper} pools. */
  private static final long IDLE_GAP_MS = 5_600;

  @Test
  void schedulingFromTheEdtNeverCreatesAThread() throws Exception {
    assertEdtSchedulingCreatesNoThread(1);
    assertEdtSchedulingCreatesNoThread(4);
  }

  private static void assertEdtSchedulingCreatesNoThread(int threads) throws Exception {
    List<Thread> creators = new CopyOnWriteArrayList<>();
    ThreadFactory recording =
        r -> {
          creators.add(Thread.currentThread());
          Thread t = new Thread(r, "warm-executors-test");
          t.setDaemon(true);
          return t;
        };
    ExecutorService pool = WarmExecutors.pool(threads, recording);
    try {
      assertEquals(threads, creators.size(), "every worker is started when the pool is built");
      assertTrue(creators.stream().allMatch(c -> c == Thread.currentThread()));

      CountDownLatch ran = new CountDownLatch(50);
      SwingUtilities.invokeAndWait(
          () -> {
            for (int i = 0; i < 50; i++) {
              pool.execute(ran::countDown);
            }
          });
      assertTrue(ran.await(5, TimeUnit.SECONDS));
      assertEquals(threads, creators.size(), "scheduling from the EDT must not start a thread");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void guiQueuesKeepTheirWorkerAcrossIdleGaps() throws Exception {
    Thread queuedBefore = workerOf(BackgroundQueuedExecutorService::schedule);
    Thread plainBefore = workerOf(BackgroundExecutorService::schedule);
    Set<Thread> parallelBefore = parallelWorkers();

    Thread.sleep(IDLE_GAP_MS);

    assertSame(queuedBefore, workerOf(BackgroundQueuedExecutorService::schedule));
    assertSame(plainBefore, workerOf(BackgroundExecutorService::schedule));
    assertTrue(queuedBefore.isAlive() && plainBefore.isAlive());
    assertEquals(parallelBefore, parallelWorkers());
  }

  private static Thread workerOf(Consumer<Runnable> schedule) throws Exception {
    CompletableFuture<Thread> worker = new CompletableFuture<>();
    schedule.accept(() -> worker.complete(Thread.currentThread()));
    return worker.get(5, TimeUnit.SECONDS);
  }

  /** Occupies all four DesktopParallelExecutor workers at once and returns them. */
  private static Set<Thread> parallelWorkers() throws Exception {
    Set<Thread> workers = ConcurrentHashMap.newKeySet();
    CountDownLatch allBusy = new CountDownLatch(4);
    CountDownLatch release = new CountDownLatch(1);
    for (int i = 0; i < 4; i++) {
      DesktopParallelExecutor.execute(
          () -> {
            workers.add(Thread.currentThread());
            allBusy.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
    }
    try {
      assertTrue(allBusy.await(5, TimeUnit.SECONDS), "four workers run at once");
    } finally {
      release.countDown();
    }
    assertEquals(4, workers.size());
    return workers;
  }
}
