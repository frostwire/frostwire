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
 */
public final class ResumeDownloadMenuAction extends MenuAction implements AbstractDialog.OnDialogClickListener {

    private final BittorrentDownload download;
    private final OnBittorrentConnectRunnable onBittorrentConnectRunnable;
    private static final String DLG_TURN_BITTORRENT_BACK_ON = "0";


    public ResumeDownloadMenuAction(Context context, BittorrentDownload download, int stringId) {
        super(context, R.drawable.contextmenu_icon_play, stringId);
        this.download = download;
        this.onBittorrentConnectRunnable = new OnBittorrentConnectRunnable(this);
    }

    @Override
    public void onClick(Context context) {
        boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
        if (bittorrentDisconnected) {
            showBittorrentDisconnectedDialog();
        } else {
            if (NetworkManager.instance().isDataUp()) {
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
        dlg.show(((Activity) getContext()).getFragmentManager());
    }
}
