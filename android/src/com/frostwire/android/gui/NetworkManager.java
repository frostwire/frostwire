/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NetworkManager {
    private static final Logger LOG = Logger.getLogger(NetworkManager.class);
    private final Application context;
    private boolean tunnelUp;
    private final ConnectivityActionReceiver connectivityActionBroadcastReceiver;
    private NetworkStatusListener networkStatusListener;

    private static NetworkManager instance;

    public synchronized static void create(Application context) {
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

    private NetworkManager(Application context) {
        this.context = context;
        this.connectivityActionBroadcastReceiver = new ConnectivityActionReceiver(this);
        this.networkStatusListener = null;
        // detect tunnel as early as possible, but only as
        // detectTunnel remains a cheap call
        registerBroadcastReceiver();
        detectTunnel();
    }

    private void registerBroadcastReceiver() {
        this.context.registerReceiver(connectivityActionBroadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    public boolean isInternetDown() {
        return !isDataWIFIUp() && !isDataMobileUp() && !isDataWiMAXUp();
    }

    public boolean isDataUp() {
        return isDataUp(null);
    }

    public boolean isDataUp(ConnectivityManager connectivityManager) {
        // boolean logic trick, since sometimes android reports WIFI and MOBILE up at the same time
        return (isDataWIFIUp(connectivityManager) != isDataMobileUp(connectivityManager)) || isDataWiMAXUp(connectivityManager);
    }

    public boolean isDataMobileUp() {
        return isDataMobileUp(null);
    }

    private boolean isDataMobileUp(ConnectivityManager connectivityManager) {
        if (connectivityManager == null) {
            connectivityManager = getConnectivityManager();
        }
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    public boolean isDataWIFIUp() {
        return isDataWIFIUp(null);
    }

    private boolean isDataWIFIUp(ConnectivityManager connectivityManager) {
        if (connectivityManager == null) {
            connectivityManager = getConnectivityManager();
        }
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    public boolean isDataWiMAXUp() {
        return isDataWiMAXUp(null);
    }

    private boolean isDataWiMAXUp(ConnectivityManager connectivityManager) {
        if (connectivityManager == null) {
            connectivityManager = getConnectivityManager();
        }
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) context.getSystemService(Application.CONNECTIVITY_SERVICE);
    }

    public boolean isTunnelUp() {
        return tunnelUp;
    }

    // eventually move this to the Platform framework
    public void detectTunnel() {
        boolean up = isValidInterfaceName("tun0");
        // if android 7, the above query will fail
        // see https://issuetracker.google.com/issues/37091475
        if (!up) {
            up = interfaceNameExists("tun0") || interfaceNameExists("tun1");
        }
        tunnelUp = up;
    }

    private static boolean isValidInterfaceName(String interfaceName) {
        try {
            String[] arr = new File("/sys/class/net").list();
            if (arr == null) {
                return false;
            }
            for (int i = 0; i < arr.length; i++) {
                String validName = arr[i];
                if (interfaceName.equals(validName)) {
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean interfaceNameExists(String name) {
        try {
            File f = new File("/sys/class/net/" + name);
            return f.exists();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public void shutdown() {
        networkStatusListener = null;
        context.unregisterReceiver(this.connectivityActionBroadcastReceiver);
    }

    public void setNetworkStatusListener(NetworkStatusListener networkStatusListener) {
        this.networkStatusListener = networkStatusListener;
    }

    public void notifyNetworkStatusListeners() {
        LOG.info("notifyNetworkStatusListeners() on thread " + Thread.currentThread().getName() + ":" + Thread.currentThread().getId());
        Engine.instance().getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager connectivityManager = getConnectivityManager();
                boolean isDataUp = isDataUp(connectivityManager);
                boolean isDataWIFIUp = isDataWIFIUp(connectivityManager);
                boolean isDataMobileUp = isDataMobileUp(connectivityManager);
                if (networkStatusListener != null) {
                    try {
                        networkStatusListener.onNetworkStatusChange(isDataUp, isDataWIFIUp, isDataMobileUp);
                    } catch (Throwable t) {
                        LOG.error(t.getMessage(), t);
                    }
                }

            }
        });
    }

    public interface NetworkStatusListener {
        /** Called from background thread, listener must do UI changes on UI thread */
        void onNetworkStatusChange(boolean isDataUp, boolean isDataWiFiUp, boolean isDataMobileUp);
    }

    private final static class ConnectivityActionReceiver extends BroadcastReceiver {

        NetworkManager networkManager;

        ConnectivityActionReceiver(NetworkManager networkManager) {
            this.networkManager = networkManager;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                NetworkInfo.DetailedState detailedState = networkInfo.getDetailedState();
                if (detailedState == NetworkInfo.DetailedState.DISCONNECTED ||
                        detailedState == NetworkInfo.DetailedState.CONNECTED ||
                        detailedState == NetworkInfo.DetailedState.DISCONNECTING ||
                        detailedState == NetworkInfo.DetailedState.CONNECTING) {
                    networkManager.notifyNetworkStatusListeners();
                }
            }
        }
    }
}