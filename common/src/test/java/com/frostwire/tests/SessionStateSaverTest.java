/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.SessionStateSaver;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Options → Apply changes libtorrent settings on the EDT; saving the session state must not run on
 * that thread (frostwire/frostwire#1317).
 */
public class SessionStateSaverTest {

  @Test
  public void requestSaveDoesNotSaveOnTheCallingThread() throws Exception {
    List<Thread> saveThreads = new CopyOnWriteArrayList<>();
    CountDownLatch saved = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      SessionStateSaver saver =
          new SessionStateSaver(
              () -> {
                saveThreads.add(Thread.currentThread());
                saved.countDown();
              },
              executor);

      saver.requestSave();

      assertTrue(saved.await(5, TimeUnit.SECONDS), "the requested save never ran");
      assertEquals(1, saveThreads.size());
      assertNotSame(Thread.currentThread(), saveThreads.get(0));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void requestsMadeWhileASaveIsQueuedShareOneSave() {
    Queue<Runnable> queued = new ArrayDeque<>();
    int[] saves = {0};
    SessionStateSaver saver = new SessionStateSaver(() -> saves[0]++, queued::add);

    // One Apply that changes several settings
    saver.requestSave();
    saver.requestSave();
    saver.requestSave();

    assertEquals(0, saves[0], "saved on the thread that applied the setting");
    assertEquals(1, queued.size());
    queued.poll().run();
    assertEquals(1, saves[0]);

    // A later Apply saves again
    saver.requestSave();
    assertEquals(1, queued.size());
    queued.poll().run();
    assertEquals(2, saves[0]);
  }

  @Test
  public void saveAndStopSavesOnTheStoppingThreadAndDropsQueuedSaves() {
    Queue<Runnable> queued = new ArrayDeque<>();
    List<Thread> saveThreads = new CopyOnWriteArrayList<>();
    SessionStateSaver saver =
        new SessionStateSaver(() -> saveThreads.add(Thread.currentThread()), queued::add);

    saver.requestSave();
    saver.saveAndStop();

    assertEquals(1, saveThreads.size());
    assertSame(Thread.currentThread(), saveThreads.get(0));
    queued.poll().run();
    assertEquals(1, saveThreads.size(), "a queued save touched the stopped session");

    saver.start();
    saver.requestSave();
    queued.poll().run();
    assertEquals(2, saveThreads.size());
  }
}
