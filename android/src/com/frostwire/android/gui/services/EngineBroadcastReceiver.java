/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.services;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.UniversalScanner;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.util.SystemUtils;

/**
 * Receives and controls messages from the external world. Depending on the
 * status it attempts to control what happens with the Engine.
 *
 * @author gubatron
 * @author aldenml
 */
public class EngineBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "FW.EngineBroadcastReceiver";

    public EngineBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                handleMediaMounted(context, intent);

                if (Engine.instance().isDisconnected()) {
                    Engine.instance().getThreadPool().execute(new Runnable() {
                        @Override
                        public void run() {
                            Engine.instance().startServices();
                        }
                    });
                }
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                handleMediaUnmounted(intent);
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                handleActionPhoneStateChanged(intent);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                Librarian.instance().syncMediaStore();
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                if (networkInfo.getDetailedState() == DetailedState.DISCONNECTED) {
                    handleDisconnectedNetwork(networkInfo);
                } else if (networkInfo.getDetailedState() == DetailedState.CONNECTED) {
                    handleConnectedNetwork(networkInfo);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error processing broadcast message", e);
        }
    }

    private void handleActionPhoneStateChanged(Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String msg = "Phone state changed to " + state;
        Log.v(TAG, msg);
    }

    private void handleDisconnectedNetwork(NetworkInfo networkInfo) {
        Log.v(TAG, "Disconnected from network (" + networkInfo.getTypeName() + ")");
        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Engine.instance().stopServices(true);
            }
        });
    }

    private void handleConnectedNetwork(NetworkInfo networkInfo) {
        if (NetworkManager.instance().isDataUp()) {

            boolean useTorrentsOnMobileData = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);

            // "Boolean Master", just for fun.
            // Let a <= "mobile up",
            //     b <= "use torrents on mobile"
            //
            // In English:
            // is mobile data up and not user torrents on mobile? then abort else start services.
            //
            // In Boolean:
            // if (a && !b) then return; else start services.
            //
            // since early 'return' statements are a source of evil, I'll use boolean algebra...
            // so that we can instead just start services under the right conditions.
            //
            // negating "a && !b" I get...
            // ^(a && !b) => ^a || b
            //
            // In English:
            // if not mobile up or use torrents on mobile data then start services. (no else needed)
            //
            // mobile up means only mobile data is up and wifi is down.

            if (!NetworkManager.instance().isDataMobileUp() || useTorrentsOnMobileData) {
                Log.v(TAG, "Connected to " + networkInfo.getTypeName());
                if (Engine.instance().isDisconnected()) {
                    // avoid ANR error inside a broadcast receiver
                    Engine.instance().getThreadPool().execute(new Runnable() {
                        @Override
                        public void run() {
                            Engine.instance().startServices();

                            if (shouldStopSeeding()) {
                                TransferManager.instance().stopSeedingTorrents();
                            }
                        }
                    });
                } else if (shouldStopSeeding()) {
                    TransferManager.instance().stopSeedingTorrents();
                }
            }
        }
    }

    private boolean shouldStopSeeding() {
        return !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS) || (!NetworkManager.instance().isDataWIFIUp() && ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY));
    }

    private void handleMediaMounted(final Context context, Intent intent) {
        try {
            String path = intent.getDataString().replace("file://", "");
            if (!SystemUtils.isPrimaryExternalPath(new File(path))) {
                Intent i = new Intent(Constants.ACTION_NOTIFY_SDCARD_MOUNTED);
                context.sendBroadcast(i);

                if (SystemUtils.hasKitKatOrNewer()) {
                    final File privateDir = new File(path + File.separator + "Android" + File.separator + "data" + File.separator + context.getPackageName() + File.separator + "files" + File.separator + "FrostWire");
                    if (privateDir.exists() && privateDir.isDirectory()) {
                        Thread t = new Thread(new Runnable() {

                            @Override
                            public void run() {
                                new UniversalScanner(context).scanDir(privateDir);
                            }
                        });

                        t.setName("Private MediaScanning");
                        t.setDaemon(true);
                        t.start();
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * make sure the current save location will be the primary external if
     * the media being unmounted is the sd card.
     *
     * @param intent
     */
    private void handleMediaUnmounted(Intent intent) {
        String path = intent.getDataString().replace("file://", "");
        if (!SystemUtils.isPrimaryExternalPath(new File(path)) &&
                SystemUtils.isPrimaryExternalStorageMounted()) {
            File primaryExternal = Environment.getExternalStorageDirectory();
            ConfigurationManager.instance().setStoragePath(primaryExternal.getAbsolutePath());
        }
    }
}
