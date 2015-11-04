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

import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconManager;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.notify.Notification;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.gui.tables.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class handles all of the data for a single download, representing
 * one "line" in the download window.  It continually updates the
 * displayed data for the download from the contained <tt>Downloader</tt>
 * instance.
 */
final class BTDownloadDataLine extends AbstractDataLine<BTDownload> {
    
    private static final String PARTIAL_DOWNLOAD_TEXT = I18n.tr(" (Handpicked)");

    /**
     * Variable for the status of the download.
     */
    private TransferState _transferState;

    /**
     * Variable for the amount of the file that has been read.
     */
    private long _download = 0;

    private long _upload;

    /**
     * Variable for the progress made in the progress bar.
     */
    private int _progress;

    /**
     * Variable for the size of the download.
     */
    private long _size = -1;

    /**
     * Variable for the speed of the download.
     */
    private double _downloadSpeed;

    private double _uploadSpeed;

    /**
     * Variable for how much time is left.
     */
    private long _timeLeft;

    private String _seeds;

    private String _peers;

    private String _shareRatio;

    private String _seedToPeerRatio;

    private Date dateCreated;
    
    private String license;

    private boolean _notification;

    private PaymentOptions paymentOptions;

    static final int ACTIONS_INDEX = 0;
    private static final LimeTableColumn ACTIONS_COLUMN = new LimeTableColumn(ACTIONS_INDEX, "TRANSFER_ACTIONS", I18n.tr("Actions"), 23, true, TransferHolder.class);

    /**
     * Column index for the file name.
     */
    static final int FILE_INDEX = 1;
    private static final LimeTableColumn FILE_COLUMN = new LimeTableColumn(FILE_INDEX, "DOWNLOAD_NAME_COLUMN", I18n.tr("Name"), 201, true, IconAndNameHolder.class);

    /** Column index for name-your-price/tips/donations */
    static final int PAYMENT_OPTIONS_INDEX = 2;
    private static final LimeTableColumn PAYMENT_OPTIONS_COLUMN = new LimeTableColumn(PAYMENT_OPTIONS_INDEX, "PAYMENT_OPTIONS_COLUMN", I18n.tr("Tips/Donations"), 65, true, PaymentOptions.class );
    
    /**
     * Column index for the file size.
     */
    static final int SIZE_INDEX = 3;
    private static final LimeTableColumn SIZE_COLUMN = new LimeTableColumn(SIZE_INDEX, "DOWNLOAD_SIZE_COLUMN", I18n.tr("Size"), 65, true, SizeHolder.class);

    /**
     * Column index for the file download status.
     */
    static final int STATUS_INDEX = 4;
    private static final LimeTableColumn STATUS_COLUMN = new LimeTableColumn(STATUS_INDEX, "DOWNLOAD_STATUS_COLUMN", I18n.tr("Status"), 152, true, String.class);

    /**
     * Column index for the progress of the download.
     */
    static final int PROGRESS_INDEX = 5;
    private static final LimeTableColumn PROGRESS_COLUMN = new LimeTableColumn(PROGRESS_INDEX, "DOWNLOAD_PROGRESS_COLUMN", I18n.tr("Progress"), 71, true, ProgressBarHolder.class);

    /**
     * Column index for actual amount of bytes downloaded.
     */
    static final int BYTES_DOWNLOADED_INDEX = 6;
    private static final LimeTableColumn BYTES_DOWNLOADED_COLUMN = new LimeTableColumn(BYTES_DOWNLOADED_INDEX, "DOWNLOAD_BYTES_DOWNLOADED_COLUMN", I18n.tr("Downloaded"), 20, true, SizeHolder.class);

    static final int BYTES_UPLOADED_INDEX = 7;
    private static final LimeTableColumn BYTES_UPLOADED_COLUMN = new LimeTableColumn(BYTES_UPLOADED_INDEX, "DOWNLOAD_BYTES_UPLOADED_COLUMN", I18n.tr("Uploaded"), 20, false, SizeHolder.class);

    /**
     * Column index for the download speed.
     */
    static final int DOWNLOAD_SPEED_INDEX = 8;
    private static final LimeTableColumn DOWNLOAD_SPEED_COLUMN = new LimeTableColumn(DOWNLOAD_SPEED_INDEX, "DOWNLOAD_SPEED_COLUMN", I18n.tr("Down Speed"), 58, true, SpeedRenderer.class);

    static final int UPLOAD_SPEED_INDEX = 9;
    private static final LimeTableColumn UPLOAD_SPEED_COLUMN = new LimeTableColumn(UPLOAD_SPEED_INDEX, "UPLOAD_SPEED_COLUMN", I18n.tr("Up Speed"), 58, true, SpeedRenderer.class);

