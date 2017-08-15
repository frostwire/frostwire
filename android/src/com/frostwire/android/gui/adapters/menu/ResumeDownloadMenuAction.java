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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ResumeDownloadMenuAction extends MenuAction implements AbstractDialog.OnDialogClickListener {

    private final BittorrentDownload download;
    private static final String DLG_TURN_BITTORRENT_BACK_ON = "DLG_TURN_BITTORRENT_BACK_ON";

    public ResumeDownloadMenuAction(Context context, BittorrentDownload download, int stringId) {
        super(context, R.drawable.contextmenu_icon_play_transfer, stringId);
        this.download = download;
    }

    @Override
    protected void onClick(Context context) {
        boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
        if (bittorrentDisconnected) {
            showBittorrentDisconnectedDialog();
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

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(DLG_TURN_BITTORRENT_BACK_ON)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onBittorrentConnect();
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

    private void onBittorrentConnect() {

        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                !NetworkManager.instance().isTunnelUp()) {
            if (getContext() instanceof Activity) {
                UIUtils.showShortMessage(((Activity) getContext()).getWindow().getDecorView().getRootView(), R.string.cannot_start_engine_without_vpn);
            } else {
                UIUtils.showShortMessage(getContext(), R.string.cannot_start_engine_without_vpn);
            }
        } else {
            Engine.instance().getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    Engine.instance().startServices();
                    while (!Engine.instance().isStarted()) {
                        SystemClock.sleep(1000);
                    }
                    final Looper mainLooper = getContext().getMainLooper();
                    Handler h = new Handler(mainLooper);
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            onClick(getContext());
                        }
                    });
                }
            });
        }
    }
}
