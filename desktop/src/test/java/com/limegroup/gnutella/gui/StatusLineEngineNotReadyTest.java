/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.bittorrent.BTEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Icebase #57095c0f3115: the status bar's setup block runs on the EDT and can run before startup
 * has set up BTEngine. It used to call {@code BTEngine.getInstance()}, which parks the caller on a
 * latch until startup sets the engine context, stalling the EDT. The status bar must read the
 * engine without waiting and show the neutral "no DHT nodes yet" state instead.
 */
class StatusLineEngineNotReadyTest {

  private static final long MAX_WAIT_MS = 3_000;

  @Test
  void engineAccessorDoesNotWaitBeforeSetup() {
    assumeTrue(BTEngine.ctx == null, "engine context already set up in this JVM");
    assertNull(BTEngine.getInstanceIfReady());
  }

  @Test
  void dhtNodeCountDoesNotWaitForTheEngine() throws InterruptedException {
    assumeTrue(BTEngine.ctx == null, "engine context already set up in this JVM");
    AtomicLong nodes = new AtomicLong(-1);
    CountDownLatch done = new CountDownLatch(1);
    // A daemon thread rather than the EDT: if the read ever waits for the engine again, the test
    // fails without leaving the shared EDT parked for the other tests in this JVM.
    Thread reader =
        new Thread(
            () -> {
              nodes.set(StatusLine.dhtNodesIfEngineReady());
              done.countDown();
            },
            "StatusLineEngineNotReadyTest-reader");
    reader.setDaemon(true);
    reader.start();

    assertTrue(
        done.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS),
        "StatusLine waited for BTEngine before startup set it up; thread state: "
            + reader.getState());
    assertEquals(0, nodes.get());
  }

  @Test
  void statusLineNeverWaitsForTheEngine() throws IOException {
    // Whitespace-stripped so reformatting cannot hide a call. Every StatusLine path runs on the EDT
    // or on the shared background queue, and both must not park until startup sets up the engine.
    String source =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/StatusLine.java"))
            .replaceAll("\\s+", "");
    assertFalse(
        source.contains("BTEngine.getInstance()"),
        "StatusLine must use BTEngine.getInstanceIfReady(), never the blocking getInstance()");
  }
}
