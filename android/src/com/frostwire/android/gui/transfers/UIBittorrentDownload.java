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

package com.frostwire.android.gui.transfers;

import android.content.Context;

import androidx.annotation.Nullable;

import com.frostwire.android.gui.Librarian;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UIBittorrentDownload implements BittorrentDownload {

    private static final Logger LOG = Logger.getLogger(UIBittorrentDownload.class);
    public static boolean SEQUENTIAL_DOWNLOADS = false;

    private final TransferManager manager;
    private final BTDownload dl;

    private String displayName;
    private double size;
    private volatile List<TransferItem> items;

    private boolean noSpaceAvailableInCurrentMount;

    public UIBittorrentDownload(TransferManager manager, BTDownload dl) {
        this.manager = manager;
        this.dl = dl;
        if (dl.getListener() == null) {
            this.dl.setListener(new UIBTDownloadListener());
        }

        this.displayName = dl.getDisplayName();
        this.size = calculateSize(dl);
        // Don't eagerly load items to prevent excessive memory retention.
        // Items will be lazily loaded when first accessed via getItems().
        // This prevents TransactionTooLargeException when fragments are destroyed.
        this.items = null;

        if (!dl.wasPaused() && !manager.isMobileAndDataSavingsOn()) {
            dl.resume();
        }

        try {
            noSpaceAvailableInCurrentMount = TransferManager.getCurrentMountAvailableBytes() < size;
        } catch (Throwable ignored) {
        }
        checkSequentialDownload();
    }

    public BTDownload getDl() {
        return dl;
    }

    @Override
    public String magnetUri() {
        return dl.magnetUri();
    }

    @Override
    public String getInfoHash() {
        return dl.getInfoHash();
    }

    @Override
    public int getConnectedPeers() {
        return dl.getConnectedPeers();
    }

    @Override
    public int getTotalPeers() {
        return dl.getTotalPeers();
    }

    @Override
    public int getConnectedSeeds() {
        return dl.getConnectedSeeds();
    }

    @Override
    public int getTotalSeeds() {
        return dl.getTotalSeeds();
    }

    @Override
    public boolean isSeeding() {
        return dl.isSeeding();
    }

    @Override
    public boolean isPaused() {
        return dl.isPaused();
    }

    @Override
    public boolean isFinished() {
        return dl.isFinished();
    }

    public boolean hasPaymentOptions() {
        return this.dl.getPaymentOptions() != null && !this.dl.getPaymentOptions().isEmpty();
    }

    public PaymentOptions getPaymentOptions() {
        return this.dl.getPaymentOptions();
    }

    @Override
    public void pause() {
        dl.pause();
    }

    @Override
    public void resume() {
        dl.resume();
    }

    @Override
    public File getSavePath() {
        return dl.getSavePath();
    }

    @Override
    public File getContentSavePath() {
        return dl.getContentSavePath();
    }

    @Override
    public boolean isDownloading() {
        return dl.getDownloadSpeed() > 0;
    }

    @Override
    public void remove(boolean deleteData) {
        remove(null, false, deleteData);
    }

    @Override
    public void remove(boolean deleteTorrent, boolean deleteData) {
        remove(null, deleteTorrent, deleteData);
    }

    @Override
    public String getPredominantFileExtension() {
        return getDl() == null ? "torrent" : getDl().getPredominantFileExtension();
    }

    public void remove(WeakReference<Context> contextRef, boolean deleteTorrent, boolean deleteData) {
        manager.remove(this);

        if (Ref.alive(contextRef) && deleteData && isComplete()) {
            // Let's remove all the file descriptors from the fetchers
            //deleteFilesFromContentResolver(contextRef.get(), deleteTorrent);

            getItems().stream().filter(TransferItem::isComplete).forEach(item -> {

                if (SystemUtils.hasAndroid10()) {
                    Librarian.mediaStoreDeleteFromDownloads(item.getFile());
                }

                // delete from the internal folder
                try {
                    if (item.getFile().isFile()) {
                        item.getFile().delete();
                    }
                } catch (Throwable t) {
                    LOG.error("UIBittorrentDownload::remove failed to delete the internal file -> " + item.getFile().getAbsolutePath());
                }
            });

        }

        dl.remove(deleteTorrent, deleteData);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public TransferState getState() {
        if (noSpaceAvailableInCurrentMount) {
            return TransferState.ERROR_DISK_FULL;
        }
        return dl.getState();
    }

    @Override
    public int getProgress() {
        try {
            // TODO: Bring back with a setting
            //checkSequentialDownload();
        } catch (Throwable e) {
            LOG.error("Error checking sequential download");
        }

        return dl.getProgress();
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public Date getCreated() {
        return dl.getCreated();
    }

    @Override
    public long getBytesReceived() {
        return dl.getBytesReceived();
    }

    @Override
    public long getBytesSent() {
        return dl.getBytesSent();
    }

    @Override
    public long getDownloadSpeed() {
        return dl.getDownloadSpeed();
    }

    @Override
    public long getUploadSpeed() {
        return dl.getUploadSpeed();
    }

    @Override
    public long getETA() {
        return dl.getETA();
    }

    @Override
    public boolean isComplete() {
        return dl.isComplete();
    }

    @Override
    public List<TransferItem> getItems() {
        // Lazy-load items to prevent excessive memory retention and Bundle serialization
        if (items == null) {
            items = calculateItems(dl);
        }
        return items;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public File previewFile() {
        return null;
    }

    void updateUI(BTDownload dl) {
        displayName = dl.getDisplayName();
        size = calculateSize(dl);
        // Recalculate items only if they're already loaded (avoid forcing large list into memory)
        if (items != null) {
            items = calculateItems(dl);
        }
        checkSequentialDownload();
    }

    /**
     * Clears the cached items list to free memory.
     * Items will be lazily reloaded when next accessed via getItems().
     * This helps prevent TransactionTooLargeException during activity lifecycle transitions.
     */
    public void clearCachedItems() {
        items = null;
    }

    private double calculateSize(BTDownload dl) {
        double size = dl.getSize();

        boolean partial = dl.isPartial();
        if (partial) {
            List<com.frostwire.transfers.TransferItem> items = dl.getItems();

            long totalSize = 0;
            for (com.frostwire.transfers.TransferItem item : items) {
                if (!item.isSkipped()) {
                    totalSize += item.getSize();
                }
            }

            if (totalSize > 0) {
                size = totalSize;
            }
        }

        return size;
    }

    private List<TransferItem> calculateItems(BTDownload dl) {
        List<TransferItem> l = new LinkedList<>();

        for (TransferItem item : dl.getItems()) {
            if (!item.isSkipped()) {
                l.add(item);
            }
        }

        return l;
    }

    /**
     * Makes sure download follows the value of the global sequential downloads setting
     */
    public void checkSequentialDownload() {
        dl.setSequentialDownload(SEQUENTIAL_DOWNLOADS);
    }

    private BTDownloadItem getFirstBiggestItem() {
        BTDownloadItem item = null;

        for (TransferItem it : items) {
            if (it instanceof BTDownloadItem) {
                BTDownloadItem bit = (BTDownloadItem) it;
                if (item == null) {
                    item = bit;
                } else {
                    if (item.getSize() < 2 * 1024 * 1024 && item.getSize() < bit.getSize()) {
                        item = bit;
                    }
                }
            }
        }

        return item;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof UIBittorrentDownload)) {
            return false;
        }
        return getInfoHash().equals(((UIBittorrentDownload) other).getInfoHash());
    }
}
