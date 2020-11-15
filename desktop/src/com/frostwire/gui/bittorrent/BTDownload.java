/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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
    double getSize();

    @SuppressWarnings("unused")
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
