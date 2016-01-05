/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.BittorrentDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
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
            if (NetworkManager.instance().isDataUp()) {
                if (download.isResumable()) {
                    download.resume();
                    UXStats.instance().log(UXAction.DOWNLOAD_RESUME);
                }
            } else {
                UIUtils.showShortMessage(context, R.string.please_check_connection_status_before_resuming_download);
            }
        }
    }
}
