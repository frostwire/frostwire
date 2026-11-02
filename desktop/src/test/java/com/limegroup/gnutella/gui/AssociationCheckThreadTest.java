/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.limegroup.gnutella.gui.shell.ShellAssociationManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * When the main window first appeared, the EDT built the file-association manager, which loads the
 * platform's association service from the jar (Icebase #2185bdb655b0, GitHub #1463). #1378 had
 * moved only the registry checks off the EDT.
 */
class AssociationCheckThreadTest {

  @Test
  void theManagerIsBuiltAndCheckedOffTheCallersThread() throws Exception {
    List<Thread> builtOn = new CopyOnWriteArrayList<>();
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      GUIMediator.checkAssociationsInBackground(
          () -> {
            builtOn.add(Thread.currentThread());
            return new ShellAssociationManager(List.of());
          },
          background,
          task -> {
            throw new AssertionError("nothing to ask: no association is held by another program");
          });
      background.shutdown();
      background.awaitTermination(10, TimeUnit.SECONDS);
    } finally {
      background.shutdownNow();
    }

    assertEquals(1, builtOn.size());
    assertNotSame(Thread.currentThread(), builtOn.get(0), "built on the caller's thread (the EDT)");
  }
}
