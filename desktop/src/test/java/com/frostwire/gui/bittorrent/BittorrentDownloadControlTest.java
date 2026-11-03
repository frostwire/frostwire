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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Pausing or resuming a transfer calls into libtorrent and starts the resume-data thread, and the
 * UI does it on the EDT (frostwire/frostwire#1476, Icebase #82c09c78ce83). BittorrentDownload hands
 * those calls to one background worker.
 */
class BittorrentDownloadControlTest {

  @Test
  void controlsQueuedFromTheEdtRunOffItInOrderAndTheFollowUpRunsOnTheEdtAfterThem()
      throws Exception {
    List<String> events = new CopyOnWriteArrayList<>();
    CountDownLatch clickReturned = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(
        () -> {
          // Run inline, this would wait out its timeout on the EDT before the click returned.
          BittorrentDownload.control(
              () ->
                  events.add(
                      "pause after click="
                          + await(clickReturned)
                          + " on EDT="
                          + EventQueue.isDispatchThread()));
          BittorrentDownload.control(
              () -> events.add("resume on EDT=" + EventQueue.isDispatchThread()));
          BittorrentDownload.afterQueuedControls(
              () -> {
                events.add("filters on EDT=" + EventQueue.isDispatchThread());
                done.countDown();
              });
          clickReturned.countDown();
        });
    assertTrue(done.await(10, TimeUnit.SECONDS), "the EDT follow-up never ran");
    assertEquals(
        List.of(
            "pause after click=true on EDT=false", "resume on EDT=false", "filters on EDT=true"),
        events);
  }

  private static boolean await(CountDownLatch latch) {
    try {
      return latch.await(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Test
  void pauseAndResumeGoThroughTheControlWorker() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/frostwire/gui/bittorrent/BittorrentDownload.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(source.contains("publicvoidpause(){control(dl::pause);}"), "pause() runs inline");
    assertTrue(source.contains("publicvoidresume(){control(dl::resume);}"), "resume() runs inline");
    assertFalse(source.contains("publicvoidpause(){dl.pause();}"));
    assertFalse(source.contains("publicvoidresume(){dl.resume();}"));
  }
}
