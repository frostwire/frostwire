/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * HTTP-backed transfers pick a free file name on disk in their constructors, so they are built off
 * the EDT and only added to the table on it (Icebase #3d3df2cf8612).
 */
class BTDownloadMediatorBuildOffEdtTest {

  @Test
  void buildsOnTheBackgroundAndAddsOnTheEdt() throws Exception {
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      AtomicBoolean builtOnEdt = new AtomicBoolean(true);
      AtomicBoolean addedOnEdt = new AtomicBoolean(false);
      AtomicReference<String> added = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      EventQueue.invokeAndWait(
          () ->
              BTDownloadMediator.buildThenAdd(
                  () -> {
                    builtOnEdt.set(EventQueue.isDispatchThread());
                    return "transfer";
                  },
                  background,
                  EventQueue::invokeLater,
                  t -> {
                    addedOnEdt.set(EventQueue.isDispatchThread());
                    added.set(t);
                    done.countDown();
                  }));
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertFalse(builtOnEdt.get(), "the transfer was built on the EDT");
      assertTrue(addedOnEdt.get(), "the transfer was added off the EDT");
      assertEquals("transfer", added.get());
    } finally {
      background.shutdownNow();
    }
  }

  /**
   * The mediator's own sources construct these transfers only inside {@code addBuiltOffEdt}: built
   * inside an EDT callback again, the disk checks would be back on the EDT.
   */
  @Test
  void httpBackedTransfersAreOnlyBuiltThroughAddBuiltOffEdt() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediator.java"),
            StandardCharsets.UTF_8);
    int sites = 0;
    for (String type : List.of("HttpDownload", "SoundcloudDownload", "SlideDownload")) {
      int at = source.indexOf("new " + type + "(");
      while (at >= 0) {
        sites++;
        int offEdt = source.lastIndexOf("addBuiltOffEdt(", at);
        int onEdt =
            Math.max(
                source.lastIndexOf("invokeLater(", at), source.lastIndexOf("invokeAndWait(", at));
        assertTrue(offEdt > onEdt, "new " + type + " is not built through addBuiltOffEdt");
        at = source.indexOf("new " + type + "(", at + 1);
      }
    }
    assertEquals(4, sites, "HTTP, HTTP with muxed audio, SoundCloud and slide downloads");
  }
}
