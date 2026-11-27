/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.frostwire.search.relay.LocalIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * The write side of the share index queue: withdrawals, a transfer removed while it was being
 * indexed, listener failures, a full executor, and the owned scheduler's reconcile sweep.
 */
public class ShareIndexQueueWritesTest {

  private final LocalIndex index = mock(LocalIndex.class);
  private final List<String> written = new ArrayList<>();
  private final List<Runnable> delayed = new ArrayList<>();
  private final AtomicBoolean shareable = new AtomicBoolean(true);

  private ShareIndexQueue<String> queue(Executor executor) {
    return new ShareIndexQueue<>(
        index,
        dl -> shareable.get(),
        written::add,
        dl -> dl,
        executor,
        (task, delayMs) -> delayed.add(task));
  }

  @Test
  public void aTransferRemovedWhileBeingIndexedIsDeletedAgain() {
    ShareIndexQueue<String> q =
        new ShareIndexQueue<>(
            index,
            dl -> shareable.get(),
            dl -> shareable.set(false), // removed during the write
            dl -> dl,
            Runnable::run,
            (task, delayMs) -> {});
    q.submit("gone");
    verify(index).delete("gone");
  }

  @Test
  public void withdrawDeletesTheRowOnlyWhileTheTransferIsStillRemoved() {
    ShareIndexQueue<String> q = queue(Runnable::run);
    AtomicInteger changes = new AtomicInteger();
    q.setIndexChangedListener(changes::incrementAndGet);

    q.withdraw("h1", () -> true);
    q.withdraw("h2", () -> false);

    verify(index).delete("h1");
    verify(index, never()).delete("h2");
    assertEquals(1, changes.get());
  }

  @Test
  public void withdrawSurvivesAFailingIndexAndAFailingListener() {
    ShareIndexQueue<String> q = queue(Runnable::run);
    org.mockito.Mockito.doThrow(new IllegalStateException("sqlite busy")).when(index).delete("x");
    q.withdraw("x", () -> true);

    q.setIndexChangedListener(
        () -> {
          throw new IllegalStateException("listener");
        });
    q.withdraw("y", () -> true);
    verify(index).delete("y");
  }

  @Test
  public void withdrawAndSubmitAreIgnoredOnceClosedOrWhenTheExecutorIsFull() {
    Executor full =
        task -> {
          throw new RejectedExecutionException("full");
        };
    ShareIndexQueue<String> q = queue(full);
    q.withdraw("a", () -> true);
    q.submit("b");
    verify(index, never()).delete("a");
    assertTrue(written.isEmpty());

    ShareIndexQueue<String> closed = queue(Runnable::run);
    closed.close();
    closed.withdraw("c", () -> true);
    closed.submit("d");
    closed.reconcile(() -> List.of("e"));
    verify(index, never()).delete("c");
    assertTrue(written.isEmpty());
  }

  @Test
  public void aFailingIndexWriteIsContainedAndTheTransferCanBeRetriedLater() {
    AtomicInteger attempts = new AtomicInteger();
    ShareIndexQueue<String> q =
        new ShareIndexQueue<>(
            index,
            dl -> true,
            dl -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("native metadata busy");
              }
              written.add(dl);
            },
            dl -> dl,
            Runnable::run,
            (task, delayMs) -> {});
    q.submit("t");
    assertTrue(written.isEmpty());
    q.submit("t");
    assertEquals(List.of("t"), written);
  }

  @Test
  public void anIndexedTransferIsNotWrittenTwice() {
    ShareIndexQueue<String> q = queue(Runnable::run);
    String download = new String("same");
    q.submit(download);
    q.submit(download);
    assertEquals(1, written.size());
  }

  @Test
  public void reconcileToleratesANullOrFailingSupplier() {
    ShareIndexQueue<String> q = queue(Runnable::run);
    q.reconcile(() -> null);
    q.reconcile(
        () -> {
          throw new IllegalStateException("transfers busy");
        });
    assertTrue(written.isEmpty());
  }

  @Test
  public void theTestConstructorHasNoSweepOfItsOwn() {
    ShareIndexQueue<String> q = queue(Runnable::run);
    q.startReconcile(() -> List.of("never"));
    assertTrue(written.isEmpty());
    q.close();
  }

  @Test
  public void theOwnedSchedulerRetriesAndTheSweepIsStartedAndStopped() throws Exception {
    CountDownLatch indexed = new CountDownLatch(1);
    AtomicInteger writes = new AtomicInteger();
    ShareIndexQueue<String> q =
        new ShareIndexQueue<>(
            index,
            dl -> true,
            dl -> {
              writes.incrementAndGet();
              indexed.countDown();
            },
            dl -> dl,
            Runnable::run);
    q.startReconcile(() -> List.of("a"));
    q.startReconcile(null);
    q.submit("a");
    assertTrue(indexed.await(5, TimeUnit.SECONDS));
    q.close();
    // After close the owned scheduler refuses new work without throwing.
    q.startReconcile(() -> List.of("b"));
    q.submit("b");
    assertEquals("nothing is indexed after close", 1, writes.get());
  }
}
