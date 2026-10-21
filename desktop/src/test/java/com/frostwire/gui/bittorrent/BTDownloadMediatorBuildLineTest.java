/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.transfers.TransferState;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BTDownloadMediatorBuildLineTest {

  /**
   * Adding a transfer initialized its row on the EDT: BTDownloadDataLine.initialize() calls
   * update(), which reads the .torrent for payment options (Icebase #cd09449f858a, #afa8a56f37d1),
   * checks the disk for a preview file (canPreview, #8e8cc827d636) and asks libtorrent for the ETA.
   * The whole line is now built on the background executor and only inserted on the EDT.
   */
  @Test
  void theRowIsBuiltAndInitializedOnTheBackgroundExecutor() throws Exception {
    Map<String, Set<String>> threadsByCall = new ConcurrentHashMap<>();
    BTDownload download =
        (BTDownload)
            Proxy.newProxyInstance(
                BTDownload.class.getClassLoader(),
                new Class<?>[] {BTDownload.class},
                (proxy, method, args) -> {
                  threadsByCall
                      .computeIfAbsent(method.getName(), name -> ConcurrentHashMap.newKeySet())
                      .add(Thread.currentThread().getName());
                  return switch (method.getName()) {
                    case "getDisplayName" -> "ubuntu.iso";
                    case "getState" -> TransferState.DOWNLOADING;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> {
                      Class<?> type = method.getReturnType();
                      if (type == boolean.class) yield false;
                      if (type == int.class) yield 0;
                      if (type == long.class) yield 0L;
                      if (type == double.class) yield 0.0;
                      if (type == float.class) yield 0f;
                      yield null;
                    }
                  };
                });

    ExecutorService background =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "row-builder"));
    AtomicReference<BTDownloadDataLine> handedOver = new AtomicReference<>();
    try {
      BTDownloadMediator.buildThenAdd(
          () -> BTDownloadMediator.buildLine(() -> download), background, handedOver::set);
      background.shutdown();
      assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      background.shutdownNow();
    }

    BTDownloadDataLine line = handedOver.get();
    assertSame(download, line.getInitializeObject(), "the line is initialized before hand-over");
    for (String expensive :
        new String[] {"getPaymentOptions", "canPreview", "getETA", "getState"}) {
      assertEquals(
          Set.of("row-builder"),
          threadsByCall.get(expensive),
          expensive + " must run while building the line, off the adding thread");
    }
  }
}
