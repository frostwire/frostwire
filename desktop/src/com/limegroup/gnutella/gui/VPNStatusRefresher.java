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

    private final long REFRESH_INTERVAL_IN_MILLIS = 20000;
    private long lastRefresh = 0;

    private Set<VPNStatusListener> clients = new HashSet<>();

    private static final ThreadPool pool = new ThreadPool("VPNStatusRefresher", 1, 1, Integer.MAX_VALUE, new LinkedBlockingQueue<Runnable>(), true);

    private VPNStatusRefresher() {
    }

    private static VPNStatusRefresher instance;

    public static VPNStatusRefresher getInstance() {
        if (instance == null) {
            instance = new VPNStatusRefresher();
        }
        return instance;
    }

    public void register(VPNStatusListener registrant){
        clients.add(registrant);
    }

    public void refresh() {
        long now = System.currentTimeMillis();
        if (lastRefresh == 0 || (now - lastRefresh >= REFRESH_INTERVAL_IN_MILLIS)) {
            lastRefresh = now;
            Thread vpnStatusCheckerThread = new Thread("VPNStatus-checker") {
                @Override
                public void run() {
                    //possibly blocking code
                    final boolean isVPNActive = VPNs.isVPNActive();
                    GUIMediator.safeInvokeLater(new Runnable() {
                        @Override
                        public void run() {
                            for (VPNStatusListener client : clients) {
                                try {
                                    client.onStatusUpdated(isVPNActive);
                                } catch (Exception e) {//client messed up in some way, but we go on.
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                }
            };
            pool.execute(vpnStatusCheckerThread);
        }
    }

    interface VPNStatusListener {
        void onStatusUpdated(boolean vpnIsOn);
    }

}
