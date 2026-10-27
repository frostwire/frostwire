/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Table column moves and resizes saved tables.props on the EDT once per event (a header drag fires
 * one move per column crossed). The save must run off the EDT and bursts must coalesce.
 */
class CoalescingBackgroundSaveTest {

  @Test
  void requestFromTheEdtSavesOnTheExecutor() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      AtomicReference<Thread> saver = new AtomicReference<>();
      CountDownLatch saved = new CountDownLatch(1);
      CoalescingBackgroundSave save =
          new CoalescingBackgroundSave(
              () -> {
                saver.set(Thread.currentThread());
                saved.countDown();
              },
              executor);
      AtomicReference<Thread> edt = new AtomicReference<>();

      SwingUtilities.invokeAndWait(
          () -> {
            edt.set(Thread.currentThread());
            save.request();
          });

      assertTrue(saved.await(5, TimeUnit.SECONDS));
      assertNotSame(edt.get(), saver.get(), "the props file must not be written on the EDT");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void burstCollapsesIntoOneSavePlusOneForRequestsDuringIt() {
    Queue<Runnable> queued = new ArrayDeque<>();
    AtomicInteger saves = new AtomicInteger();
    AtomicReference<CoalescingBackgroundSave> ref = new AtomicReference<>();
    CoalescingBackgroundSave save =
        new CoalescingBackgroundSave(
            () -> {
              if (saves.incrementAndGet() == 1) {
                // more column moves arrive while the first write is in progress
                ref.get().request();
                ref.get().request();
              }
            },
            queued::add);
    ref.set(save);

    for (int i = 0; i < 5; i++) {
      save.request();
    }
    assertEquals(1, queued.size(), "one background task per burst");

    queued.poll().run();
    assertEquals(2, saves.get(), "requests made during the save cause exactly one more save");
    assertTrue(queued.isEmpty());

    save.request();
    assertEquals(1, queued.size(), "the gate is released after the burst");
  }

  @Test
  void failedSaveDoesNotBlockLaterSaves() {
    Queue<Runnable> queued = new ArrayDeque<>();
    AtomicInteger attempts = new AtomicInteger();
    CoalescingBackgroundSave save =
        new CoalescingBackgroundSave(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("disk full");
              }
            },
            queued::add);

    save.request();
    queued.poll().run();
    save.request();
    queued.poll().run();

    assertEquals(2, attempts.get());
  }

  @Test
  void columnPreferencesAreSavedThroughTheBackgroundSave() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "src/main/java/com/limegroup/gnutella/gui/tables/DefaultColumnPreferenceHandler.java"))
            .replaceAll("\\s+", "");
    int start = source.indexOf("protectedvoidsave(){");
    assertTrue(start >= 0, "save() not found");
    String body = source.substring(start, source.indexOf('}', start));

    assertTrue(body.contains("TABLES_SAVE.request();"));
    assertFalse(body.contains(".save();"), "tables.props must not be written on the caller");
    assertTrue(
        source.contains("newCoalescingBackgroundSave(()->TablesHandlerSettings.instance().save()"));
    assertFalse(
        source.contains("if(changed)TablesHandlerSettings.instance().save();"),
        "column order fix-ups must use the background save too");
  }
}
