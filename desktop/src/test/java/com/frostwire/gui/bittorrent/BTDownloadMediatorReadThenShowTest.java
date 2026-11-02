/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Opening a .torrent file reads and bdecodes it, which takes seconds for a big torrent on a slow
 * disk (Icebase #08f3fa6109d9, frostwire/frostwire#1468). {@code readThenShow} reads once on the
 * background and shows the result, or the failure, on the EDT.
 */
class BTDownloadMediatorReadThenShowTest {

  private final ExecutorService background = Executors.newSingleThreadExecutor();

  @AfterEach
  void shutDown() {
    background.shutdownNow();
  }

  @Test
  void readsOnceOffTheEdtAndShowsOnTheEdt() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    AtomicBoolean readOnEdt = new AtomicBoolean(true);
    AtomicBoolean shownOnEdt = new AtomicBoolean(false);
    AtomicReference<String> shown = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    EventQueue.invokeAndWait(
        () ->
            BTDownloadMediator.readThenShow(
                () -> {
                  reads.incrementAndGet();
                  readOnEdt.set(EventQueue.isDispatchThread());
                  return "parsed";
                },
                background,
                EventQueue::invokeLater,
                result -> {
                  shownOnEdt.set(EventQueue.isDispatchThread());
                  shown.set(result);
                  done.countDown();
                },
                e -> done.countDown()));
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertEquals(1, reads.get());
    assertFalse(readOnEdt.get(), "the torrent was read on the EDT");
    assertTrue(shownOnEdt.get(), "the result was shown off the EDT");
    assertEquals("parsed", shown.get());
  }

  @Test
  void aFailedReadIsReportedOnTheEdtAndShowsNothing() throws Exception {
    IOException malformed = new IOException("malformed torrent");
    AtomicReference<Exception> reported = new AtomicReference<>();
    AtomicBoolean reportedOnEdt = new AtomicBoolean(false);
    AtomicReference<Object> shown = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    BTDownloadMediator.<Object>readThenShow(
        () -> {
          throw malformed;
        },
        background,
        EventQueue::invokeLater,
        result -> {
          shown.set(result);
          done.countDown();
        },
        e -> {
          reportedOnEdt.set(EventQueue.isDispatchThread());
          reported.set(e);
          done.countDown();
        });
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertEquals(malformed, reported.get());
    assertTrue(reportedOnEdt.get(), "the failure was reported off the EDT");
    assertNull(shown.get());
  }
}
