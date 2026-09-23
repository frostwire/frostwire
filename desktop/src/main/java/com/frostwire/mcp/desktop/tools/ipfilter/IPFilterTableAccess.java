/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.ipfilter;

import com.limegroup.gnutella.gui.options.panes.IPFilterTableMediator;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.tables.DataLineModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import javax.swing.SwingUtilities;

/** Snapshots or changes only the Swing table on the EDT; callers perform I/O and JNI elsewhere. */
final class IPFilterTableAccess {

  private static final int IMPORT_BATCH_SIZE = 128;

  private IPFilterTableAccess() {}

  static <T> T onEdt(Callable<T> operation) {
    if (SwingUtilities.isEventDispatchThread()) {
      return run(operation);
    }
    FutureTask<T> task = new FutureTask<>(operation);
    SwingUtilities.invokeLater(task);
    try {
      return task.get();
    } catch (InterruptedException e) {
      task.cancel(false);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while accessing IP filter table", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Could not access IP filter table", e.getCause());
    }
  }

  private static <T> T run(Callable<T> operation) {
    try {
      return operation.call();
    } catch (Exception e) {
      throw new IllegalStateException("Could not access IP filter table", e);
    }
  }

  static List<IPRange> snapshot() {
    return onEdt(
        () -> {
          DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> model =
              IPFilterTableMediator.getInstance().getDataModel();
          return copyRows(model::getRowCount, i -> model.get(i).getInitializeObject());
        });
  }

  static <T> List<T> copyRows(IntSupplier count, IntFunction<T> row) {
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("Table rows must be copied on the EDT");
    }
    int size = count.getAsInt();
    List<T> result = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      result.add(row.apply(i));
    }
    return result;
  }

  static void add(List<IPRange> ranges) {
    for (int start = 0; start < ranges.size(); start += IMPORT_BATCH_SIZE) {
      int from = start;
      int to = Math.min(start + IMPORT_BATCH_SIZE, ranges.size());
      onEdt(
          () -> {
            IPFilterTableMediator mediator = IPFilterTableMediator.getInstance();
            DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> model =
                mediator.getDataModel();
            for (int i = from; i < to; i++) {
              model.add(ranges.get(i), model.getRowCount());
            }
            if (to == ranges.size()) {
              mediator.refresh();
            }
            return null;
          });
    }
  }

  static void clear() {
    onEdt(
        () -> {
          IPFilterTableMediator.getInstance().clearTable();
          return null;
        });
  }
}
