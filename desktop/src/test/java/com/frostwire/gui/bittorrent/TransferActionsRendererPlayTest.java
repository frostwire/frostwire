/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TransferActionsRendererPlayTest {

  /** A BTDownload that records which thread asked whether it can be previewed. */
  private static BTDownload previewable(File previewFile, List<Thread> previewCheckThreads) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "canPreview" -> {
                    previewCheckThreads.add(Thread.currentThread());
                    yield true;
                  }
                  case "getPreviewFile" -> previewFile;
                  default -> null;
                });
  }

  @Test
  void previewChecksRunOnTheBackgroundExecutorNotTheCaller() throws Exception {
    List<Thread> previewCheckThreads = new CopyOnWriteArrayList<>();
    List<File> launched = new CopyOnWriteArrayList<>();
    File file = new File("movie.mp4");
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      TransferActionsRenderer.play(
          previewable(file, previewCheckThreads), background, launched::add);
      background.shutdown();
      assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      background.shutdownNow();
    }

    assertEquals(1, previewCheckThreads.size());
    assertNotSame(Thread.currentThread(), previewCheckThreads.get(0));
    assertEquals(List.of(file), launched);
  }

  @Test
  void nothingIsScheduledWithoutADownload() {
    TransferActionsRenderer.play(
        null,
        task -> {
          throw new AssertionError("must not schedule work");
        },
        file -> {
          throw new AssertionError("must not launch");
        });
  }
}
