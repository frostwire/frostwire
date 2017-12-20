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

package com.frostwire.android.gui;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;

import java.io.File;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NetworkManager {

    private final ExecutorService pool;

    private final Context appContext;
    private boolean tunnelUp;

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
        this.pool = Engine.instance().getThreadPool();
        this.appContext = context.getApplicationContext();
    }

    /**
     * aka -> isInternetUp
     */
    public boolean isDataUp(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

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

    public ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) appContext.getSystemService(Application.CONNECTIVITY_SERVICE);
    }

    public boolean isTunnelUp() {
        return tunnelUp;
    }

    private void detectTunnel() {
        // see https://issuetracker.google.com/issues/37091475
        // for more information on possible restrictions in the
        // future
        tunnelUp = interfaceNameExists("tun0") || interfaceNameExists("tun1");
    }

    private static boolean interfaceNameExists(String name) {
        try {
            File f = new File("/sys/class/net/" + name);
            return f.exists();
        } catch (Throwable e) {
            // ignore
        }
        return false;
    }

    public void queryNetworkStatus() {
        pool.execute(new QueryNetworkStatusTask());
    }

    private static final class QueryNetworkStatusTask implements Runnable {

        @Override
        public void run() {
            NetworkManager manager = NetworkManager.instance();
            Context context = manager.appContext;
            boolean isDataUp = manager.isDataUp(context);
            manager.detectTunnel();

            Intent intent = new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
            intent.putExtra("isDataUp", isDataUp);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }
}
