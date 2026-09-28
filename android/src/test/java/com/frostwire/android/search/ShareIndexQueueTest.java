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

import com.frostwire.search.relay.LocalIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Retry/reconcile lifecycle of the Android share index, without native handles. */
public class ShareIndexQueueTest {

  private static final class Harness {
    final List<Runnable> delayed = new ArrayList<>();
    final List<Long> delays = new ArrayList<>();
    final List<String> indexedWrites = new ArrayList<>();
    final AtomicInteger checks = new AtomicInteger();
    final AtomicInteger changes = new AtomicInteger();
    final AtomicBoolean shareable = new AtomicBoolean();
    final ShareIndexQueue<String> queue;

    Harness() {
      queue =
          new ShareIndexQueue<>(
              mock(LocalIndex.class),
              dl -> {
                checks.incrementAndGet();
                return shareable.get();
              },
              indexedWrites::add,
              dl -> dl,
              Runnable::run,
              (task, delayMs) -> {
                delays.add(delayMs);
                delayed.add(task);
              });
      queue.setIndexChangedListener(changes::incrementAndGet);
    }

    void runNextDelayed() {
      delayed.remove(0).run();
    }
  }

  @Test
  public void seedPausedAtAddIsIndexedOnceItResumes() {
    // A torrent built from a finished YouTube download is added paused; its only engine
    // callback sees it paused. It must still become searchable once it resumes seeding.
    Harness h = new Harness();
    h.queue.submit("big-in-japan");
    assertTrue("paused transfer is not indexed yet", h.indexedWrites.isEmpty());
    assertEquals(1, h.delayed.size());

    h.shareable.set(true); // TorrentUtils resumed the handle
    h.runNextDelayed();

    assertEquals(List.of("big-in-japan"), h.indexedWrites);
    assertEquals(1, h.changes.get());
    assertTrue("no further retries once indexed", h.delayed.isEmpty());
  }

  @Test
  public void retriesAreBoundedForTransfersThatStayUnshareable() {
    Harness h = new Harness();
    h.queue.submit("user-paused");
    while (!h.delayed.isEmpty()) {
      h.runNextDelayed();
    }
    assertEquals(ShareIndexQueue.RETRY_DELAYS_MS.length + 1, h.checks.get());
    assertEquals(ShareIndexQueue.RETRY_DELAYS_MS.length, h.delays.size());
    for (int i = 0; i < h.delays.size(); i++) {
      assertEquals(ShareIndexQueue.RETRY_DELAYS_MS[i], (long) h.delays.get(i));
    }
    assertTrue(h.indexedWrites.isEmpty());
  }

  @Test
  public void reconcileIndexesTransfersThatBecameShareableWithoutACallback() {
    Harness h = new Harness();
    h.queue.reconcile(() -> List.of("resumed-later"));
    assertTrue(h.indexedWrites.isEmpty());
    assertTrue("the periodic sweep does not start per-item retries", h.delayed.isEmpty());

    h.shareable.set(true);
    h.queue.reconcile(() -> List.of("resumed-later"));
    h.queue.reconcile(() -> List.of("resumed-later"));

    assertEquals("already-indexed transfers are not rewritten", 1, h.indexedWrites.size());
  }

  @Test
  public void closeCancelsPendingRetries() {
    Harness h = new Harness();
    h.queue.submit("closing");
    h.queue.close();
    h.shareable.set(true);
    h.runNextDelayed();
    assertTrue(h.indexedWrites.isEmpty());
  }
}
