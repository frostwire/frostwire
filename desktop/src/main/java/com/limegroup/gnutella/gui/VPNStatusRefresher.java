/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Grzesiek Rzaca (grzesiekrzaca)
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

package com.limegroup.gnutella.gui;

import com.frostwire.util.ThreadPool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class VPNStatusRefresher {
    private static final ThreadPool pool = new ThreadPool("VPNStatusRefresher", 1, 1, Integer.MAX_VALUE, new LinkedBlockingQueue<>(), true);
    private static VPNStatusRefresher instance;
    private long lastRefresh = 0;
    private final Set<VPNStatusListener> clients = new HashSet<>();
    private boolean active = true;

    private VPNStatusRefresher() {
    }

    public static VPNStatusRefresher getInstance() {
        if (instance == null) {
            instance = new VPNStatusRefresher();
        }
        return instance;
    }

    public void addRefreshListener(VPNStatusListener listener) {
        clients.add(listener);
    }

    public void refresh() {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        long REFRESH_INTERVAL_IN_MILLIS = 20000;
        if (lastRefresh == 0 || (now - lastRefresh >= REFRESH_INTERVAL_IN_MILLIS)) {
            lastRefresh = now;
            Thread vpnStatusCheckerThread = new Thread("VPNStatus-checker") {
                @Override
                public void run() {
                    if (!active) {
                        return;
                    }
                    //possibly blocking code
                    final boolean isVPNActive = VPNs.isVPNActive();
                    if (!active) {
                        return;
                    }
                    GUIMediator.safeInvokeLater(() -> {
                        if (!active) {
                            return;
                        }
                        for (VPNStatusListener client : clients) {
                            try {
                                client.onStatusUpdated(isVPNActive);
                            } catch (Exception e) { //client messed up in some way, but we go on.
                                e.printStackTrace();
                            }
                        }
                    });
                }
            };
            vpnStatusCheckerThread.setDaemon(true);
            //Don't try refactoring into this, won't work. Weird duplicate setting error happens.
            //GUIMediator.instance().uiThreadPool().execute(vpnStatusCheckerThread);
            pool.execute(vpnStatusCheckerThread);
        }
    }

    public void shutdown() {
        active = false;
    }

    interface VPNStatusListener {
        void onStatusUpdated(boolean vpnIsOn);
    }
}
