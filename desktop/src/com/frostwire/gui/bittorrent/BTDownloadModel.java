/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
