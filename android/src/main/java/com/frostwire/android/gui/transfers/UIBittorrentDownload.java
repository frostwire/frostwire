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
import com.frostwire.transfers.TransferStateListener;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UIBittorrentDownload implements BittorrentDownload {

    private static final Logger LOG = Logger.getLogger(UIBittorrentDownload.class);
    public static boolean SEQUENTIAL_DOWNLOADS = false;

    private final TransferManager manager;
    private final BTDownload dl;
    private final List<TransferStateListener> listeners = new CopyOnWriteArrayList<>();

    private String displayName;
    private long size;
    private volatile List<TransferItem> items;

    private boolean noSpaceAvailableInCurrentMount;

    // Cached values to avoid blocking JNI calls on UI thread (ANR fix)
    // These are updated asynchronously via updateCachedState()
    private volatile TransferState cachedState;
    private volatile int cachedProgress;
    private volatile int cachedConnectedSeeds;
    private volatile int cachedTotalSeeds;
    private volatile int cachedConnectedPeers;
    private volatile int cachedTotalPeers;

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

        // Initialize cached state (may block briefly, but only during construction)
        updateCachedState();
    }

    /**
     * Updates cached values by reading from BTDownload.
     * MUST be called from background thread, never UI thread.
     */
    public void updateCachedState() {
        try {
            cachedState = dl.getState();
            cachedProgress = dl.getProgress();
            cachedConnectedSeeds = dl.getConnectedSeeds();
            cachedTotalSeeds = dl.getTotalSeeds();
            cachedConnectedPeers = dl.getConnectedPeers();
            cachedTotalPeers = dl.getTotalPeers();
        } catch (Throwable e) {
            LOG.error("Error updating cached state", e);
        }
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
        // Return cached value to avoid blocking JNI call on UI thread
        // NEVER fall back to dl.getConnectedPeers() - it calls blocking th.status()
        return cachedConnectedPeers;
    }

    @Override
    public int getTotalPeers() {
        // Return cached value to avoid blocking JNI call on UI thread
        // NEVER fall back to dl.getTotalPeers() - it calls blocking th.status()
        return cachedTotalPeers;
    }

    @Override
    public int getConnectedSeeds() {
        // Return cached value to avoid blocking JNI call on UI thread
        // NEVER fall back to dl.getConnectedSeeds() - it calls blocking th.status()
        return cachedConnectedSeeds;
    }

    @Override
    public int getTotalSeeds() {
        // Return cached value to avoid blocking JNI call on UI thread
        // NEVER fall back to dl.getTotalSeeds() - it calls blocking th.status()
        return cachedTotalSeeds;
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

        // Post all cleanup operations asynchronously to avoid blocking the UI thread
        // This includes torrent removal, file deletion, and media store cleanup
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
            // Remove from bittorrent engine (can take time)
            try {
                dl.remove(deleteTorrent, deleteData);
            } catch (Throwable t) {
                LOG.error("UIBittorrentDownload::remove failed to remove from bittorrent engine", t);
            }

            // Delete files if requested
            if (deleteData && isComplete()) {
                // This block runs on CONFIG_MANAGER thread (safe for I/O operations)
                if (Ref.alive(contextRef)) {
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
            }
        });
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
        // Return cached state to avoid blocking JNI call on UI thread (ANR fix)
        // NEVER fall back to dl.getState() - it calls blocking th.status()
        // Cached value is updated asynchronously via updateCachedState()
        return cachedState != null ? cachedState : TransferState.DOWNLOADING;
    }

    @Override
    public int getProgress() {
        try {
            // TODO: Bring back with a setting
            //checkSequentialDownload();
        } catch (Throwable e) {
            LOG.error("Error checking sequential download");
        }
        // Return cached progress to avoid blocking JNI call on UI thread
        // NEVER fall back to dl.getProgress() - it calls blocking th.status()
        return cachedProgress;
    }

    @Override
    public long getSize() {
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

    /**
     * Returns items without triggering lazy loading.
     * Safe to call on UI thread - never does blocking JNI.
     * Returns null if items haven't been loaded yet.
     */
    public List<TransferItem> peekItems() {
        return items;
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

    private long calculateSize(BTDownload dl) {
        long size = dl.getSize();

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

    public void addListener(TransferStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(TransferStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyStateChanged(TransferState oldState, TransferState newState) {
        if (oldState == newState) {
            return;
        }
        for (TransferStateListener listener : listeners) {
            try {
                listener.onTransferStateChanged(this, oldState, newState);
            } catch (Throwable e) {
                LOG.error("Error notifying TransferStateListener", e);
            }
        }
    }

    private void notifyProgressChanged(int progress) {
        for (TransferStateListener listener : listeners) {
            try {
                listener.onTransferProgressChanged(this, progress);
            } catch (Throwable e) {
                LOG.error("Error notifying TransferStateListener", e);
            }
        }
    }
}
