/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Grzesiek Rzaca (grzesiekrzaca)
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
