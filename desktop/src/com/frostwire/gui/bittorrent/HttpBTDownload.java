/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.settings.SharingSettings;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Date;

/**
 * Created on 12/8/16.
 *
 * @author gubatron
 * @author aldenml
 */
abstract class HttpBTDownload implements BTDownload {
    private static final Logger LOG = Logger.getLogger(HttpBTDownload.class);
    private static final int SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS = 1000;
    final File completeFile;
    final HttpClient httpClient;
    private final Date dateCreated;
    double size;
    TransferState state;
    long bytesReceived;
    HttpClient.HttpClientListener httpClientListener;
    private long averageSpeed; // in bytes
    private boolean deleteDataWhenRemoved;
    private long speedMarkTimestamp;
    private long totalReceivedSinceLastSpeedStamp;

    HttpBTDownload(String filename, double size) {
        completeFile = org.limewire.util.FileUtils.buildFile(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), filename);
        this.size = size;
        dateCreated = new Date();
        bytesReceived = 0;
        httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
        httpClient.setListener(createHttpClientListener());
    }

    static boolean copyPlayingTemp(File temp, File dest) {
        boolean r;
        System.out.println(temp);
        try {
            FileUtils.copyFile(temp, dest);
            r = true;
        } catch (Throwable e) {
            e.printStackTrace();
            r = false;
        }
        return r;
    }

    static File getIncompleteFolder() {
        File incompleteFolder = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue().getParentFile(), "Incomplete");
        if (!incompleteFolder.exists()) {
            if (!incompleteFolder.mkdirs()) {
                LOG.warn("BTDownloadUtils.getIncompleteFolder(): could not mkdirs for [" + incompleteFolder.getAbsolutePath() + "]");
            }
        }
        return incompleteFolder;
    }

    static File buildTempFile(String name, String ext) {
        return new File(HttpBTDownload.getIncompleteFolder(), name + "." + ext);
    }

    abstract HttpClient.HttpClientListener createHttpClientListener();

    void updateAverageDownloadSpeed() {
        long now = System.currentTimeMillis();
        if (isCompleted()) {
            averageSpeed = 0;
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = 0;
        } else if (now - speedMarkTimestamp > SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS) {
            averageSpeed = ((bytesReceived - totalReceivedSinceLastSpeedStamp) * 1000) / (now - speedMarkTimestamp);
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = bytesReceived;
        }
    }

    void cleanup() {
        cleanupIncomplete();
        cleanupComplete();
    }

    void cleanupFile(File f) {
        if (f.exists()) {
            boolean delete = f.delete();
            if (!delete) {
                f.deleteOnExit();
            }
        }
    }

    abstract void cleanupIncomplete();

    private void cleanupComplete() {
        cleanupFile(completeFile);
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    abstract public String getName();

    @Override
    abstract public String getDisplayName();

    @Override
    public boolean isResumable() {
        return false;
    }

    @Override
    public boolean isPausable() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return bytesReceived > 0 && (bytesReceived == size || state == TransferState.FINISHED);
    }

    @Override
    public TransferState getState() {
        return state;
    }

    @Override
    public void remove() {
        if (state != TransferState.FINISHED) {
            state = TransferState.CANCELING;
            httpClient.cancel();
        }
        if (deleteDataWhenRemoved) {
            cleanup();
            if (!getSaveLocation().delete()) {
                LOG.warn("HttpBTDownload.remove(): could not delete [" + getSaveLocation().getAbsolutePath() + "]");
            }
        }
    }

    @Override
    public void pause() {
        if (state != TransferState.FINISHED) {
            state = TransferState.CANCELING;
            httpClient.cancel();
        }
    }

    @Override
    public File getSaveLocation() {
        return completeFile;
    }

    @Override
    abstract public void resume();

    @Override
    public int getProgress() {
        int progress = -1;
        if (size > 0) {
            if (isCompleted()) {
                progress = 100;
            } else {
                progress = (int) ((bytesReceived * 100) / size);
                progress = Math.min(100, progress);
            }
        }
        return progress;
    }

    @Override
    public long getBytesReceived() {
        if (isCompleted() && getSaveLocation().exists()) {
            bytesReceived = getSaveLocation().length();
        }
        return bytesReceived;
    }

    @Override
    public long getBytesSent() {
        return 0;
    }

    @Override
    public double getDownloadSpeed() {
        double result = 0;
        if (state == TransferState.DOWNLOADING) {
            result = averageSpeed / 1000;
        }
        return result;
    }

    @Override
    public double getUploadSpeed() {
        return 0;
    }

    @Override
    public long getETA() {
        if (size > 0) {
            long speed = averageSpeed;
            return speed > 0 ? (long) ((size - getBytesReceived()) / speed) : -1;
        } else {
            return -1;
        }
    }

    @Override
    public String getPeersString() {
        return "";
    }

    @Override
    public String getSeedsString() {
        return "";
    }

    @Override
    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove) {
    }

    @Override
    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove) {
        this.deleteDataWhenRemoved = deleteDataWhenRemove;
    }

    @Override
    abstract public String getHash();

    @Override
    public String getSeedToPeerRatio() {
        return "";
    }

    @Override
    public String getShareRatio() {
        return "";
    }

    @Override
    public boolean isPartialDownload() {
        return false;
    }

    @Override
    public Date getDateCreated() {
        return dateCreated;
    }

    @Override
    public PaymentOptions getPaymentOptions() {
        return null;
    }

    @Override
    public CopyrightLicenseBroker getCopyrightLicenseBroker() {
        return null;
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    @Override
    abstract public File getPreviewFile();
}
