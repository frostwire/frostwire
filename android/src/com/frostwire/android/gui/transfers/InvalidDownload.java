/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.transfers;

import com.frostwire.android.R;
import com.frostwire.search.SearchResult;
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
    private final SearchResult sr;

    public InvalidDownload(int reasonResId, SearchResult searchResult) {
        this.reasonResId = reasonResId;
        this.sr = searchResult;
    }

    public InvalidDownload(){
        this.reasonResId = R.string.download_type_not_supported;
        sr = null;
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

    @Override
    public SearchResult getSearchResult() {
        return sr;
    }
}
