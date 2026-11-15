/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Copy Magnet built the magnet link on the EDT, and libtorrent makes one by asking its session
 * thread for the torrent's metadata, so the window froze until the session was free (Icebase
 * #1ad3acfaed4f, frostwire/frostwire#1561). The magnet is now built on a background worker and only
 * the clipboard is set on the EDT.
 */
@Timeout(15)
class CopyMagnetOffEdtTest {

  @Test
  void theMagnetIsBuiltOffTheEdtAndCopiedOnIt() throws Exception {
    AtomicReference<Thread> magnetThread = new AtomicReference<>();
    AtomicBoolean builtOnEdt = new AtomicBoolean();
    BTDownload first = download("magnet:?xt=urn:btih:first", magnetThread, builtOnEdt);
    BTDownload noMagnet = download(null, magnetThread, builtOnEdt);
    BTDownload second = download("magnet:?xt=urn:btih:second", magnetThread, builtOnEdt);
    AtomicReference<String> copied = new AtomicReference<>();
    AtomicBoolean copiedOnEdt = new AtomicBoolean();
    CountDownLatch done = new CountDownLatch(1);

    // Clicked on the EDT, as the menu action is.
    SwingUtilities.invokeAndWait(
        () ->
            BTDownloadActions.copyMagnets(
                new BTDownload[] {first, noMagnet, second},
                text -> {
                  copiedOnEdt.set(SwingUtilities.isEventDispatchThread());
                  copied.set(text);
                  done.countDown();
                }));

    assertTrue(done.await(10, TimeUnit.SECONDS), "the magnets are copied");
    assertNotNull(magnetThread.get());
    assertFalse(
        builtOnEdt.get(),
        "the magnet must not be built on the EDT, it was built on " + magnetThread.get().getName());
    assertTrue(copiedOnEdt.get(), "the clipboard is set on the EDT");
    assertEquals(
        "magnet:?xt=urn:btih:first" + System.lineSeparator() + "magnet:?xt=urn:btih:second",
        copied.get());
  }

  /**
   * A transfer whose getMagnetUri records the thread it runs on, and whether it is the EDT, and
   * answers {@code magnet}.
   */
  private static BTDownload download(
      String magnet, AtomicReference<Thread> calledOn, AtomicBoolean calledOnEdt) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getMagnetUri")) {
                calledOn.set(Thread.currentThread());
                if (SwingUtilities.isEventDispatchThread()) {
                  calledOnEdt.set(true);
                }
                return magnet;
              }
              if (method.getName().equals("toString")) {
                return "fake transfer " + magnet;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }
}
