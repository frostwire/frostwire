/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.concurrent.concurrent.ThreadExecutor;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Pressing Enter in the search box hands work from the EDT to two background executors: the search
 * queue that starts the engines' performers, and ThreadExecutor, which re-sorts the new result
 * table. Their workers used to die after 5 idle seconds, so the next search (nearly always more
 * than 5 s after the last one) made the EDT start an OS thread first. Thread.start waits on the JVM
 * and the OS and stalled the EDT for over 5 s (Icebase #c85877617043, frostwire/frostwire#1332;
 * same cause as #1314). Both executors must reuse the same live worker across idle gaps.
 */
class SearchWorkersStayWarmTest {

  /** Longer than the 5 s idle timeout of the {@code ExecutorsHelper} pools. */
  private static final long IDLE_GAP_MS = 5_600;

  @Test
  void searchQueueKeepsItsWorkerAcrossIdleGaps() throws Exception {
    ExecutorService searchQueue = searchQueue();
    assertKeepsItsWorkerAcrossIdleGaps(searchQueue::execute);
  }

  @Test
  void threadExecutorKeepsItsWorkerAcrossIdleGaps() throws Exception {
    assertKeepsItsWorkerAcrossIdleGaps(
        r -> ThreadExecutor.startThread(r, "SearchWorkersStayWarmTest"));
  }

  private static void assertKeepsItsWorkerAcrossIdleGaps(Consumer<Runnable> schedule)
      throws Exception {
    Thread before = workerOf(schedule);
    Thread.sleep(IDLE_GAP_MS);
    assertTrue(before.isAlive(), "the worker must survive an idle gap");
    assertSame(before, workerOf(schedule));
  }

  private static ExecutorService searchQueue() throws Exception {
    Field field = SearchMediator.class.getDeclaredField("SEARCH_QUEUE");
    field.setAccessible(true);
    return (ExecutorService) field.get(null);
  }

  private static Thread workerOf(Consumer<Runnable> schedule) throws Exception {
    CompletableFuture<Thread> worker = new CompletableFuture<>();
    schedule.accept(() -> worker.complete(Thread.currentThread()));
    return worker.get(5, TimeUnit.SECONDS);
  }
}
