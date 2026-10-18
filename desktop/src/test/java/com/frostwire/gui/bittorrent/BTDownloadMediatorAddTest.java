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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BTDownloadMediatorAddTest {

  @Test
  void transferObjectsAreBuiltOnTheBackgroundExecutor() throws Exception {
    AtomicReference<Thread> builtOn = new AtomicReference<>();
    List<String> added = new CopyOnWriteArrayList<>();
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      BTDownloadMediator.buildThenAdd(
          () -> {
            builtOn.set(Thread.currentThread());
            return "download";
          },
          background,
          added::add);
      background.shutdown();
      assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      background.shutdownNow();
    }

    assertNotSame(Thread.currentThread(), builtOn.get());
    assertEquals(List.of("download"), added);
  }

  @Test
  void aFailingBuildIsLoggedAndAddsNothing() throws Exception {
    List<String> added = new CopyOnWriteArrayList<>();

    BTDownloadMediator.<String>buildThenAdd(
        () -> {
          throw new IllegalStateException("invalid torrent handle");
        },
        Runnable::run,
        added::add);

    assertTrue(added.isEmpty());
  }
}
