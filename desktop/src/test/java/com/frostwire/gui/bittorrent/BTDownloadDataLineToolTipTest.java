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
import com.limegroup.gnutella.gui.tables.SeedsHolder;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class BTDownloadDataLineToolTipTest {

  /** Cheap on the EDT: a cached field and the cached-status completion check. */
  private static final Set<String> ALLOWED = Set.of("getDisplayName", "isCompleted");

  private static BTDownload recording(List<String> calls) {
    return recording(calls, "3 (7)");
  }

  private static BTDownload recording(List<String> calls, String seedsAndPeers) {
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
                case "getSeedsString", "getPeersString" -> seedsAndPeers;
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

  /**
   * getValueAt() runs for every row on each paint, on the EDT. The Seeds cell was a new SeedsHolder
   * per call, parsed from "" for HTTP and metadata-fetching downloads with a caught
   * NumberFormatException each time (frostwire/frostwire#1380).
   */
  @Test
  void seedsCellIsBuiltByUpdateNotPerPaint() {
    BTDownloadDataLine line = new BTDownloadDataLine();
    line.setInitializeObject(recording(new CopyOnWriteArrayList<>(), ""));
    line.update();
    int seedsColumn = -1;
    for (int i = 0; i < line.getColumnCount(); i++) {
      if (line.getValueAt(i) instanceof SeedsHolder) {
        seedsColumn = i;
      }
    }
    assertTrue(seedsColumn >= 0, "a Seeds column");
    assertSame(line.getValueAt(seedsColumn), line.getValueAt(seedsColumn));
  }
}
