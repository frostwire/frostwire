/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class BTDownloadMediatorBuildRowTest {

  /**
   * Adding a transfer row read the .torrent file for payment and license metadata on the EDT, the
   * first time BTDownloadDataLine.initialize() asked for it; a 2-CPU Windows 10 machine stalled
   * there for over 5s (Icebase #cd09449f858a). buildRow() runs on the background executor and loads
   * the metadata before the row is handed to the EDT.
   */
  @Test
  void buildRowLoadsTheRowMetadataBeforeHandingItOver() {
    List<String> calls = new CopyOnWriteArrayList<>();
    BTDownload download =
        (BTDownload)
            Proxy.newProxyInstance(
                BTDownload.class.getClassLoader(),
                new Class<?>[] {BTDownload.class},
                (proxy, method, args) -> {
                  calls.add(method.getName());
                  return null;
                });

    BTDownload built = BTDownloadMediator.buildRow(() -> download);

    assertSame(download, built);
    assertEquals(List.of("getPaymentOptions"), calls);
  }
}