    /**
     * Column index for the download time remaining.
     */
    static final int TIME_INDEX = 10;
    private static final LimeTableColumn TIME_COLUMN = new LimeTableColumn(TIME_INDEX, "DOWNLOAD_TIME_REMAINING_COLUMN", I18n.tr("Time"), 49, true, TimeRemainingHolder.class);

    static final int SEEDS_INDEX = 11;
    private static final LimeTableColumn SEEDS_COLUMN = new LimeTableColumn(SEEDS_INDEX, "SEEDS_STATUS_COLUMN", I18n.tr("Seeds"), 80, true, String.class);

    static final int PEERS_INDEX = 12;
    private static final LimeTableColumn PEERS_COLUMN = new LimeTableColumn(PEERS_INDEX, "PEERS_STATUS_COLUMN", I18n.tr("Peers"), 80, false, String.class);

    static final int SHARE_RATIO_INDEX = 13;
    private static final LimeTableColumn SHARE_RATIO_COLUMN = new LimeTableColumn(SHARE_RATIO_INDEX, "SHARE_RATIO_COLUMN", I18n.tr("Share Ratio"), 80, false, String.class);

    static final int SEED_TO_PEER_RATIO_INDEX = 14;
    private static final LimeTableColumn SEED_TO_PEER_RATIO_COLUMN = new LimeTableColumn(SEED_TO_PEER_RATIO_INDEX, "SEED_TO_PEER_RATIO_COLUMN", I18n.tr("Seeds/Peers"), 80, false, String.class);

    static final int DATE_CREATED_INDEX = 15;
    static final LimeTableColumn DATE_CREATED_COLUMN = new LimeTableColumn(DATE_CREATED_INDEX, "DATE_CREATED_COLUMN", I18n.tr("Started On"), 80, false, Date.class);

    static final int LICENSE_INDEX = 16;
    static final LimeTableColumn LICENSE_COLUMN = new LimeTableColumn(LICENSE_INDEX, "LICENSE_COLUMN", I18n.tr("License"), 80, false, String.class);

    /**
     * Number of columns to display
     */
    static final int NUMBER_OF_COLUMNS = 17;

    // Implements DataLine interface
    public int getColumnCount() {
        return NUMBER_OF_COLUMNS;
    }

    public static Map<TransferState, String> TRANSFER_STATE_STRING_MAP =
            new HashMap<TransferState, String>();

