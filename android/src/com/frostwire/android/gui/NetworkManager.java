/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.frostwire.android.core.Constants;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NetworkManager {

    private final Context appContext;
    private boolean tunnelUp;

    private WeakReference<ConnectivityManager> connManRef;

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
    }

    /**
     * aka -> isInternetUp
     */
    public boolean isDataUp() {
        ConnectivityManager connectivityManager = getConnectivityManager();

        boolean wifiUp = isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_WIFI);
        boolean mobileUp = isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_MOBILE);

        // boolean logic trick, since sometimes android reports WIFI and MOBILE up at the same time
        return wifiUp != mobileUp;
    }

    private boolean isNetworkTypeUp(ConnectivityManager connectivityManager, final int networkType) {
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(networkType);
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    public boolean isDataMobileUp() {
        ConnectivityManager connectivityManager = getConnectivityManager();
        return isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_MOBILE);
    }

    public boolean isDataWIFIUp() {
        ConnectivityManager connectivityManager = getConnectivityManager();
        return isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_WIFI);
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
        boolean isDataUp = manager.isDataUp();
        manager.detectTunnel();
        Intent intent = new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
        intent.putExtra("isDataUp", isDataUp);
        LocalBroadcastManager.getInstance(manager.appContext).sendBroadcast(intent);
    }
}
