/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
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

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.*;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.iTunesImportSettings;
import com.limegroup.gnutella.settings.iTunesSettings;
import org.limewire.util.OSUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public class BittorrentDownload implements com.frostwire.gui.bittorrent.BTDownload {
    private static final Logger LOG = Logger.getLogger(BittorrentDownload.class);
    private final BTDownload dl;
    private String displayName;
    private double size;
    private List<TransferItem> items;
    private boolean partial;
    private boolean deleteTorrentWhenRemove;
    private boolean deleteDataWhenRemove;
    private BTInfoAdditionalMetadataHolder holder;
    private CopyrightLicenseBroker licenseBroker;
    private PaymentOptions paymentOptions;

    public BittorrentDownload(BTDownload dl) {
        this.dl = dl;
        this.dl.setListener(new StatusListener());
        this.displayName = dl.getDisplayName();
        this.size = calculateSize(dl);
        this.items = calculateItems(dl);
        this.partial = dl.isPartial();
        if (dl.isFinished(true) &&
                !SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
            dl.pause();
            finalCleanup(dl.getIncompleteFiles());
        }
        if (!dl.wasPaused()) {
            dl.resume();
        }
    }

    private static boolean deleteEmptyDirectoryRecursive(File directory) {
        // make sure we only delete canonical children of the parent file we
        // wish to delete. I have a hunch this might be an issue on OSX and
        // Linux under certain circumstances.
        // If anyone can test whether this really happens (possibly related to
        // symlinks), I would much appreciate it.
        String canonicalParent;
        try {
            canonicalParent = directory.getCanonicalPath();
        } catch (IOException ioe) {
            return false;
        }
        if (!directory.isDirectory()) {
            return false;
        }
        boolean canDelete = true;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (!file.getCanonicalPath().startsWith(canonicalParent))
                        continue;
                } catch (IOException ioe) {
                    canDelete = false;
                }
                if (!deleteEmptyDirectoryRecursive(file)) {
                    canDelete = false;
                }
            }
        }
        return canDelete && directory.delete();
    }

    public BTDownload getDl() {
        return dl;
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return dl.getName();
    }

    @Override
    public boolean isResumable() {
        return dl.isPaused();
    }

    @Override
    public boolean isPausable() {
        return !dl.isPaused();
    }

    @Override
    public boolean isCompleted() {
        return dl.isFinished();
    }

    @Override
    public TransferState getState() {
        return dl.getState();
    }

    @Override
    public void remove() {
        dl.remove(deleteTorrentWhenRemove, deleteDataWhenRemove);
    }

    @Override
    public void pause() {
        dl.pause();
    }

    @Override
    public File getSaveLocation() {
        // Returns the torrent folder.
        if (!partial) {
            return dl.getSavePath();
        }
        for (TransferItem item : items) {
            if (item.getDisplayName().equals(getDisplayName())) {
                return item.getFile();
            }
        }
        return dl.getSavePath();
    }

    @Override
    public void resume() {
        dl.resume();
    }

    @Override
    public int getProgress() {
        return dl.getProgress();
    }

    @Override
    public long getBytesReceived() {
        return dl.getBytesReceived();
    }

    @Override
    public long getBytesSent() {
        return dl.getTotalBytesSent();
    }

    @Override
    public double getDownloadSpeed() {
        return dl.getDownloadSpeed() / 1024;
    }

    @Override
    public double getUploadSpeed() {
        return dl.getUploadSpeed() / 1024;
    }

    @Override
    public long getETA() {
        return dl.getETA();
    }

    @Override
    public String getPeersString() {
        return dl.getConnectedPeers() + "/" + dl.getTotalPeers();
    }

    @Override
    public String getSeedsString() {
        return dl.getConnectedSeeds() + "/" + dl.getTotalSeeds();
    }

    @Override
    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove) {
        this.deleteTorrentWhenRemove = deleteTorrentWhenRemove;
    }

    @Override
    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove) {
        this.deleteDataWhenRemove = deleteDataWhenRemove;
    }

    @Override
    public String getHash() {
        return dl.getInfoHash();
    }

    @Override
    public String getSeedToPeerRatio() {
        return dl.getTotalSeeds() + "/" + dl.getTotalPeers();
    }

    @Override
    public String getShareRatio() {
        long sent = dl.getTotalBytesSent();
        long received = dl.getTotalBytesReceived();
        if (received < 0) {
            return "0";
        }
        return String.valueOf((double) sent / (double) received);
    }

    @Override
    public boolean isPartialDownload() {
        return partial;
    }

    @Override
    public Date getDateCreated() {
        return dl.getCreated();
    }

    @Override
    public PaymentOptions getPaymentOptions() {
        setupMetadataHolder();
        return paymentOptions;
    }

    @Override
    public CopyrightLicenseBroker getCopyrightLicenseBroker() {
        setupMetadataHolder();
        return licenseBroker;
    }

    @Override
    public boolean canPreview() {
        checkSequentialDownload();
        return getPreviewFile() != null;
    }

    @Override
    public File getPreviewFile() {
        BTDownloadItem item = getFirstBiggestItem();
        if (item != null && MediaPlayer.isPlayableFile(item.getFile())) {
            long downloaded = item.getSequentialDownloaded();
            long size = item.getSize();
            //LOG.debug("Downloaded: " + downloaded + ", seq: " + dl.isSequentialDownload());
            if (size > 0) {
                long percent = (100 * downloaded) / size;
                String shareRatio = getShareRatio();
                return (percent > 30 ||
                        downloaded > 10 * 1024 * 1024 ||
                        shareRatio.equalsIgnoreCase("Infinity") ||
                        shareRatio.equalsIgnoreCase("NaN")) ?
                        item.getFile() : null;
            }
        }
        return null;
    }

    void updateUI(BTDownload dl) {
        displayName = dl.getDisplayName();
        size = calculateSize(dl);
        items = calculateItems(dl);
        partial = dl.isPartial();
    }

    private void checkSequentialDownload() {
        BTDownloadItem item = getFirstBiggestItem();
        if (item != null && MediaPlayer.isPlayableFile(item.getFile())) {
            long downloaded = item.getSequentialDownloaded();
            long size = item.getSize();
            if (size > 0) {
                long percent = (100 * downloaded) / size;
                if (percent > 30 || downloaded > 10 * 1024 * 1024) {
                    if (dl.isSequentialDownload()) {
                        dl.setSequentialDownload(false);
                    }
                } else {
                    if (!dl.isSequentialDownload()) {
                        dl.setSequentialDownload(true);
                    }
                }
            }
        } else {
            if (dl.isSequentialDownload()) {
                dl.setSequentialDownload(false);
            }
        }
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

    String makeMagnetUri() {
        return dl.magnetUri();
    }

    TorrentInfo getTorrentInfo() {
        return new TorrentInfo(dl.getTorrentFile());
    }

    private void setupMetadataHolder() {
        if (holder == null) {
            File torrent;
            try {
                torrent = dl.getTorrentFile();
                if (torrent != null && torrent.exists() && torrent.canRead()) {
                    holder = new BTInfoAdditionalMetadataHolder(torrent, getDisplayName());
                    licenseBroker = holder.getLicenseBroker();
                    paymentOptions = holder.getPaymentOptions();
                    if (paymentOptions != null) {
                        paymentOptions.setItemName(getDisplayName());
                    }
                }
            } catch (Throwable e) {
                LOG.error("Unable to setup licence holder");
            }
        }
    }

    //Deletes incomplete files and the save location from the iTunes import settings
    private void finalCleanup(Set<File> incompleteFiles) {
        if (incompleteFiles != null) {
            for (File f : incompleteFiles) {
                try {
                    if (f.exists() && !f.delete()) {
                        LOG.warn("Can't delete file: " + f);
                    }
                } catch (Throwable e) {
                    LOG.warn("Can't delete file: " + f + ", ex: " + e.getMessage());
                }
            }
            File saveLocation = dl.getContentSavePath();
            if (saveLocation != null) {
                deleteEmptyDirectoryRecursive(saveLocation);
                iTunesImportSettings.IMPORT_FILES.remove(saveLocation);
            }
        }
    }

    private double calculateSize(BTDownload dl) {
        double size = dl.getSize();
        boolean partial = dl.isPartial();
        if (partial) {
            List<TransferItem> items = dl.getItems();
            long totalSize = 0;
            for (TransferItem item : items) {
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

    static class RendererHelper {
        static boolean canShareNow(com.frostwire.gui.bittorrent.BTDownload dl) {
            return (dl instanceof BittorrentDownload && dl.isCompleted()) || dl.isCompleted();
        }

        static void onSeedTransfer(final com.frostwire.gui.bittorrent.BTDownload dl, final boolean showShareTorrentDialog) {
            boolean canShareNow = canShareNow(dl);
            if (!canShareNow) {
                System.out.println("Not doing anything.");
                return;
            }
            if (dl instanceof BittorrentDownload &&
                    dl.getState().equals(TransferState.SEEDING) &&
                    !showShareTorrentDialog) {
                dl.pause();
                return;
            }
            GUIMediator.safeInvokeLater(() -> {
                if (dl instanceof BittorrentDownload &&
                        TorrentUtil.askForPermissionToSeedAndSeedDownloads(new com.frostwire.gui.bittorrent.BTDownload[]{dl}) &&
                        showShareTorrentDialog) {
                    new ShareTorrentDialog(GUIMediator.getAppFrame(), ((BittorrentDownload) dl).getTorrentInfo()).setVisible(true);
                } else if (dl instanceof SoundcloudDownload || dl instanceof HttpDownload) {
                    if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
                        new Thread(() -> {
                            TorrentUtil.makeTorrentAndDownload(dl.getSaveLocation(), null, showShareTorrentDialog);
                            dl.setDeleteDataWhenRemove(false);
                            GUIMediator.safeInvokeLater(() -> {
                                BTDownloadMediator.instance().remove(dl);
                                BTDownloadMediator.instance().updateTableFilters();
                            });
                        }).start();
                    }
                }
            });
        }
    }

    private class StatusListener implements BTDownloadListener {
        @Override
        public void finished(BTDownload dl) {
            if (!SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
                dl.pause();
                finalCleanup(dl.getIncompleteFiles());
            }
            if (dl.getName() != null) {
                File saveLocation = new File(dl.getSavePath(), dl.getName());
                if (iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() && !iTunesMediator.instance().isScanned(saveLocation)) {
                    if ((OSUtils.isMacOSX() || OSUtils.isWindows())) {
                        iTunesMediator.instance().scanForSongs(saveLocation);
                    }
                }
                if (!LibraryMediator.instance().isScanned(dl.hashCode())) {
                    LibraryMediator.instance().scan(dl.hashCode(), saveLocation);
                }
            }
            //if you have to hide seeds, do so.
            GUIMediator.safeInvokeLater(() -> BTDownloadMediator.instance().updateTableFilters());
        }

        @Override
        public void removed(BTDownload dl, Set<File> incompleteFiles) {
            finalCleanup(incompleteFiles);
        }
    }
}
