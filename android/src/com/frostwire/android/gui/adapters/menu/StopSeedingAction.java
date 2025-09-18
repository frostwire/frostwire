/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.Logger;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class StopSeedingAction extends MenuAction {
    private static final Logger LOG = Logger.getLogger(StopSeedingAction.class);
    private final BittorrentDownload btDownload;
    private final Transfer transferToClear;

    private StopSeedingAction(Context context,
                              BittorrentDownload existingBittorrentDownload,
                              @SuppressWarnings("SameParameterValue") Transfer transferToClear) {
        super(context,
                R.drawable.contextmenu_icon_seed,
                R.string.seed_stop,
                UIUtils.getAppIconPrimaryColor(context));
        this.btDownload = existingBittorrentDownload;
        this.transferToClear = transferToClear;
    }


    @Override
    public void onClick(Context context) {
        stopSeedingEm();
        UIUtils.showTransfersOnDownloadStart(getContext());
    }

    public StopSeedingAction(Context context, BittorrentDownload download) {
        this(context, download, null);
    }

    private void stopSeedingEm() {
        if (btDownload != null) {
            stopSeedingBTDownload();
        }
        if (transferToClear != null) {
            TransferManager.instance().remove(transferToClear);
        }
    }

    private void stopSeedingBTDownload() {
        final Object torrentHandle = BTEngine.getInstance().find(new Sha1Hash(btDownload.getInfoHash()));
        if (torrentHandle != null) {
            btDownload.pause();
        } else {
            LOG.warn("stopSeedingBTDownload() could not find torrentHandle for existing torrent.");
        }
    }
}
