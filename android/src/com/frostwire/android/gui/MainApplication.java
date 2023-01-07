/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui;

import static com.frostwire.android.util.RunStrict.runStrict;

import android.content.Context;

import androidx.multidex.MultiDexApplication;

import com.andrew.apollo.cache.ImageCache;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 */
public class MainApplication extends MultiDexApplication {

    private static final Logger LOG = Logger.getLogger(MainApplication.class);

    private static final Object appContextLock = new Object();

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (appContextLock) {
            if (appContext == null) {
                appContext = this;
            }
        }

        runStrict(this::onCreateSafe);

        Platforms.set(new AndroidPlatform(this));

        Engine.instance().onApplicationCreate(this);

        new Thread(new BTEngineInitializer()).start();

        ImageLoader.start(this);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, () -> this.initializeCrawlPagedWebSearchPerformer(this));

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, LocalSearchEngine::instance);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, MainApplication::cleanTemp);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            TellurideCourier.ytDlpVersion((version) -> LOG.info("MainApplication::onCreate -> yt_dlp version: " + version));
        });
    }

    public static Context context() {
        return appContext;
    }

    @Override
    public void onLowMemory() {
        ImageCache.getInstance(this).evictAll();
        ImageLoader.getInstance(this).clear();
        super.onLowMemory();
    }

    private void onCreateSafe() {
        ConfigurationManager.create(this);

        AbstractActivity.setMenuIconsVisible(true);

        PlayStore.getInstance(this); // triggers initial query

        NetworkManager.create(this);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
                () -> NetworkManager.queryNetworkStatusBackground(NetworkManager.instance()));
    }

    private void initializeCrawlPagedWebSearchPerformer(Context context) {
        CrawlPagedWebSearchPerformer.setCache(new DiskCrawlCache(context));
        CrawlPagedWebSearchPerformer.setMagnetDownloader(new LibTorrentMagnetDownloader());
    }

    // don't try to refactor this into an async call since this guy runs on a thread
    // outside the Engine thread pool
    private static class BTEngineInitializer implements Runnable {
        BTEngineInitializer() {
        }

        public void run() {
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
            String[] vStrArray = Constants.FROSTWIRE_VERSION_STRING.split("\\.");
            ctx.version[0] = Integer.parseInt(vStrArray[0]);
            ctx.version[1] = Integer.parseInt(vStrArray[1]);
            ctx.version[2] = Integer.parseInt(vStrArray[2]);
            ctx.version[3] = BuildConfig.VERSION_CODE;

            BTEngine.ctx = ctx;
            BTEngine.onCtxSetupComplete();
            BTEngine.getInstance().start();
        }
    }

    private static void cleanTemp() {
        try {
            File tmp = Platforms.get().systemPaths().temp();
            if (tmp.exists()) {
                FileUtils.cleanDirectory(tmp);
            }
        } catch (Throwable e) {
            LOG.error("Error during setup of temp directory", e);
        }
    }
}
