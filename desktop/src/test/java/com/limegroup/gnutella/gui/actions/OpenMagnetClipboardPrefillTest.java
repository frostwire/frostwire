/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * File > Open .Torrent, Magnet or Video URL offers the clipboard's magnet link, but reading the
 * clipboard waits on the application that owns it, so it must not run on the EDT (GitHub #1349).
 */
class OpenMagnetClipboardPrefillTest {

  private static final String MAGNET =
      "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567";

  @Test
  void theClipboardIsReadOffTheEdtAndFillsAnEmptyField() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      AtomicBoolean readOnEdt = new AtomicBoolean();
      CountDownLatch read = new CountDownLatch(1);
      JTextField field = new JTextField();
      SwingUtilities.invokeAndWait(
          () ->
              FileMenuActions.OpenMagnetTorrentVideoUrlAction.prefillFromClipboard(
                  field,
                  () -> {
                    readOnEdt.set(SwingUtilities.isEventDispatchThread());
                    read.countDown();
                    return MAGNET;
                  },
                  worker));
      assertTrue(read.await(10, TimeUnit.SECONDS));
      flushEdt(worker);
      assertFalse(readOnEdt.get(), "the clipboard was read on the EDT");
      assertEquals(MAGNET, field.getText());
    } finally {
      worker.shutdownNow();
    }
  }

  @Test
  void whatTheUserTypedMeanwhileIsKept() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      CountDownLatch typed = new CountDownLatch(1);
      JTextField field = new JTextField();
      SwingUtilities.invokeAndWait(
          () ->
              FileMenuActions.OpenMagnetTorrentVideoUrlAction.prefillFromClipboard(
                  field,
                  () -> {
                    awaitQuietly(typed);
                    return MAGNET;
                  },
                  worker));
      SwingUtilities.invokeAndWait(() -> field.setText("https://example.test/a.torrent"));
      typed.countDown();
      flushEdt(worker);
      assertEquals("https://example.test/a.torrent", field.getText());
    } finally {
      worker.shutdownNow();
    }
  }

  @Test
  void theMenuActionNoLongerReadsTheClipboardItself() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/actions/FileMenuActions.java"))
            .replaceAll("\\s+", "");
    assertFalse(
        source.contains(
            "pathField.setText(MagnetClipboardListener.getMagnetOrTorrentURLFromClipboard())"),
        "the action reads the clipboard on the EDT again");
  }

  /** Waits for the worker's read, then for the EDT task it posted. */
  private static void flushEdt(ExecutorService worker) throws Exception {
    worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
