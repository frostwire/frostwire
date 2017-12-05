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
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NetworkManager {
    private final WeakReference<Application> contextRef;
    private boolean tunnelUp;

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
        this.contextRef = Ref.weak(context);
        // detect tunnel as early as possible, but only as
        // detectTunnel remains a cheap call
        detectTunnel();
    }

    /** aka -> isInternetUp */
    public boolean isDataUp(ConnectivityManager connectivityManager) {
        // boolean logic trick, since sometimes android reports WIFI and MOBILE up at the same time
        return (isDataWIFIUp(connectivityManager) != isDataMobileUp(connectivityManager));
    }

    private boolean isNetworkTypeUp(ConnectivityManager connectivityManager, final int NETWORK_TYPE) {
        NetworkInfo networkInfo = null;
        ConnectivityManager connManager1;
        if (connectivityManager != null) {
            networkInfo = connectivityManager.getNetworkInfo(NETWORK_TYPE);
        } else if ((connManager1 = getConnectivityManager()) != null) {
            networkInfo = connManager1.getNetworkInfo(NETWORK_TYPE);
        }
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    public boolean isDataMobileUp(ConnectivityManager connectivityManager) {
        return isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_MOBILE);
    }

    public boolean isDataWIFIUp(ConnectivityManager connectivityManager) {
        return isNetworkTypeUp(connectivityManager, ConnectivityManager.TYPE_WIFI);
    }

    public ConnectivityManager getConnectivityManager() {
        if (!Ref.alive(contextRef)) {
            return null;
        }
        return (ConnectivityManager) contextRef.get().getSystemService(Application.CONNECTIVITY_SERVICE);
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
        if (interfaceName == null) {
            return false;
        }
        try {
            String[] arr = new File("/sys/class/net").list();
            if (arr == null) {
                return false;
            }
            for (String validName : arr) {
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

    public void notifyNetworkStatusListeners() {
        Engine.instance().getThreadPool().execute(new NotifyNetworkStatusTask());
    }

    private static final class NotifyNetworkStatusTask implements Runnable {

        @Override
        public void run() {
            NetworkManager manager = NetworkManager.instance();
            if (!Ref.alive(manager.contextRef)) {
                return;
            }
            Application context = manager.contextRef.get();
            ConnectivityManager connectivityManager = manager.getConnectivityManager();
            boolean isDataUp = manager.isDataUp(connectivityManager);

            Intent intent = new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
            intent.putExtra("isDataUp", isDataUp);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }
}
