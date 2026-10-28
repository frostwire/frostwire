/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.frostwire.transfers.TransferItem;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Showing a torrent's files sorts them without holding the table's list lock (GitHub #1344), so a
 * paint or a refresh never waits on the sort, and still fires a single table event.
 */
class TransferDetailFilesSortTest {

  private static final int SIZE_COLUMN = 4;

  @Test
  void holdersAreSortedOutsideTheListLockWithOneEvent() throws Exception {
    TransferDetailFilesModel model = new TransferDetailFilesModel();
    sortDescending(model, SIZE_COLUMN);
    Object listLock = listLock(model);
    AtomicBoolean sortKeyReadUnderLock = new AtomicBoolean();
    List<TransferDetailFiles.TransferItemHolder> holders = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      long size = (i * 37L) % 101;
      holders.add(
          new TransferDetailFiles.TransferItemHolder(
              i,
              item(
                  "file" + i,
                  size,
                  () -> {
                    if (Thread.holdsLock(listLock)) {
                      sortKeyReadUnderLock.set(true);
                    }
                  })));
    }
    int[] events = {0};
    model.addTableModelListener(e -> events[0]++);

    model.setHolders(holders);

    assertFalse(sortKeyReadUnderLock.get(), "sort keys were read while holding the list lock");
    assertEquals(1, events[0]);
    assertEquals(holders.size(), model.getRowCount());
    for (int row = 1; row < model.getRowCount(); row++) {
      long previous = model.get(row - 1).getInitializeObject().transferItem.getSize();
      long current = model.get(row).getInitializeObject().transferItem.getSize();
      assertFalse(previous < current, "rows " + (row - 1) + " and " + row + " out of order");
    }
  }

  private static void sortDescending(TransferDetailFilesModel model, int column) throws Exception {
    set(model, "_activeColumn", column);
    set(model, "_ascending", -1);
    set(model, "_isSorted", true);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = BasicDataLineModel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object listLock(TransferDetailFilesModel model) throws Exception {
    Method method = BasicDataLineModel.class.getDeclaredMethod("getListLock");
    method.setAccessible(true);
    return method.invoke(model);
  }

  /** A transfer item whose size reports, through {@code onSize}, every time it is read. */
  private static TransferItem item(String name, long size, Runnable onSize) {
    return (TransferItem)
        Proxy.newProxyInstance(
            TransferItem.class.getClassLoader(),
            new Class<?>[] {TransferItem.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getName":
                case "getDisplayName":
                  return name;
                case "getSize":
                  onSize.run();
                  return size;
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  break;
              }
              Class<?> type = method.getReturnType();
              if (type == boolean.class) {
                return false;
              }
              if (type == int.class) {
                return 0;
              }
              if (type == long.class) {
                return 0L;
              }
              return null;
            });
  }
}
