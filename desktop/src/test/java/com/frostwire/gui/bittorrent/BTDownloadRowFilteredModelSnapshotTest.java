/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BTDownloadRowFilteredModelSnapshotTest {

  @Test
  void snapshotIncludesHiddenRowsWithoutChangingTheVisibleFilter() throws Exception {
    AtomicReference<List<BTDownload>> result = new AtomicReference<>();
    AtomicReference<BTDownloadRowFilteredModel> modelRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          BTDownloadRowFilteredModel model =
              new BTDownloadRowFilteredModel(line -> !"hidden".equals(line.getDisplayName()));
          modelRef.set(model);
          BTDownload visible = download("visible");
          BTDownload hidden = download("hidden");
          BTDownloadDataLine visibleLine = new BTDownloadDataLine();
          visibleLine.setInitializeObject(visible);
          BTDownloadDataLine hiddenLine = new BTDownloadDataLine();
          hiddenLine.setInitializeObject(hidden);
          model.add(visibleLine, 0);
          model.add(hiddenLine, 1);

          assertEquals(1, model.getRowCount());
          result.set(model.snapshotAllDownloads());
          assertEquals(1, model.getRowCount());
        });
    assertEquals(
        List.of("visible", "hidden"),
        result.get().stream().map(BTDownload::getDisplayName).toList());
    assertThrows(IllegalStateException.class, () -> modelRef.get().snapshotAllDownloads());
  }

  private static BTDownload download(String name) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getDisplayName")) {
                return name;
              }
              if (method.getName().equals("equals")) {
                return proxy == args[0];
              }
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getReturnType() == boolean.class) {
                return false;
              }
              if (method.getReturnType() == int.class) {
                return 0;
              }
              if (method.getReturnType() == long.class) {
                return 0L;
              }
              if (method.getReturnType() == double.class) {
                return 0.0;
              }
              return null;
            });
  }
}
