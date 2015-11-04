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

import java.io.File;
import java.util.Date;
import java.util.List;

import com.frostwire.android.R;
import com.frostwire.transfers.TransferItem;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class InvalidDownload implements DownloadTransfer, InvalidTransfer {

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public Date getDateCreated() {
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
    public void cancel() {
    }

    @Override
    public int getReasonResId() {
        return R.string.download_type_not_supported;
    }

    @Override
    public File getSavePath() {
        return null;
    }

    public void cancel(boolean deleteData) {
    }
    
    @Override
    public String getDetailsUrl() {
        return null;
    }

    @Override
    public File previewFile() {
        return null;
    }
}
