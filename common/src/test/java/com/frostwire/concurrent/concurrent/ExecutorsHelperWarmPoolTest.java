/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.concurrent.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Torrent status refreshes ran on a pool that let its idle threads time out after 5 s, so the
 * transfers table's one-second refresh, on the EDT, started an OS thread after any pause; starting
 * a thread waits on GC safepoints and froze the window (GitHub #1466). A warm pool starts its
 * workers up front and keeps them.
 */
class ExecutorsHelperWarmPoolTest {

  @Test
  void theWorkersStartUpFrontAndSubmittingStartsNone() throws Exception {
    AtomicInteger created = new AtomicInteger();
    ExecutorService pool =
        ExecutorsHelper.newWarmFixedThreadPool(
            2,
            r -> {
              created.incrementAndGet();
              Thread t = new Thread(r);
              t.setDaemon(true);
              return t;
            });
    try {
      assertEquals(2, created.get(), "both workers start with the pool");

      CountDownLatch ran = new CountDownLatch(10);
      for (int i = 0; i < 10; i++) {
        pool.execute(ran::countDown);
      }
      assertTrue(ran.await(5, TimeUnit.SECONDS));
      assertEquals(2, created.get(), "submitting a task never starts a thread");
    } finally {
      pool.shutdownNow();
    }
  }

  /** Source check: the refresh pool is the warm one. */
  @Test
  void torrentStatusRefreshesUseAWarmPool() throws Exception {
    String source =
        Files.readString(
                Path.of("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(
        source.contains(
            "STATUS_REFRESH_EXECUTOR=ExecutorsHelper.newWarmFixedThreadPool(2,\"BTDownload-StatusRefresh\")"));
  }
}
