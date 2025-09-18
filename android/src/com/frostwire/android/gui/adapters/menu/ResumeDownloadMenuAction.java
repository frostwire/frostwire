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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.transfers.BittorrentDownload;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class ResumeDownloadMenuAction extends MenuAction implements AbstractDialog.OnDialogClickListener {

    private final BittorrentDownload download;
    private final OnBittorrentConnectRunnable onBittorrentConnectRunnable;
    private static final String DLG_TURN_BITTORRENT_BACK_ON = "0";


    public ResumeDownloadMenuAction(Context context, BittorrentDownload download, int stringId) {
        super(context, R.drawable.contextmenu_icon_play, stringId, UIUtils.getAppIconPrimaryColor(context));
        this.download = download;
        this.onBittorrentConnectRunnable = new OnBittorrentConnectRunnable(this);
    }

    @Override
    public void onClick(Context context) {
        boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
        if (bittorrentDisconnected) {
            showBittorrentDisconnectedDialog();
        } else {
            if (NetworkManager.instance().isInternetDataConnectionUp()) {
                if (download.isPaused()) {
                    download.resume();
                    if (context instanceof TimerObserver) {
                        ((TimerObserver) context).onTime();
                    }
                }
            } else {
                UIUtils.showShortMessage(context, R.string.please_check_connection_status_before_resuming_download);
            }
        }
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(DLG_TURN_BITTORRENT_BACK_ON)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onBittorrentConnectRunnable.onBittorrentConnect(getContext());
            }
        }
    }

    private void showBittorrentDisconnectedDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                DLG_TURN_BITTORRENT_BACK_ON,
                R.string.bittorrent_off,
                R.string.bittorrent_is_currently_disconnected_would_you_like_me_to_start_it_for_you,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.setOnDialogClickListener(this);
        dlg.show(getFragmentManager());
    }
}
