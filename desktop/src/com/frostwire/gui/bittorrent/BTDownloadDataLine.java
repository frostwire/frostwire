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

import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconManager;
import com.limegroup.gnutella.gui.tables.*;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * This class handles all of the data for a single download, representing
 * one "line" in the download window.  It continually updates the
 * displayed data for the download from the contained <tt>Downloader</tt>
 * instance.
 */
public final class BTDownloadDataLine extends AbstractDataLine<BTDownload> {
    static final LimeTableColumn ACTIONS_COLUMN;
    static final LimeTableColumn SEEDING_COLUMN;
    static final LimeTableColumn PAYMENT_OPTIONS_COLUMN;
    static final LimeTableColumn DATE_CREATED_COLUMN;
    private static final String PARTIAL_DOWNLOAD_TEXT = I18n.tr(" (Handpicked)");
    private static final List<LimeTableColumn> columns;
    private static final int COLUMN_COUNT;
    private static final LimeTableColumn FILE_COLUMN;
    private static final LimeTableColumn SIZE_COLUMN;
    private static final LimeTableColumn STATUS_COLUMN;
    private static final LimeTableColumn PROGRESS_COLUMN;
    private static final LimeTableColumn BYTES_DOWNLOADED_COLUMN;
    private static final LimeTableColumn BYTES_UPLOADED_COLUMN;
    private static final LimeTableColumn DOWNLOAD_SPEED_COLUMN;
    private static final LimeTableColumn UPLOAD_SPEED_COLUMN;
    private static final LimeTableColumn TIME_COLUMN;
    private static final LimeTableColumn SEEDS_COLUMN;
    private static final LimeTableColumn PEERS_COLUMN;
    private static final LimeTableColumn SHARE_RATIO_COLUMN;
    private static final LimeTableColumn SEED_TO_PEER_RATIO_COLUMN;
    private static final LimeTableColumn LICENSE_COLUMN;
    public static final Map<TransferState, String> TRANSFER_STATE_STRING_MAP = new HashMap<>();

    static {
        columns = new ArrayList<>();
        ACTIONS_COLUMN = new LimeTableColumn(columns.size(), "TRANSFER_ACTIONS", I18n.tr("Actions"), 65, true, TransferHolder.class);
        columns.add(ACTIONS_COLUMN);
        FILE_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_NAME_COLUMN", I18n.tr("Name"), 232, true, IconAndNameHolder.class);
        columns.add(FILE_COLUMN);
        SEEDING_COLUMN = new LimeTableColumn(columns.size(), "SEEDING_COLUMN", I18n.tr("Seeding"), 67, true, SeedingHolder.class);
        columns.add(SEEDING_COLUMN);
        PAYMENT_OPTIONS_COLUMN = new LimeTableColumn(columns.size(), "PAYMENT_OPTIONS_COLUMN", I18n.tr("Tips/Donations"), 126, true, PaymentOptions.class);
        columns.add(PAYMENT_OPTIONS_COLUMN);
        SIZE_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_SIZE_COLUMN", I18n.tr("Size"), 79, true, SizeHolder.class);
        columns.add(SIZE_COLUMN);
        STATUS_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_STATUS_COLUMN", I18n.tr("Status"), 56, true, String.class);
        columns.add(STATUS_COLUMN);
        PROGRESS_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_PROGRESS_COLUMN", I18n.tr("Progress"), 156, true, ProgressBarHolder.class);
        columns.add(PROGRESS_COLUMN);
        BYTES_DOWNLOADED_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_BYTES_DOWNLOADED_COLUMN", I18n.tr("Downloaded"), 82, true, SizeHolder.class);
        columns.add(BYTES_DOWNLOADED_COLUMN);
        BYTES_UPLOADED_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_BYTES_UPLOADED_COLUMN", I18n.tr("Uploaded"), 20, false, SizeHolder.class);
        columns.add(BYTES_UPLOADED_COLUMN);
        DOWNLOAD_SPEED_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_SPEED_COLUMN", I18n.tr("Down Speed"), 89, true, SpeedRenderer.class);
        columns.add(DOWNLOAD_SPEED_COLUMN);
        UPLOAD_SPEED_COLUMN = new LimeTableColumn(columns.size(), "UPLOAD_SPEED_COLUMN", I18n.tr("Up Speed"), 84, true, SpeedRenderer.class);
        columns.add(UPLOAD_SPEED_COLUMN);
        TIME_COLUMN = new LimeTableColumn(columns.size(), "DOWNLOAD_TIME_REMAINING_COLUMN", I18n.tr("Time"), 66, true, TimeRemainingHolder.class);
        columns.add(TIME_COLUMN);
        SEEDS_COLUMN = new LimeTableColumn(columns.size(), "SEEDS_STATUS_COLUMN", I18n.tr("Seeds"), 56, true, String.class);
        columns.add(SEEDS_COLUMN);
        PEERS_COLUMN = new LimeTableColumn(columns.size(), "PEERS_STATUS_COLUMN", I18n.tr("Peers"), 80, false, String.class);
        columns.add(PEERS_COLUMN);
        SHARE_RATIO_COLUMN = new LimeTableColumn(columns.size(), "SHARE_RATIO_COLUMN", I18n.tr("Share Ratio"), 80, false, String.class);
        columns.add(SHARE_RATIO_COLUMN);
        SEED_TO_PEER_RATIO_COLUMN = new LimeTableColumn(columns.size(), "SEED_TO_PEER_RATIO_COLUMN", I18n.tr("Seeds/Peers"), 80, false, String.class);
        columns.add(SEED_TO_PEER_RATIO_COLUMN);
        DATE_CREATED_COLUMN = new LimeTableColumn(columns.size(), "DATE_CREATED_COLUMN", I18n.tr("Started On"), 80, false, Date.class);
        columns.add(DATE_CREATED_COLUMN);
        LICENSE_COLUMN = new LimeTableColumn(columns.size(), "LICENSE_COLUMN", I18n.tr("License"), 80, false, String.class);
        columns.add(LICENSE_COLUMN);
        COLUMN_COUNT = columns.size();
    }

