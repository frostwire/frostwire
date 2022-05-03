/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.transfers.Transfer;

/**
 * @author gubatron
 */
public class RetryDownloadAction extends MenuAction {

    private final Transfer transfer;

    public RetryDownloadAction(Context context, Transfer transfer) {
        super(context, R.drawable.contextmenu_icon_download, R.string.retry);
        this.transfer = transfer;
    }

    @Override
    public void onClick(Context context) {
        TransferManager.instance().remove(transfer);

        if (transfer instanceof InvalidTransfer) {
            retryInvalidTransfer();
        } else if (transfer instanceof TorrentFetcherDownload) {
            retryTorrentFetcherDownload();
        }
    }

    private void retryTorrentFetcherDownload() {
        TorrentFetcherDownload tfd = (TorrentFetcherDownload) transfer;

        TransferManager.instance().downloadTorrent(
                tfd.getTorrentDownloadInfo().getTorrentUrl(),
                tfd.getTorrentFetcherListener(),
                tfd.getName());
    }

    private void retryInvalidTransfer() {
        InvalidTransfer it = (InvalidTransfer) transfer;
        TransferManager.instance().download(it.getSearchResult());
    }
}
