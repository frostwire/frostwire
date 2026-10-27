/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.Test;

/** A Transfers filter change sorts once and repaints the table once, not once per row. */
class BTDownloadRowFilteredModelRebuildTest {

  private volatile Predicate<String> shown = name -> true;

  @Test
  void filterChangeFiresOneTableChangedEventAndKeepsTheSortedOrder() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          BTDownloadRowFilteredModel model =
              new BTDownloadRowFilteredModel(
                  line -> shown.test(line.getInitializeObject().getDisplayName()));
          model.sort(new BTDownloadDataLine().getTypeAheadColumn()); // the FILE column
          shown = name -> !name.equals("echo");
          for (String name : new String[] {"delta", "alpha", "echo", "charlie", "bravo"}) {
            model.add(download(name));
          }

          shown = name -> !name.equals("alpha");
          List<TableModelEvent> events = new ArrayList<>();
          model.addTableModelListener(events::add);
          model.filtersChanged();

          assertEquals(1, events.size(), "events fired by one filter change");
          assertEquals(Integer.MAX_VALUE, events.get(0).getLastRow(), "a whole-table change");
          assertEquals(List.of("bravo", "charlie", "delta", "echo"), names(model));
          assertEquals(5, model.snapshotAllDownloads().size());

          shown = name -> true;
          model.filtersChanged();
          assertEquals(List.of("alpha", "bravo", "charlie", "delta", "echo"), names(model));
        });
  }

  private static List<String> names(BTDownloadRowFilteredModel model) {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < model.getRowCount(); i++) {
      names.add(model.get(i).getInitializeObject().getDisplayName());
    }
    return names;
  }

  private static BTDownload download(String name) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getDisplayName", "getHash", "toString" -> {
                  return name;
                }
                case "equals" -> {
                  return proxy == args[0];
                }
                case "hashCode" -> {
                  return System.identityHashCode(proxy);
                }
                default -> {
                  Class<?> type = method.getReturnType();
                  if (type == boolean.class) return false;
                  if (type == int.class) return 0;
                  if (type == long.class) return 0L;
                  if (type == double.class) return 0d;
                  if (type == float.class) return 0f;
                  return null;
                }
              }
            });
  }
}
