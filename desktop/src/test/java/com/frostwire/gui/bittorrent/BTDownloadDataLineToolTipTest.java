/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.transfers.TransferState;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class BTDownloadDataLineToolTipTest {

  /** Cheap on the EDT: a cached field and the cached-status completion check. */
  private static final Set<String> ALLOWED = Set.of("getDisplayName", "isCompleted");

  private static BTDownload recording(List<String> calls) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              calls.add(method.getName());
              return switch (method.getName()) {
                case "getDisplayName" -> "ubuntu.iso";
                case "getState" -> TransferState.DOWNLOADING;
                case "getProgress" -> 42;
                case "getDownloadSpeed" -> 2048.0;
                case "getETA" -> 90L;
                case "getSize" -> 4096L;
                case "getSeedsString", "getPeersString" -> "3 (7)";
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
  }

  /**
   * The tooltip timer fires on the EDT. Building the tooltip called getETA(), which reads
   * libtorrent's torrent_file_ptr and blocked while the session was busy (Icebase #adcba6988ceb).
   */
  @Test
  void toolTipUsesTheValuesCachedByUpdate() {
    List<String> calls = new CopyOnWriteArrayList<>();
    BTDownloadDataLine line = new BTDownloadDataLine();
    line.setInitializeObject(recording(calls));
    line.update();
    assertTrue(calls.contains("getETA"), "update() reads the live values");

    calls.clear();
    String[] tip = line.getToolTipArray(0);

    List<String> live = calls.stream().filter(name -> !ALLOWED.contains(name)).toList();
    assertEquals(List.of(), live, "tooltip must not query the download");
    assertEquals("ubuntu.iso", tip[0]);
    assertTrue(tip[2].endsWith("42%"), tip[2]);
    assertTrue(tip[7].endsWith("3 (7)"), tip[7]);
  }
}
