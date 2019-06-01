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

package com.frostwire.android.gui.transfers;

import com.frostwire.android.R;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class InvalidDownload implements Transfer, InvalidTransfer {

    private final int reasonResId;

    public InvalidDownload(int reasonResId) {
        this.reasonResId = reasonResId;
    }

    public InvalidDownload(){
        this.reasonResId = R.string.download_type_not_supported;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public TransferState getState() {
        return TransferState.UNKNOWN;
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public Date getCreated() {
        return null;
    }

    @Override
    public long getBytesReceived() {
        return 0;
    }

    @Override
    public long getBytesSent() {
        return 0;
    }

    @Override
    public long getDownloadSpeed() {
        return 0;
    }

    @Override
    public long getUploadSpeed() {
        return 0;
    }

    @Override
    public long getETA() {
        return 0;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public boolean isDownloading() {
        return false;
    }

    @Override
    public List<TransferItem> getItems() {
        return null;
    }

    @Override
    public int getReasonResId() {
        return reasonResId;
    }

    @Override
    public File getSavePath() {
        return null;
    }

    @Override
    public void remove(boolean deleteData) {
    }
    
    @Override
    public String getName() {
        return null;
    }

    @Override
    public File previewFile() {
        return null;
    }
}
