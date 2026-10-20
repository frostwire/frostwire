/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.util.Logger;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opens an icebridge:// URL the user copied when they come back to FrostWire.
 *
 * <p>Window activation is an EDT event, and reading the system clipboard can block for seconds on
 * Windows (another application holding it open, delayed rendering), so the read runs on {@code
 * worker}. At most one read is in flight; activations during a read are skipped.
 */
final class IceBridgeClipboardMonitor extends WindowAdapter {
  private static final Logger LOG = Logger.getLogger(IceBridgeClipboardMonitor.class);
  private static final String SCHEME = "icebridge://";

  private final Executor worker;
  private final Supplier<String> clipboardText;
  private final Runnable clearClipboard;
  private final Consumer<String> onIceBridgeUrl;
  private final AtomicBoolean reading = new AtomicBoolean();
  private volatile String lastClipboardText;

  /**
   * @param clipboardText reads the clipboard's text, or null; called on {@code worker}
   * @param clearClipboard empties the clipboard so the URL opens once; called on {@code worker}
   * @param onIceBridgeUrl receives the URL; called on {@code worker}
   */
  IceBridgeClipboardMonitor(
      Executor worker,
      Supplier<String> clipboardText,
      Runnable clearClipboard,
      Consumer<String> onIceBridgeUrl) {
    this.worker = worker;
    this.clipboardText = clipboardText;
    this.clearClipboard = clearClipboard;
    this.onIceBridgeUrl = onIceBridgeUrl;
  }

  @Override
  public void windowActivated(WindowEvent e) {
    if (!reading.compareAndSet(false, true)) {
      return;
    }
    try {
      worker.execute(this::checkClipboard);
    } catch (RejectedExecutionException rejected) {
      reading.set(false);
      LOG.debug("IceBridge clipboard check skipped: background pool is busy");
    }
  }

  private void checkClipboard() {
    try {
      String text = clipboardText.get();
      if (text == null) {
        return;
      }
      text = text.trim();
      if (text.isEmpty() || text.equals(lastClipboardText)) {
        return;
      }
      lastClipboardText = text;
      if (text.toLowerCase(Locale.ROOT).startsWith(SCHEME)) {
        try {
          clearClipboard.run();
        } catch (Throwable ignored) {
        }
        onIceBridgeUrl.accept(text);
      }
    } catch (Throwable t) {
      LOG.debug("IceBridge clipboard monitor error", t);
    } finally {
      reading.set(false);
    }
  }
}
