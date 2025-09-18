/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 8/28/17.
 */


final class OnBittorrentConnectRunnable implements Runnable {
    private final WeakReference<MenuAction> menuActionRef;
    private static final Logger LOG = Logger.getLogger(OnBittorrentConnectRunnable.class);

    OnBittorrentConnectRunnable(MenuAction menuAction) {
        menuActionRef = Ref.weak(menuAction);
    }

    public void run() {
        Engine.instance().startServices();
        while (!Engine.instance().isStarted()) {
            SystemClock.sleep(1000);
        }
        if (!Ref.alive(menuActionRef)) {
            return;
        }
        final MenuAction menuAction = menuActionRef.get();
        final Looper mainLooper = menuAction.getContext().getMainLooper();
        Handler h = new Handler(mainLooper);
        h.post(() -> {
            try {
                menuAction.onClick(menuAction.getContext());
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) {
                    throw t;
                }
                LOG.error("OnBittorrentConnectRunnable::run() error when posting to main looper: " + t.getMessage(), t);
            }
        });
    }

    void onBittorrentConnect(Context context) {
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                !NetworkManager.instance().isTunnelUp()) {
            if (context instanceof Activity) {
                UIUtils.showShortMessage(((Activity) context).getWindow().getDecorView().getRootView(), R.string.cannot_start_engine_without_vpn);
            } else {
                UIUtils.showShortMessage(context, R.string.cannot_start_engine_without_vpn);
            }
        } else {
            Engine.instance().getThreadPool().execute(this);
        }
    }
}
