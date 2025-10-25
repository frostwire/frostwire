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

import static com.frostwire.android.util.SystemUtils.postToHandler;
import static com.frostwire.android.util.SystemUtils.ensureBackgroundThreadOrCrash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
            } else if (Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION.equals(action)) {
                postToHandler(SystemUtils.HandlerThreadName.MISC, () -> handleNetworkStateChange(context, intent));
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

    /**
     * Handles network state changes from NetworkManager.
     * Applies user's WiFi-only and VPN guard settings.
     */
    private void handleNetworkStateChange(Context context, Intent intent) {
        ensureBackgroundThreadOrCrash("EngineBroadcastReceiver.handleNetworkStateChange must be called from a background thread");

        NetworkManager networkManager = NetworkManager.instance();
        boolean isDataUp = intent.getBooleanExtra("isDataUp", false);

        if (isDataUp) {
            handleConnectedNetwork(context, networkManager);
        } else {
            handleDisconnectedNetwork(networkManager);
        }

        handleNetworkStatusChange();
        reopenNetworkSockets();
    }

    /**
     * Handles network connection with WiFi-only and VPN guard protections.
     */
    private void handleConnectedNetwork(Context context, NetworkManager networkManager) {
        ConfigurationManager CM = ConfigurationManager.instance();

        // Check WiFi-only setting: don't start services if only mobile data is available
        // and user requires WiFi
        if (!networkManager.isDataWIFIUp() && !networkManager.isDataMobileUp()) {
            LOG.info("Connected to network but no WiFi or mobile data detected");
            return;
        }

        boolean useTorrentsOnMobileData = !CM.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
        boolean isMobileDataOnly = networkManager.isDataMobileUp() && !networkManager.isDataWIFIUp();

        // If only mobile data is available and user doesn't allow torrents on mobile
        if (isMobileDataOnly && !useTorrentsOnMobileData) {
            LOG.info("Connected to mobile network but user has WiFi-only setting enabled. Pausing torrents.");
            TransferManager.instance().pauseTorrents();
            Engine.instance().stopServices(true);
            return;
        }

        // Check VPN guard: if user requires VPN for torrents, check if VPN is available
        boolean vpnGuardEnabled = CM.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY);
        boolean hasVpn = networkManager.isTunnelUp() || networkManager.isVpnConnected();

        if (vpnGuardEnabled && !hasVpn) {
            LOG.info("VPN guard enabled but no VPN detected. Pausing torrents.");
            TransferManager.instance().pauseTorrents();
            Engine.instance().stopServices(true);
            return;
        }

        if (Engine.instance().isDisconnected()) {
            LOG.info("Connected to network. Starting services.");
            Engine.instance().startServices();
        }

        if (shouldStopSeeding()) {
            TransferManager.instance().stopSeedingTorrents();
        }
    }

    /**
     * Handles network disconnection with VPN guard consideration.
     */
    private void handleDisconnectedNetwork(NetworkManager networkManager) {
        LOG.info("Disconnected from network");

        // If VPN guard is enabled and we still have VPN, don't stop
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                (networkManager.isTunnelUp() || networkManager.isVpnConnected())) {
            LOG.info("Network disconnected but VPN is still active. Not stopping services.");
            return;
        }

        Engine.instance().stopServices(true);
    }

    private void handleNetworkStatusChange() {
        NetworkManager.queryNetworkStatusBackground(NetworkManager.instance());
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
            NetworkManager networkManager = NetworkManager.instance();
            ConfigurationManager CM = ConfigurationManager.instance();
            boolean vpnGuardEnabled = CM.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY);
            boolean hasVpn = networkManager.isTunnelUp() || networkManager.isVpnConnected();

            if (!vpnGuardEnabled || hasVpn) {
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


    private static void reopenNetworkSockets() {
        // sleep for a second, since IPv6 addresses takes time to be reported
        SystemClock.sleep(1000);
        BTEngine.getInstance().reopenNetworkSockets();
    }
}
