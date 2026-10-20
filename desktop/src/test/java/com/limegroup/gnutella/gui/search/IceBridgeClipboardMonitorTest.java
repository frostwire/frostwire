/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IceBridgeClipboardMonitorTest {

  private static final String URL = "icebridge://" + "ab".repeat(32) + "/browse";

  /**
   * Activating the window read the system clipboard on the EDT, which blocked for over 5s on a
   * 2-CPU Windows 8.1 machine (Icebase #fae51b3408bd). The read now runs on the worker.
   */
  @Test
  void activationOnlyQueuesTheClipboardRead() {
    List<Runnable> queued = new ArrayList<>();
    AtomicInteger reads = new AtomicInteger();
    List<String> opened = new ArrayList<>();
    IceBridgeClipboardMonitor monitor =
        new IceBridgeClipboardMonitor(
            queued::add,
            () -> {
              reads.incrementAndGet();
              return URL;
            },
            () -> {},
            opened::add);

    monitor.windowActivated(null);
    monitor.windowActivated(null); // a second activation while the first read is pending

    assertEquals(0, reads.get(), "the activating thread must not read the clipboard");
    assertEquals(1, queued.size(), "one read in flight");
    queued.get(0).run();
    assertEquals(1, reads.get());
    assertEquals(List.of(URL), opened);
  }

  @Test
  void aUrlOpensOnceAndOtherTextIsIgnored() {
    AtomicReference<String> clipboard = new AtomicReference<>("  " + URL + "  ");
    AtomicInteger clears = new AtomicInteger();
    List<String> opened = new ArrayList<>();
    IceBridgeClipboardMonitor monitor =
        new IceBridgeClipboardMonitor(
            Runnable::run, clipboard::get, clears::incrementAndGet, opened::add);

    monitor.windowActivated(null);
    monitor.windowActivated(null); // same text again
    clipboard.set("just some text");
    monitor.windowActivated(null);
    clipboard.set(null);
    monitor.windowActivated(null);

    assertEquals(List.of(URL), opened);
    assertEquals(1, clears.get());
  }
}
