/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * BTDownload needs a live libtorrent handle, so guard the wiring at source level: cached-status
 * refreshes (triggered from UI callers every second per transfer) must reuse a shared executor
 * instead of starting a new thread each time.
 */
class BTDownloadStatusRefreshStructureTest {
  private static final Path SOURCE =
      Path.of(System.getProperty("user.dir"))
          .resolve("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java");

  @Test
  void statusRefreshUsesTheSharedExecutor() throws Exception {
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    int start = source.indexOf("privatevoidscheduleStatusRefresh(){");
    assertTrue(start >= 0, "scheduleStatusRefresh() not found");
    String body = source.substring(start, source.indexOf("publicvoidrefreshStatusCache()", start));

    assertTrue(body.contains("STATUS_REFRESH_EXECUTOR.execute("));
    assertFalse(body.contains("startThread("));
    assertTrue(source.contains("ExecutorsHelper.newFixedSizeUnboundedThreadPool(2,"));
  }
}
