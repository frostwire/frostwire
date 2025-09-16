/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.tables.HashBasedDataLineModel;

import java.io.File;

/**
 * Library specific DataLineModel.
 * Uses HashBasedDataLineModel instead of BasicDataLineModel
 * for quicker access to row's based on the file.
 *
 * @author gubatron
 * @author aldenml
 */
final class LibraryFilesTableModel extends HashBasedDataLineModel<LibraryFilesTableDataLine, File> {
    /**
     *
     */
    private static final long serialVersionUID = 2859783399965055446L;

    LibraryFilesTableModel() {
        super(LibraryFilesTableDataLine.class);
    }

    /**
     * Creates a new LibraryTableDataLine
     */
    public LibraryFilesTableDataLine createDataLine() {
        return new LibraryFilesTableDataLine(this);
    }

    /**
     * Override the normal refresh.
     * Because the DataLine's don't cache any data,
     * we can just call update & they'll show the correct info
     * now.
     */
    public Object refresh() {
        fireTableRowsUpdated(0, getRowCount());
        return null;
    }

    /**
     * Override default so new ones get added to the end
     */
    @Override
    public int add(File o) {
        return addSorted(o);//, getRowCount());
    }

    /**
     * Override the dataline add so we can re-initialize files
     * to include the FileDesc.  Necessary for changing pending status
     * to shared status.
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
     * Returns the file object stored in the given row.
     *
     * @param row The row of the file
     * @return The <code>File</code> object stored at the specified row
     */
    File getFile(int row) {
        return get(row).getInitializeObject();
    }

    /**
     * Returns a boolean specifying whether or not specific cell in the table
     * is editable.
     *
     * @param row the row of the table to access
     * @param col the column of the table to access
     * @return <code>true</code> if the specified cell is editable,
     * <code>false</code> otherwise
     */
    public boolean isCellEditable(int row, int col) {
        return col == LibraryFilesTableDataLine.ACTIONS_IDX || /*col == LibraryFilesTableDataLine.SHARE_IDX || */ col == LibraryFilesTableDataLine.PAYMENT_OPTIONS_IDX;
    }
}