    static {
        TRANSFER_STATE_STRING_MAP.put(TransferState.QUEUED_FOR_CHECKING, I18n.tr("Queued for checking"));
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
     * Must initialize data.
     *
     * @param downloader the <tt>Downloader</tt>
     *  that provides access to
     *  information about the download
     */
    public void initialize(BTDownload downloader) {
        super.initialize(downloader);
        _notification = downloader.isCompleted();
        paymentOptions = initializer.getPaymentOptions(); // Comes with item name preset.
        update();
    }

    public boolean isSeeding() {
        if (initializer == null) {
            return false;
        }

        return initializer.getState() == TransferState.SEEDING;
    }

    /**
     * Returns the <tt>Object</tt> stored at the specified column in this
     * line of data.
     *
     * @param index the index of the column to retrieve data from
     * @return the <tt>Object</tt> stored at that index
     * @implements DataLine interface
     */
    public Object getValueAt(int index) {
        switch (index) {
            case FILE_INDEX:
                return new IconAndNameHolderImpl(getIcon(), initializer.getDisplayName());
            case PAYMENT_OPTIONS_INDEX:
                return paymentOptions;
            case SIZE_INDEX:
                if (initializer.isPartialDownload()) {
                    return new SizeHolder(_size, PARTIAL_DOWNLOAD_TEXT);
                } else {
                    return new SizeHolder(_size);
                }
            case STATUS_INDEX:
                String status = TRANSFER_STATE_STRING_MAP.get(_transferState);
                if (status == null) {
                    status = I18n.tr("Unknown status");
                }
                return status;
            case PROGRESS_INDEX:
                return Integer.valueOf(_progress);
            case BYTES_DOWNLOADED_INDEX:
                return new SizeHolder(_download);
            case BYTES_UPLOADED_INDEX:
                return new SizeHolder(_upload);
            case DOWNLOAD_SPEED_INDEX:
                return new Double(_downloadSpeed);
            case UPLOAD_SPEED_INDEX:
                return new Double(_uploadSpeed);
            case TIME_INDEX:
                if (initializer.isCompleted()) {
                    return new TimeRemainingHolder(0);
                } else if (_downloadSpeed < 0.001) {
                    return new TimeRemainingHolder(-1);
                } else {
                    return new TimeRemainingHolder(_timeLeft);
                }
            case SEEDS_INDEX:
                return new SeedsHolder(_seeds);
            case PEERS_INDEX:
                return _peers;
            case SHARE_RATIO_INDEX:
                return _shareRatio;
            case SEED_TO_PEER_RATIO_INDEX:
                return _seedToPeerRatio;
            case DATE_CREATED_INDEX:
                return dateCreated;
            case LICENSE_INDEX:
                return license;
            case ACTIONS_INDEX:
                return new TransferHolder(initializer);
        }
        return null;
    }

    /**
     * @implements DataLine interface
     */
    public LimeTableColumn getColumn(int idx) {
        return staticGetColumn(idx);
    }

    static LimeTableColumn staticGetColumn(int idx) {
        switch (idx) {
            case FILE_INDEX:
                return FILE_COLUMN;
            case PAYMENT_OPTIONS_INDEX:
                return PAYMENT_OPTIONS_COLUMN;
            case SIZE_INDEX:
                return SIZE_COLUMN;
            case STATUS_INDEX:
                return STATUS_COLUMN;
            case PROGRESS_INDEX:
                return PROGRESS_COLUMN;
            case BYTES_DOWNLOADED_INDEX:
                return BYTES_DOWNLOADED_COLUMN;
            case BYTES_UPLOADED_INDEX:
                return BYTES_UPLOADED_COLUMN;
            case DOWNLOAD_SPEED_INDEX:
                return DOWNLOAD_SPEED_COLUMN;
            case UPLOAD_SPEED_INDEX:
                return UPLOAD_SPEED_COLUMN;
            case TIME_INDEX:
                return TIME_COLUMN;
            case SEEDS_INDEX:
                return SEEDS_COLUMN;
            case PEERS_INDEX:
                return PEERS_COLUMN;
            case SHARE_RATIO_INDEX:
                return SHARE_RATIO_COLUMN;
            case SEED_TO_PEER_RATIO_INDEX:
                return SEED_TO_PEER_RATIO_COLUMN;
            case DATE_CREATED_INDEX:
                return DATE_CREATED_COLUMN;
            case LICENSE_INDEX:
                return LICENSE_COLUMN;
            case ACTIONS_INDEX:
                return ACTIONS_COLUMN;
        }
        return null;
    }

    public int getTypeAheadColumn() {
        return FILE_INDEX;
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
        } else if (initializer instanceof YouTubeDownload || initializer instanceof SoundcloudDownload) {
            return IconManager.instance().getIconForFile(initializer.getSaveLocation());
        } else {
            return IconManager.instance().getIconForFile(initializer.getSaveLocation());
        }
    }

    /**
     * Updates all of the data for this download, obtaining fresh information
     * from the contained <tt>Downloader</tt> instance.
     *
     * @implements DataLine interface
     */
    public void update() {
        _transferState = initializer.getState();
        _progress = initializer.getProgress();
        _download = initializer.getBytesReceived();
        _upload = initializer.getBytesSent();
        _downloadSpeed = initializer.getDownloadSpeed();
        _uploadSpeed = initializer.getUploadSpeed();
        _timeLeft = initializer.getETA();
        _seeds = initializer.getSeedsString();
        _peers = initializer.getPeersString();
        _shareRatio = initializer.getShareRatio();
        _seedToPeerRatio = initializer.getSeedToPeerRatio();
        _size = initializer.getSize();
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
        if (!_notification) {
            _notification = true;
            Notification notification = null;
            BTDownload theDownload = getInitializeObject();
            if (theDownload.isCompleted()) {
                Action[] actions = null;
                File file = getInitializeObject().getSaveLocation();
                if (file != null) {
                    actions = new Action[] { new LaunchAction(file), new ShowInLibraryAction(file) };
                }
                notification = new Notification(theDownload.getDisplayName(), getIcon(), actions);
                LibraryMediator.instance().getLibraryExplorer().clearDirectoryHolderCaches();
            } else {
                return;
            }

            if (notification != null) {
                NotifyUserProxy.instance().showMessage(notification);
            }
        }
    }

    private final class LaunchAction extends AbstractAction {

        private static final long serialVersionUID = 4020797972200661119L;

        private File file;

        public LaunchAction(File file) {
            this.file = file;

            putValue(Action.NAME, I18n.tr("Launch"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Launch Selected Files"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_LAUNCH");
        }

        public void actionPerformed(ActionEvent ae) {
            // It adds and plays the current file to the media library when user clicks from the "Launch" notification Window.        
            GUIUtils.launchOrEnqueueFile(file, false);
        }
    }

    private final class ShowInLibraryAction extends AbstractAction {

        private File file;

        public ShowInLibraryAction(File file) {
            this.file = file;

            putValue(Action.NAME, I18n.tr("Show in Library"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Show Download in Library"));
        }

        public void actionPerformed(ActionEvent ae) {
            GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
            LibraryMediator.instance().setSelectedFile(file);
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