    static {
        TRANSFER_STATE_STRING_MAP.put(TransferState.CHECKING, I18n.tr("Checking..."));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_METADATA, I18n.tr("Downloading metadata"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_TORRENT, I18n.tr("Downloading torrent"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING, I18n.tr("Downloading"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.FINISHED, I18n.tr("Finished"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.SEEDING, I18n.tr("Seeding"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ALLOCATING, I18n.tr("Allocating"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSED, I18n.tr("Paused"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR, I18n.tr("Error"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_MOVING_INCOMPLETE, I18n.tr("Error: Moving incomplete"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_HASH_MD5, I18n.tr("Error: Wrong md5 hash"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_SIGNATURE, I18n.tr("Error: Wrong signature"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_NOT_ENOUGH_PEERS, I18n.tr("Try again, not enough peers"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.STOPPED, I18n.tr("Stopped"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSING, I18n.tr("Pausing"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELING, I18n.tr("Canceling"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELED, I18n.tr("Canceled"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.WAITING, I18n.tr("Waiting"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.COMPLETE, I18n.tr("Complete"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UPLOADING, I18n.tr("Uploading"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UNCOMPRESSING, I18n.tr("Uncompressing"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DEMUXING, I18n.tr("Demuxing"));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_DISK_FULL, I18n.tr("Error: Disk full - Change default save location."));
        TRANSFER_STATE_STRING_MAP.put(TransferState.REDIRECTING, I18n.tr("Redirecting"));
    }

    /**
     * Variable for the status of the download.
     */
    private TransferState transferState;
    /**
     * Variable for the amount of the file that has been read.
     */
    private long download = 0;
    private long upload;
    /**
     * Variable for the progress made in the progress bar.
     */
    private int progress;
    /**
     * Variable for the size of the download.
     */
    private double size = -1;
    /**
     * Variable for the speed of the download.
     */
    private double downloadSpeed;
    private double uploadSpeed;
    /**
     * Variable for how much time is left.
     */
    private long timeLeft;
    private String seeds;
    private String peers;
    private String shareRatio;
    private String seedToPeerRatio;
    private Date dateCreated;
    private String license;
    private boolean notificationShown;
    private PaymentOptions paymentOptions;
    private TransferHolder transferHolder;
    private SeedingHolder seedingHolder;

    static LimeTableColumn staticGetColumn(int idx) {
        try {
            return columns.get(idx);
        } catch (Throwable t) {
            System.out.println("BTDownloadDataLine::staticGetColumn(" + idx + ") - Index out of bound.");
            return null;
        }
    }

    // Implements DataLine interface
    public int getColumnCount() {
        return COLUMN_COUNT;
    }

    /**
     * Must initialize data.
     *
     * @param downloader the <tt>Downloader</tt>
     *                   that provides access to
     *                   information about the download
     */
    public void initialize(BTDownload downloader) {
        super.initialize(downloader);
        notificationShown = downloader.isCompleted();
        transferHolder = new TransferHolder(downloader);
        seedingHolder = new SeedingHolder(downloader);
        paymentOptions = initializer.getPaymentOptions(); // Comes with item name preset.
        update();
    }

    public boolean isSeeding() {
        return initializer != null && initializer.getState() == TransferState.SEEDING;
    }

    public boolean isDownloading() {
        final TransferState state = initializer.getState();
        // almost like TorrentUtils.isActive() but doesn't consider uploading or seeding.
        final boolean downloading = state == TransferState.ALLOCATING ||
                state == TransferState.CHECKING ||
                state == TransferState.DOWNLOADING ||
                state == TransferState.DOWNLOADING_METADATA ||
                state == TransferState.DOWNLOADING_TORRENT;
        final boolean pausedButUnfinished = !initializer.isCompleted() && state == TransferState.PAUSED;
        return initializer != null && (downloading || pausedButUnfinished);
    }

    public boolean isFinished() {
        return initializer != null && initializer.isCompleted() && !isSeeding();
    }

    public String getDisplayName() {
        if (initializer == null) {
            return "";
        }
        return initializer.getDisplayName();
    }

    /**
     * Returns the <tt>Object</tt> stored at the specified column in this
     * line of data.
     *
     * @return the <tt>Object</tt> stored at that index
     */
    public Object getValueAt(int index) {
        final LimeTableColumn column = columns.get(index);
        if (column == ACTIONS_COLUMN) {
            return transferHolder;
        } else if (column == SEEDING_COLUMN) {
            return seedingHolder;
        } else if (column == FILE_COLUMN) {
            return new IconAndNameHolderImpl(getIcon(), initializer.getDisplayName());
        } else if (column == PAYMENT_OPTIONS_COLUMN) {
            return paymentOptions;
        } else if (column == SIZE_COLUMN) {
            if (initializer.isPartialDownload()) {
                return new SizeHolder(size, PARTIAL_DOWNLOAD_TEXT);
            } else {
                return new SizeHolder(size);
            }
        } else if (column == STATUS_COLUMN) {
            String status = TRANSFER_STATE_STRING_MAP.get(transferState);
            if (status == null) {
                status = I18n.tr("Unknown status");
            }
            return status;
        } else if (column == PROGRESS_COLUMN) {
            return progress;
        } else if (column == BYTES_DOWNLOADED_COLUMN) {
            return new SizeHolder(download);
        } else if (column == BYTES_UPLOADED_COLUMN) {
            return new SizeHolder(upload);
        } else if (column == DOWNLOAD_SPEED_COLUMN) {
            return downloadSpeed;
        } else if (column == UPLOAD_SPEED_COLUMN) {
            return uploadSpeed;
        } else if (column == TIME_COLUMN) {
            if (initializer.isCompleted()) {
                return new TimeRemainingHolder(0);
            } else if (downloadSpeed < 0.001) {
                return new TimeRemainingHolder(-1);
            } else {
                return new TimeRemainingHolder(timeLeft);
            }
        } else if (column == SEEDS_COLUMN) {
            return new SeedsHolder(seeds);
        } else if (column == PEERS_COLUMN) {
            return peers;
        } else if (column == SHARE_RATIO_COLUMN) {
            return shareRatio;
        } else if (column == SEED_TO_PEER_RATIO_COLUMN) {
            return seedToPeerRatio;
        } else if (column == DATE_CREATED_COLUMN) {
            return dateCreated;
        } else if (column == LICENSE_COLUMN) {
            return license;
        }
        return null;
    }

    public LimeTableColumn getColumn(int idx) {
        return staticGetColumn(idx);
    }

    public int getTypeAheadColumn() {
        return FILE_COLUMN.getModelIndex();
    }

    public String[] getToolTipArray(int col) {
        String[] info = new String[11];
        String name = getInitializeObject().getDisplayName();
        String status = I18n.tr("Status") + ": " + I18n.tr(getInitializeObject().getState().name());
        String progress = I18n.tr("Progress") + ": " + getInitializeObject().getProgress() + "%";
        String downSpeed = I18n.tr("Down Speed") + ": " + GUIUtils.rate2speed(getInitializeObject().getDownloadSpeed());
        String upSpeed = I18n.tr("Up Speed") + ": " + GUIUtils.rate2speed(getInitializeObject().getUploadSpeed());
        String downloaded = I18n.tr("Downloaded") + ": " + new SizeHolder(getInitializeObject().getBytesReceived());
        String uploaded = I18n.tr("Uploaded") + ": " + new SizeHolder(getInitializeObject().getBytesSent());
        String peers = I18n.tr("Peers") + ": " + getInitializeObject().getPeersString();
        String seeds = I18n.tr("Seeds") + ": " + getInitializeObject().getSeedsString();
        String size = I18n.tr("Size") + ": " + new SizeHolder(getInitializeObject().getSize());
        String time = I18n.tr("ETA") + ": " + (getInitializeObject().isCompleted() ? new TimeRemainingHolder(0) : (getInitializeObject().getDownloadSpeed() < 0.001 ? new TimeRemainingHolder(-1) : new TimeRemainingHolder(getInitializeObject().getETA())));
        info[0] = name;
        info[1] = status;
        info[2] = progress;
        info[3] = downSpeed;
        info[4] = upSpeed;
        info[5] = downloaded;
        info[6] = uploaded;
        info[7] = peers;
        info[8] = seeds;
        info[9] = size;
        info[10] = time;
        return info;
    }

    private Icon getIcon() {
        if (initializer.isPartialDownload()) {
            try {
                return IconManager.instance().getIconForFile(new File(initializer.getDisplayName()));
            } catch (Exception e) {
                // ignore error
                return IconManager.instance().getIconForFile(initializer.getSaveLocation());
            }
        } else if (initializer instanceof SoundcloudDownload) {
            return IconManager.instance().getIconForFile(initializer.getSaveLocation());
        } else {
            return IconManager.instance().getIconForFile(initializer.getSaveLocation());
        }
    }

    /**
     * Updates all of the data for this download, obtaining fresh information
     * from the contained <tt>Downloader</tt> instance.
     */
    @Override
    public void update() {
        transferState = initializer.getState();
        progress = initializer.getProgress();
        download = initializer.getBytesReceived();
        upload = initializer.getBytesSent();
        downloadSpeed = initializer.getDownloadSpeed();
        uploadSpeed = initializer.getUploadSpeed();
        timeLeft = initializer.getETA();
        seeds = initializer.getSeedsString();
        peers = initializer.getPeersString();
        shareRatio = initializer.getShareRatio();
        seedToPeerRatio = initializer.getSeedToPeerRatio();
        size = initializer.getSize();
        dateCreated = initializer.getDateCreated();
        if (initializer.getCopyrightLicenseBroker() != null &&
                initializer.getCopyrightLicenseBroker().license != null) {
            license = initializer.getCopyrightLicenseBroker().license.getName();
        } else {
            license = "";
        }
        if (initializer.getPaymentOptions() != null) {
            paymentOptions = initializer.getPaymentOptions();
        }
        if (getInitializeObject().isCompleted()) {
            showNotification();
        }
    }

    private void showNotification() {
        if (!notificationShown) {
            notificationShown = true;
            BTDownload theDownload = getInitializeObject();
            if (theDownload.isCompleted()) {
                LibraryMediator.instance().getLibraryExplorer().clearDirectoryHolderCaches();
            }
        }
    }

    @Override
    public boolean isDynamic(int col) {
        return false;
    }

    @Override
    public boolean isClippable(int col) {
        return false;
    }
}
