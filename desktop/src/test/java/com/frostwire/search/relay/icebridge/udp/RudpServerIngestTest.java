/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies the bounded ingest hand-off that decouples the Netty event loop from {@link
 * RudpSessionManager#onPacket}. These tests exercise {@link RudpServer.IngestExecutor} directly, so
 * they need no real network or event loop.
 */
class RudpServerIngestTest {

  @Test
  @Timeout(10)
  void submissionRunsOffCallingThread() throws Exception {
    try (RudpServer.IngestExecutor ingest = new RudpServer.IngestExecutor(1, 8)) {
      AtomicReference<Thread> runner = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);

      assertTrue(
          ingest.submit(
              () -> {
                runner.set(Thread.currentThread());
                done.countDown();
              }));

      assertTrue(done.await(5, TimeUnit.SECONDS));
      assertNotSame(Thread.currentThread(), runner.get());
      assertTrue(runner.get().getName().startsWith("icebridge-rudp-worker"));
      assertTrue(runner.get().isDaemon());
    }
  }

  @Test
  @Timeout(10)
  void boundedQueueRunsOnCallerWhenFull() throws Exception {
    try (RudpServer.IngestExecutor ingest = new RudpServer.IngestExecutor(1, 1)) {
      assertEquals(1, ingest.queueCapacity());

      CountDownLatch workerBusy = new CountDownLatch(1);
      CountDownLatch releaseWorker = new CountDownLatch(1);
      assertTrue(
          ingest.submit(
              () -> {
                workerBusy.countDown();
                await(releaseWorker);
              }));
      assertTrue(workerBusy.await(5, TimeUnit.SECONDS));

      assertTrue(ingest.submit(() -> {}));

      Thread caller = Thread.currentThread();
      AtomicReference<Thread> runner = new AtomicReference<>();
      assertTrue(ingest.submit(() -> runner.set(Thread.currentThread())));
      assertSame(caller, runner.get());

      releaseWorker.countDown();
    }
  }

  @Test
  @Timeout(10)
  void submissionsAfterShutdownAreIgnored() {
    RudpServer.IngestExecutor ingest = new RudpServer.IngestExecutor(1, 8);
    AtomicBoolean ran = new AtomicBoolean(false);

    ingest.shutdown();
    assertTrue(ingest.isShutdown());
    assertFalse(ingest.submit(() -> ran.set(true)));

    ingest.close();
    assertFalse(ingest.submit(() -> ran.set(true)));

    assertFalse(ran.get());
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
