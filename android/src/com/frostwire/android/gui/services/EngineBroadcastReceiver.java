/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Environment;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.frostwire.util.Ref;

import java.io.File;

import static com.frostwire.android.util.Asyncs.async;

/**
 * Receives and controls messages from the external world. Depending on the
 * status it attempts to control what happens with the Engine.
 *
 * @author gubatron
 * @author aldenml
 */
public class EngineBroadcastReceiver extends BroadcastReceiver {

    private static final Logger LOG = Logger.getLogger(EngineBroadcastReceiver.class);

    public EngineBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();

            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                async(context, EngineBroadcastReceiver::handleMediaMounted, intent);
            } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                async(this, EngineBroadcastReceiver::handleMediaUnmounted, intent);
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                // doesn't do anything except log, no need for async
                handleActionPhoneStateChanged(intent);
            } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                // heavy lifting happens in Thread()
                Librarian.instance().syncMediaStore(Ref.weak(context));
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                async(this, EngineBroadcastReceiver::handleConnectivityChange, context, intent);
            }
        } catch (Throwable e) {
            LOG.error("Error processing broadcast message", e);
        }
    }

    private void handleActionPhoneStateChanged(Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String msg = "Phone state changed to " + state;
        LOG.info(msg);
    }

    private void handleConnectivityChange(Context context, Intent intent) {
        NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        DetailedState detailedState = networkInfo.getDetailedState();
        switch (detailedState) {
            case CONNECTED:
                handleConnectedNetwork(context, networkInfo);
                handleNetworkStatusChange();
                reopenNetworkSockets();
                break;
            case DISCONNECTED:
                handleDisconnectedNetwork(networkInfo);
                handleNetworkStatusChange();
                reopenNetworkSockets();
                break;
            case CONNECTING:
            case DISCONNECTING:
                handleNetworkStatusChange();
                break;
            default:
                break;
        }
    }

    private void handleNetworkStatusChange() {
        NetworkManager.queryNetworkStatusBackground(NetworkManager.instance());
    }

    private void handleDisconnectedNetwork(NetworkInfo networkInfo) {
        LOG.info("Disconnected from network (" + networkInfo.getTypeName() + ")");

        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                isNetworkVPN(networkInfo)) {
            //don't stop
            return;
        }
        async(Engine.instance()::stopServices, true);
    }

    private void handleConnectedNetwork(Context context, NetworkInfo networkInfo) {
        PlayStore.getInstance(context).refresh();
        NetworkManager networkManager = NetworkManager.instance();
        if (networkManager.isDataUp()) {
            ConfigurationManager CM = ConfigurationManager.instance();
            boolean useTorrentsOnMobileData = !CM.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);

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

            if (!networkManager.isDataMobileUp() || useTorrentsOnMobileData) {
                LOG.info("Connected to " + networkInfo.getTypeName());
                if (Engine.instance().isDisconnected()) {
                    // avoid ANR error inside a broadcast receiver

                    if (CM.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                            !(networkManager.isTunnelUp() || isNetworkVPN(networkInfo))) {
                        //don't start
                        return;
                    }

                    Engine.instance().startServices();
                    if (shouldStopSeeding()) {
                        TransferManager.instance().stopSeedingTorrents();
                    }
                } else if (shouldStopSeeding()) {
                    TransferManager.instance().stopSeedingTorrents();
                }
            }
        }
    }

    private boolean shouldStopSeeding() {
        ConfigurationManager CM = ConfigurationManager.instance();
        return !CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS) ||
                (!NetworkManager.instance().isDataWIFIUp() &&
                        CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY));
    }

    private static void handleMediaMounted(final Context context, Intent intent) {
        try {
            String path = intent.getDataString().replace("file://", "");
            if (!SystemUtils.isPrimaryExternalPath(new File(path))) {
                UIUtils.broadcastAction(context, Constants.ACTION_NOTIFY_SDCARD_MOUNTED);

                final File privateDir = new File(path + File.separator + "Android" + File.separator + "data" + File.separator + context.getPackageName() + File.separator + "files" + File.separator + "FrostWire");
                if (privateDir.exists() && privateDir.isDirectory() && !SystemUtils.hasAndroid10OrNewer()) {
                    Platforms.fileSystem().scan(privateDir);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (Engine.instance().isDisconnected()) {
            if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                !NetworkManager.instance().isTunnelUp()) {
                //don't start
            } else {
                Engine.instance().startServices();
            }
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

    // on some devices, the VPN network is properly identified with
    // the VPN type, some research is necessary to determine if this
    // is valid a valid check, and probably replace the dev/tun test
    private static boolean isNetworkVPN(NetworkInfo networkInfo) {
        // the constant TYPE_VPN=17 is in API 21, but using
        // the type name is OK for now
        String typeName = networkInfo.getTypeName();
        return typeName != null && typeName.contains("VPN");
    }

    private static void reopenNetworkSockets() {
        // sleep for a second, since IPv6 addresses takes time to be reported
        SystemClock.sleep(1000);
        BTEngine.getInstance().reopenNetworkSockets();
    }
}
