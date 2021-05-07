/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.transfers;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.providers.TableFetcher;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.gui.Librarian;
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
    private List<TransferItem> items;

    private boolean noSpaceAvailableInCurrentMount;

    public UIBittorrentDownload(TransferManager manager, BTDownload dl) {
        this.manager = manager;
        this.dl = dl;
        if (dl.getListener() == null) {
            this.dl.setListener(new UIBTDownloadListener());
        }

        this.displayName = dl.getDisplayName();
        this.size = calculateSize(dl);
        this.items = calculateItems(dl);

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
            deleteFilesFromContentResolver(contextRef.get(), deleteTorrent);
        }

        dl.remove(deleteTorrent, deleteData);
    }

    private void deleteFilesFromContentResolver(Context context, boolean deleteTorrent) {
        final List<TransferItem> items = getItems();
        final ContentResolver cr = context.getContentResolver();
        Librarian librarian = Librarian.instance();
        if (librarian == null) {
            return;
        }
        for (TransferItem item : items) {
            final List<FWFileDescriptor> FWFileDescriptors = librarian.getFiles(context, item.getFile().getAbsolutePath(), true);
            for (FWFileDescriptor fd : FWFileDescriptors) {
                File file = new File(fd.filePath);
                if (file.isFile()) {
                    try {
                        TableFetcher fetcher = TableFetchers.getFetcher(fd.fileType);
                        if (fetcher != null) {
                            cr.delete(fetcher.getExternalContentUri(), MediaStore.MediaColumns._ID + " = " + fd.id, null);
                        }
                    } catch (Throwable e) {
                        LOG.error("Failed to delete file from media store. (" + fd.filePath + ")", e);
                    }
                }
            }
        }

        if (deleteTorrent) {
            File torrent = dl.getTorrentFile();
            if (torrent != null) {
                final List<FWFileDescriptor> fds = librarian.getFiles(context, Constants.FILE_TYPE_TORRENTS, torrent.getAbsolutePath(), true);
                librarian.deleteFiles(context, Constants.FILE_TYPE_TORRENTS, fds);
            }
        }
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
        items = calculateItems(dl);
        checkSequentialDownload();
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
