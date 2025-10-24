/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.services;

import static com.frostwire.android.util.SystemUtils.ensureBackgroundThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.postToHandler;

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
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.Objects;

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
                postToHandler(SystemUtils.HandlerThreadName.MISC, () -> EngineBroadcastReceiver.handleMediaMounted(context, intent));
            } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                postToHandler(SystemUtils.HandlerThreadName.MISC, () -> handleMediaUnmounted(intent));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                // doesn't do anything except log, no need for async
                handleActionPhoneStateChanged(intent);
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                postToHandler(SystemUtils.HandlerThreadName.MISC, () -> handleConnectivityChange(context, intent));
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
        if (networkInfo == null) {
            LOG.warn("EngineBroadcastReceiver.handleConnectivityChange() aborted, could not get NetworkInfo");
            return;
        }
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
        ensureBackgroundThreadOrCrash("EngineBroadcastReceiver.handleDisconnectedNetwork must be called from a background thread");
        LOG.info("Disconnected from network (" + networkInfo.getTypeName() + ")");

        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                isNetworkVPN(networkInfo)) {
            //don't stop
            return;
        }

        // Already running on background thread (posted from onReceive), no need for additional postToHandler
        Engine.instance().stopServices(true);
    }

    private void handleConnectedNetwork(Context context, NetworkInfo networkInfo) {
        NetworkManager networkManager = NetworkManager.instance();
        if (networkManager.isInternetDataConnectionUp()) {
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
                }
                if (shouldStopSeeding()) {
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
            String path = Objects.requireNonNull(intent.getDataString()).replace("file://", "");
            if (!SystemUtils.isPrimaryExternalPath(new File(path))) {
                UIUtils.broadcastAction(context, Constants.ACTION_NOTIFY_SDCARD_MOUNTED);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (Engine.instance().isDisconnected()) {
            if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) ||
                    NetworkManager.instance().isTunnelUp()) {
                Engine.instance().startServices();
            }
        }
    }

    /**
     * Makes sure the current save location will be the primary external if
     * the media being unmounted is the sd card.
     */
    private void handleMediaUnmounted(Intent intent) {
        String path = Objects.requireNonNull(intent.getDataString()).replace("file://", "");
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
