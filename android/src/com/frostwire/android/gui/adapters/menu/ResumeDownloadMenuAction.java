/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ResumeDownloadMenuAction extends MenuAction {

    private final BittorrentDownload download;

    public ResumeDownloadMenuAction(Context context, BittorrentDownload download, int stringId) {
        super(context, R.drawable.contextmenu_icon_play_transfer, stringId);
        this.download = download;
    }

    @Override
    protected void onClick(Context context) {
        boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
        if (bittorrentDisconnected) {
            UIUtils.showLongMessage(context, R.string.cant_resume_torrent_transfers);
        } else {
            NetworkManager networkManager = NetworkManager.instance();
            if (networkManager.isDataUp(networkManager.getConnectivityManager())) {
                if (download.isPaused()) {
                    download.resume();
                    UXStats.instance().log(UXAction.DOWNLOAD_RESUME);
                }
            } else {
                UIUtils.showShortMessage(context, R.string.please_check_connection_status_before_resuming_download);
            }
        }
    }
}
