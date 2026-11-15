/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import com.frostwire.bittorrent.PaymentOptions;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.tables.HashBasedDataLineModel;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Library specific DataLineModel. Uses HashBasedDataLineModel instead of BasicDataLineModel for
 * quicker access to row's based on the file.
 *
 * @author gubatron
 * @author aldenml
 */
final class LibraryFilesTableModel extends HashBasedDataLineModel<LibraryFilesTableDataLine, File> {
  /** How long resolved rows gather before they are applied and repainted together. */
  private static final long RESOLVED_ROWS_DELAY_MS = 40;

  /** Applies resolved rows under the list lock, off the EDT. */
  private static final ScheduledExecutorService RESOLVED_ROWS_FLUSHER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "LibraryFilesTableModel:resolved-rows");
            thread.setDaemon(true);
            return thread;
          });

  private final Object pendingLock = new Object();

  /** Guarded by {@link #pendingLock}. */
  private List<ResolvedFileData> pendingResolved = new ArrayList<>();

  /** Guarded by {@link #pendingLock}. */
  private boolean flushScheduled;

  /** */
  LibraryFilesTableModel() {
    super(LibraryFilesTableDataLine.class);
  }

  /** Creates a new LibraryTableDataLine */
  public LibraryFilesTableDataLine createDataLine() {
    return new LibraryFilesTableDataLine(this);
  }

  /**
   * Override the normal refresh. Because the DataLine's don't cache any data, we can just call
   * update and they'll show the correct info now. Repaints only rows whose displayed values
   * changed.
   */
  public Object refresh() {
    if (getRowCount() == 0) {
      return null;
    }
    fireChangedRowsSinceLastRefresh();
    return null;
  }

  /** Override default so new ones get added to the end */
  @Override
  public int add(File o) {
    return addSorted(o); // , getRowCount());
  }

  /**
   * Override the dataline add so we can re-initialize files to include the FileDesc. Necessary for
   * changing pending status to shared status.
   */
  @Override
  public int add(LibraryFilesTableDataLine dl, int row) {
    File init = dl.getInitializeObject();
    if (!contains(init)) {
      return forceAdd(dl, row);
    } else {
      // we aren't going to use this dl, so clean it up.
      dl.cleanup();
    }
    return -1;
  }

  /**
   * Appends rows for files already {@link LibraryFilesTableDataLine#resolve}d off the EDT, skipping
   * those already shown, with one table event for the whole batch.
   *
   * @return how many rows were added
   */
  int addResolved(List<LibraryFilesTableDataLine.ResolvedFile> files) {
    List<LibraryFilesTableDataLine> lines = new ArrayList<>(files.size());
    for (LibraryFilesTableDataLine.ResolvedFile file : files) {
      LibraryFilesTableDataLine line = createDataLine();
      line.initialize(file);
      lines.add(line);
    }
    return appendAll(lines);
  }

  /**
   * Returns the file object stored in the given row.
   *
   * @param row The row of the file
   * @return The <code>File</code> object stored at the specified row
   */
  File getFile(int row) {
    return get(row).getInitializeObject();
  }

  /**
   * Applies a file's size, date, license and tips found in the background, from any thread but the
   * EDT. The rows resolved within {@link #RESOLVED_ROWS_DELAY_MS} are applied together under the
   * list lock on the flush thread, and the EDT only gets their row events, so it never waits for
   * the list lock while a sort or a bulk add holds it (Icebase #a09fb9913779,
   * frostwire/frostwire#1543).
   */
  void updateResolvedFileData(
      File file,
      SizeHolder sizeHolder,
      Date lastModified,
      String license,
      PaymentOptions paymentOptions) {
    synchronized (pendingLock) {
      pendingResolved.add(
          new ResolvedFileData(file, sizeHolder, lastModified, license, paymentOptions));
      if (flushScheduled) {
        return;
      }
      flushScheduled = true;
    }
    RESOLVED_ROWS_FLUSHER.schedule(
        this::applyPendingResolved, RESOLVED_ROWS_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  /** Runs on the flush thread: may wait for the list lock, the EDT doesn't. */
  private void applyPendingResolved() {
    List<ResolvedFileData> batch;
    synchronized (pendingLock) {
      batch = pendingResolved;
      pendingResolved = new ArrayList<>();
      flushScheduled = false;
    }
    int[] rows = new int[batch.size()];
    int count = 0;
    boolean shouldResort;
    synchronized (getListLock()) {
      for (ResolvedFileData data : batch) {
        int row = getRow(data.file());
        if (row == -1) {
          continue;
        }
        get(row)
            .applyResolvedFileData(
                data.sizeHolder(), data.lastModified(), data.license(), data.paymentOptions());
        rows[count++] = row;
      }
      shouldResort =
          isSorted() && getSortColumn() == LibraryFilesTableDataLine.MODIFICATION_TIME_IDX;
    }
    if (count == 0) {
      return;
    }
    int[] updated = Arrays.copyOf(rows, count);
    Arrays.sort(updated);
    GUIMediator.safeInvokeLater(() -> fireRowsUpdated(updated));
    if (shouldResort) {
      resort();
    }
  }

  /** One event per run of consecutive rows. Takes no lock. */
  private void fireRowsUpdated(int[] sortedRows) {
    int start = sortedRows[0];
    for (int i = 1; i <= sortedRows.length; i++) {
      if (i == sortedRows.length || sortedRows[i] > sortedRows[i - 1] + 1) {
        fireTableRowsUpdated(start, sortedRows[i - 1]);
        if (i < sortedRows.length) {
          start = sortedRows[i];
        }
      }
    }
  }

  private record ResolvedFileData(
      File file,
      SizeHolder sizeHolder,
      Date lastModified,
      String license,
      PaymentOptions paymentOptions) {}

  /**
   * Returns a boolean specifying whether or not specific cell in the table is editable.
   *
   * @param row the row of the table to access
   * @param col the column of the table to access
   * @return <code>true</code> if the specified cell is editable, <code>false</code> otherwise
   */
  public boolean isCellEditable(int row, int col) {
    return col == LibraryFilesTableDataLine.ACTIONS_IDX
        || /*col == LibraryFilesTableDataLine.SHARE_IDX || */ col
            == LibraryFilesTableDataLine.PAYMENT_OPTIONS_IDX;
  }
}
