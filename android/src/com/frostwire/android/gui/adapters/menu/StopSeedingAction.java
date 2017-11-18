/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
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
 */
public class StopSeedingAction extends MenuAction {
    private static final Logger LOG = Logger.getLogger(StopSeedingAction.class);
    private final BittorrentDownload btDownload;
    private final Transfer transferToClear;

    private StopSeedingAction(Context context,
                              BittorrentDownload existingBittorrentDownload,
                              Transfer transferToClear) {
        super(context, R.drawable.contextmenu_icon_seed, R.string.seed_stop);
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
