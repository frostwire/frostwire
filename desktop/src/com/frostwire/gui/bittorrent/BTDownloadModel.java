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

package com.frostwire.gui.bittorrent;

import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;

import java.util.HashSet;

/**
 * This class provides access to the <tt>ArrayList</tt> that stores all of the
 * downloads displayed in the download window.
 */
public class BTDownloadModel extends BasicDataLineModel<BTDownloadDataLine, BTDownload> {
    private final HashSet<String> _hashDownloads;

    /**
     * Initialize the model by setting the class of its DataLines.
     */
    BTDownloadModel() {
        super(BTDownloadDataLine.class);
        _hashDownloads = new HashSet<>();
    }

    /**
     * Creates a new DownloadDataLine
     */
    public BTDownloadDataLine createDataLine() {
        return new BTDownloadDataLine();
    }

    int getActiveDownloads() {
        int size = getRowCount();
        int count = 0;
        for (int i = 0; i < size; i++) {
            BTDownload downloader = get(i).getInitializeObject();
            if (!downloader.isCompleted() && downloader.getState() == TransferState.DOWNLOADING) {
                count++;
            }
        }
        return count;
    }

    int getActiveUploads() {
        int size = getRowCount();
        int count = 0;
        for (int i = 0; i < size; i++) {
            BTDownload downloader = get(i).getInitializeObject();
            if (downloader.isCompleted() && downloader.getState() == TransferState.SEEDING) {
                count++;
            }
        }
        return count;
    }

    public int getTotalDownloads() {
        return getRowCount();
    }

    /**
     * Over-ride the default refresh so that we can
     * set the CLEAR_BUTTON as appropriate.
     */
    public Object refresh() {
        try {
            int size = getRowCount();
            for (int i = 0; i < size; i++) {
                BTDownloadDataLine ud = get(i);
                ud.update();
            }
            fireTableRowsUpdated(0, size);
        } catch (Exception e) {
            System.out.println("ATTENTION: Send the following output to the FrostWire Development team.");
            System.out.println("===============================START COPY & PASTE=======================================");
            e.printStackTrace();
            System.out.println("===============================END COPY & PASTE=======================================");
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Override
    public int add(BTDownload downloader) {
        _hashDownloads.add(downloader.getHash());
        return super.add(downloader);
    }

    @Override
    public int add(BTDownload downloader, int row) {
        _hashDownloads.add(downloader.getHash());
        return super.add(downloader, row);
    }

    @Override
    public void remove(int i) {
        BTDownloadDataLine line = get(i);
        BTDownload downloader = line.getInitializeObject();
        downloader.remove();
        _hashDownloads.remove(downloader.getHash());
        super.remove(i);
    }

    public void remove(String hash) {
        _hashDownloads.remove(hash);
    }
//    public BTDownloadDataLine getDataline(int i) {
//        return get(i);
//    }

    public boolean isDownloading(String hash) {
        return _hashDownloads.contains(hash);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == BTDownloadDataLine.PAYMENT_OPTIONS_COLUMN.getModelIndex() ||
                columnIndex == BTDownloadDataLine.ACTIONS_COLUMN.getModelIndex() ||
                columnIndex == BTDownloadDataLine.SEEDING_COLUMN.getModelIndex();
    }
}
