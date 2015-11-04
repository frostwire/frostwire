/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

    public long getSize();

    public String getName();

    public String getDisplayName();

    public boolean isResumable();

    public boolean isPausable();

    public boolean isCompleted();

    public TransferState getState();

    public void remove();

    public void pause();

    public File getSaveLocation();

    public void resume();

    public int getProgress();

    public long getBytesReceived();

    public long getBytesSent();

    public double getDownloadSpeed();

    public double getUploadSpeed();

    public long getETA();

    public String getPeersString();

    public String getSeedsString();

    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove);

    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove);

    public String getHash();

    public String getSeedToPeerRatio();

    public String getShareRatio();

    public boolean isPartialDownload();

    public Date getDateCreated();

    public PaymentOptions getPaymentOptions();

    public CopyrightLicenseBroker getCopyrightLicenseBroker();

    public boolean canPreview();

    public File getPreviewFile();
}
