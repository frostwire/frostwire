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

package com.frostwire.android.gui;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Modern network monitoring using ConnectivityManager.NetworkCallback (API 21+).
 * Replaces deprecated CONNECTIVITY_CHANGE broadcasts and TYPE_WIFI/TYPE_MOBILE constants.
 *
 * @author gubatron
 * @author aldenml
 */
public final class NetworkManager {

    private static final Logger LOG = Logger.getLogger(NetworkManager.class);

    private final Context appContext;
    private boolean tunnelUp;
    private volatile boolean isWifiConnected;
    private volatile boolean isMobileConnected;
    private volatile boolean isVpnConnected;
    private volatile boolean wasWifiConnected;
    private volatile boolean wasMobileConnected;

    private WeakReference<ConnectivityManager> connManRef;
    private ConnectivityManager.NetworkCallback networkCallback;

    // this is one of the few justified occasions in which
    // holding a context in a static field has no problems,
    // this is a reference to the application context and
    // greatly improve the API design
    @SuppressLint("StaticFieldLeak")
    private static NetworkManager instance;

    public synchronized static void create(Context context) {
        if (instance != null) {
            return;
        }
        instance = new NetworkManager(context);
    }

    public static NetworkManager instance() {
        if (instance == null) {
            throw new RuntimeException("NetworkManager not created");
        }
        return instance;
    }

    private NetworkManager(Context context) {
        this.appContext = context.getApplicationContext();
        registerNetworkCallback();
    }

    /**
     * Registers a NetworkCallback to monitor network changes using modern Android APIs.
     * This replaces the deprecated CONNECTIVITY_CHANGE broadcast approach.
     */
    private void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = getConnectivityManager();
            if (connectivityManager == null) {
                LOG.error("ConnectivityManager is null, cannot register network callback");
                return;
            }

            // Initial network state check
            updateNetworkState();

            // Register callback for future network changes
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    LOG.info("Network available: " + network);
                    updateNetworkState();
                    notifyNetworkChange();
                }

                @Override
                public void onLost(Network network) {
                    LOG.info("Network lost: " + network);
                    updateNetworkState();
                    notifyNetworkChange();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    LOG.info("Network capabilities changed: " + network);
                    updateNetworkState();
                    notifyNetworkChange();
                }
            };

            connectivityManager.registerNetworkCallback(request, networkCallback);
            LOG.info("NetworkCallback registered successfully");
        } catch (Throwable t) {
            LOG.error("Error registering NetworkCallback", t);
        }
    }

    /**
     * Unregisters the network callback. Should be called when the manager is no longer needed.
     */
    public void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                ConnectivityManager connectivityManager = getConnectivityManager();
                if (connectivityManager != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                    LOG.info("NetworkCallback unregistered successfully");
                }
            } catch (Throwable t) {
                LOG.error("Error unregistering NetworkCallback", t);
            } finally {
                networkCallback = null;
            }
        }
    }

    /**
     * Updates the current network state by checking active network capabilities.
     * Uses modern NetworkCapabilities API instead of deprecated TYPE_WIFI/TYPE_MOBILE.
     */
    private void updateNetworkState() {
        ConnectivityManager connectivityManager = getConnectivityManager();
        if (connectivityManager == null) {
            isWifiConnected = false;
            isMobileConnected = false;
            isVpnConnected = false;
            return;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            isWifiConnected = false;
            isMobileConnected = false;
            isVpnConnected = false;
            return;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            isWifiConnected = false;
            isMobileConnected = false;
            isVpnConnected = false;
            return;
        }

        // Save previous state to detect transitions
        wasWifiConnected = isWifiConnected;
        wasMobileConnected = isMobileConnected;

        isWifiConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        isMobileConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        isVpnConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    /**
     * Sends a local broadcast to notify the app of network state changes.
     */
    private void notifyNetworkChange() {
        boolean isDataUp = isInternetDataConnectionUp();
        detectTunnel();
        Intent intent = new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
        intent.putExtra("isDataUp", isDataUp);
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
    }

    /**
     * Refresh network and VPN state, triggering notification if changed.
     * This should be called periodically to detect VPN disconnections that
     * might not trigger NetworkCallback.
     */
    public void refreshNetworkState() {
        boolean wasVpnConnected = isVpnConnected;
        boolean wasTunnelUp = tunnelUp;

        updateNetworkState();
        detectTunnel();

        // If VPN status changed, notify even if network itself didn't change
        if (wasVpnConnected != isVpnConnected || wasTunnelUp != tunnelUp) {
            LOG.info("VPN state changed, notifying network listeners");
            notifyNetworkChange();
        }
    }

    public boolean isInternetDataConnectionUp() {
        // boolean logic trick, since sometimes android reports WIFI and MOBILE up at the same time
        return isWifiConnected != isMobileConnected;
    }

    public boolean isDataMobileUp() {
        return isMobileConnected;
    }

    public boolean isDataWIFIUp() {
        return isWifiConnected;
    }

    public boolean isVpnConnected() {
        return isVpnConnected;
    }

    public boolean hasNetworkStateTransitioned() {
        return wasWifiConnected != isWifiConnected || wasMobileConnected != isMobileConnected;
    }

    private ConnectivityManager getConnectivityManager() {
        if (!Ref.alive(connManRef)) {
            connManRef = Ref.weak((ConnectivityManager) appContext.getSystemService(Application.CONNECTIVITY_SERVICE));
        }
        return connManRef.get();
    }

    public boolean isTunnelUp() {
        return tunnelUp;
    }

    private void detectTunnel() {
        List<String> names = getInterfaceNames();
        tunnelUp = interfaceNameExists(names, "tun0") ||
                interfaceNameExists(names, "tun1") ||
                interfaceNameExists(names, "tap0") ||
                interfaceNameExists(names, "tap1");
    }

    private static boolean interfaceNameExists(List<String> names, String name) {
        for (String s : names) {
            if (s.contains(name)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> getInterfaceNames() {
        List<String> names = new LinkedList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces != null) {
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    String name = networkInterface.getName();
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
        } catch (Throwable e) {
            // ignore
            // important, but no need to crash the app
        }

        return names;
    }

    public static void queryNetworkStatusBackground(NetworkManager manager) {
        boolean isDataUp = manager.isInternetDataConnectionUp();
        manager.detectTunnel();
        Intent intent = new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
        intent.putExtra("isDataUp", isDataUp);
        LocalBroadcastManager.getInstance(manager.appContext).sendBroadcast(intent);
    }
}
