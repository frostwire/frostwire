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

package com.frostwire.light;

import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.util.ThreadPool;
import io.vertx.core.Vertx;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static com.frostwire.light.Main.ExecutorID.GENERAL;


public final class Main {
    enum ExecutorID {
        GENERAL,
        CLOUD_SEARCH,
        P2P_SEARCH
    }

    private Map<ExecutorID, ExecutorService> EXECUTORS;

    private final Vertx VERTX;

    private Main() {
        EXECUTORS = new HashMap<>();
        initExecutorServices();
        ConfigurationManager.instance();
        // Load I18n according to current language selection
        // TODO: current language setting, language String loader/manager class or perhaps
        // this will all be handled on the UI side
        setupBTEngine();
        VERTX = Vertx.vertx();
    }

    private void initExecutorServices() {
        EXECUTORS.put(GENERAL, ThreadPool.newThreadPool(GENERAL.name(), 8, false));
    }

    private void setupBTEngine() {
        SystemPaths paths = Platforms.get().systemPaths();

        BTContext ctx = new BTContext();
        ctx.homeDir = paths.libtorrent();
        ctx.torrentsDir = paths.torrents();
        ctx.dataDir = paths.data();
        ctx.optimizeMemory = true;

        // port range [37000, 57000]
        int port0 = 37000 + new Random().nextInt(20000);
        int port1 = port0 + 10; // 10 retries
        String iface = "0.0.0.0:%1$d,[::]:%1$d";
        ctx.interfaces = String.format(Locale.US, iface, port0);
        ctx.retries = port1 - port0;

        ctx.enableDht = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_ENABLE_DHT);

        BTEngine.ctx = ctx;
        BTEngine.getInstance().start();
    }

    public static void main(String[] args) {
    }
}
