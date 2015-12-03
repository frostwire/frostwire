/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import java.io.File;
import java.util.Date;

/**
 * @author gubatron
 * @author aldenml
 */
public interface BTDownload {

    long getSize();

    String getName();

    String getDisplayName();

    boolean isResumable();

    boolean isPausable();

    boolean isCompleted();

    TransferState getState();

    void remove();

    void pause();

    File getSaveLocation();

    void resume();

    int getProgress();

    long getBytesReceived();

    long getBytesSent();

    double getDownloadSpeed();

    double getUploadSpeed();

    long getETA();

    String getPeersString();

    String getSeedsString();

    void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove);

    void setDeleteDataWhenRemove(boolean deleteDataWhenRemove);

    String getHash();

    String getSeedToPeerRatio();

    String getShareRatio();

    boolean isPartialDownload();

    Date getDateCreated();

    PaymentOptions getPaymentOptions();

    CopyrightLicenseBroker getCopyrightLicenseBroker();

    boolean canPreview();

    File getPreviewFile();
}
