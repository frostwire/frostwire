/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides access to the `ArrayList` that stores all of the
 * downloads displayed in the download window.
 */
public class BTDownloadModel extends BasicDataLineModel<BTDownloadDataLine, BTDownload> {
    private final HashSet<String> _hashDownloads;
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);

    /**
     * Cache active downloads/uploads counts to avoid iterating through all downloads
     * on every status line refresh. Updates are done in background thread.
     */
    private volatile int cachedActiveDownloads = 0;
    private volatile int cachedActiveUploads = 0;
    private volatile long lastCountUpdateTime = 0;
    private static final long COUNT_CACHE_INTERVAL_MS = 500; // Update count cache every 500ms
    private final AtomicBoolean isUpdatingCounts = new AtomicBoolean(false);

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

    /**
     * Returns the count of active downloads.
     * Uses cached value to avoid expensive iteration on every status line refresh.
     * Cache is updated periodically in background thread.
     */
    int getActiveDownloads() {
        long now = System.currentTimeMillis();
        if (now - lastCountUpdateTime >= COUNT_CACHE_INTERVAL_MS) {
            scheduleCountCacheUpdate();
        }
        return cachedActiveDownloads;
    }

    /**
     * Returns the count of active uploads.
     * Uses cached value to avoid expensive iteration on every status line refresh.
     * Cache is updated periodically in background thread.
     */
    int getActiveUploads() {
        long now = System.currentTimeMillis();
        if (now - lastCountUpdateTime >= COUNT_CACHE_INTERVAL_MS) {
            scheduleCountCacheUpdate();
        }
        return cachedActiveUploads;
    }

    /**
     * Schedules a background thread to recalculate active download/upload counts.
     * This prevents EDT blocking when there are many downloads.
     */
    private void scheduleCountCacheUpdate() {
        // Prevent multiple concurrent updates
        if (!isUpdatingCounts.compareAndSet(false, true)) {
            return;
        }

        BackgroundQueuedExecutorService.schedule(() -> {
            try {
                int size = getRowCount();
                int downloads = 0;
                int uploads = 0;

                try {
                    for (int i = 0; i < size; i++) {
                        BTDownload downloader = get(i).getInitializeObject();
                        if (downloader != null) {
                            boolean completed = downloader.isCompleted();
                            TransferState state = downloader.getState();

                            if (!completed && state == TransferState.DOWNLOADING) {
                                downloads++;
                            } else if (completed && state == TransferState.SEEDING) {
                                uploads++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error updating active counts cache: " + e.getMessage());
                }

                cachedActiveDownloads = downloads;
                cachedActiveUploads = uploads;
                lastCountUpdateTime = System.currentTimeMillis();
            } finally {
                isUpdatingCounts.set(false);
            }
        });
    }

    public int getTotalDownloads() {
        return getRowCount();
    }

    /**
     * Over-ride the default refresh so that we can
     * set the CLEAR_BUTTON as appropriate.
     * This method now uses background threads to avoid EDT blocking.
     */
    public Object refresh() {
        // Prevent multiple concurrent refresh operations
        if (!isRefreshing.compareAndSet(false, true)) {
            return Boolean.TRUE;
        }

        try {
            int size = getRowCount();
            if (size == 0) {
                isRefreshing.set(false);
                return Boolean.TRUE;
            }

            // Perform updates in background thread to avoid EDT blocking
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    int currentSize = getRowCount();
                    for (int i = 0; i < currentSize; i++) {
                        try {
                            BTDownloadDataLine ud = get(i);
                            if (ud != null) {
                                ud.update();
                            }
                        } catch (Exception e) {
                            // Continue updating other rows even if one fails
                            System.err.println("Error updating row " + i + ": " + e.getMessage());
                        }
                    }
                    // Fire table update on EDT after background work is done
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        int currentSize2 = getRowCount();
                        if (currentSize2 > 0) {
                            fireTableRowsUpdated(0, currentSize2 - 1);
                        }
                    });
                } catch (Exception e) {
                    System.out.println("ATTENTION: Send the following output to the FrostWire Development team.");
                    System.out.println("===============================START COPY & PASTE=======================================");
                    e.printStackTrace();
                    System.out.println("===============================END COPY & PASTE=======================================");
                } finally {
                    isRefreshing.set(false);
                }
            });
        } catch (Exception e) {
            System.out.println("ATTENTION: Send the following output to the FrostWire Development team.");
            System.out.println("===============================START COPY & PASTE=======================================");
            e.printStackTrace();
            System.out.println("===============================END COPY & PASTE=======================================");
            isRefreshing.set(false);
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
