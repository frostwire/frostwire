/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Copy actions run on the EDT, and on Windows opening the clipboard waits while another program
 * holds it (Icebase #8e8c062251e8, frostwire/frostwire#1579). The write must happen on a worker.
 */
class GUIMediatorClipboardTest {

  private static final class RecordingClipboard extends Clipboard {
    final CountDownLatch written = new CountDownLatch(1);
    final AtomicReference<Thread> writer = new AtomicReference<>();
    final AtomicReference<String> text = new AtomicReference<>();

    RecordingClipboard() {
      super("test");
    }

    @Override
    public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
      writer.set(Thread.currentThread());
      try {
        text.set((String) contents.getTransferData(DataFlavor.stringFlavor));
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      written.countDown();
    }
  }

  @Test
  void theWriteToTheClipboardNeverRunsOnTheEdt() throws Exception {
    RecordingClipboard clipboard = new RecordingClipboard();
    EventQueue.invokeAndWait(
        () -> GUIMediator.setClipboardContentAsync("magnet:?xt=1", () -> clipboard));
    assertTrue(clipboard.written.await(10, TimeUnit.SECONDS), "the text was never written");
    assertEquals("magnet:?xt=1", clipboard.text.get());
    assertFalse(EventQueue.isDispatchThread());
    assertFalse(
        clipboard.writer.get().getName().startsWith("AWT-EventQueue"),
        "written on " + clipboard.writer.get().getName());
  }

  @Test
  void theSynchronousOverloadWritesTheText() {
    RecordingClipboard clipboard = new RecordingClipboard();
    GUIMediator.setClipboardContent("abc", clipboard);
    assertEquals("abc", clipboard.text.get());
  }
}
