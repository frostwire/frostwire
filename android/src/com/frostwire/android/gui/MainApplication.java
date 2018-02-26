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

import android.app.Application;
import android.content.Context;
import android.view.ViewConfiguration;

import com.andrew.apollo.cache.ImageCache;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static com.frostwire.android.util.Debug.runStrict;

/**
 * @author gubatron
 * @author aldenml
 */
public class MainApplication extends Application {

    private static final Logger LOG = Logger.getLogger(MainApplication.class);

    @Override
    public void onCreate() {
        super.onCreate();

        runStrict(this::onCreateSafe);

        ConfigurationManager.create(this);

        Platforms.set(new AndroidPlatform(this));

        ExecutorService threadPool = Engine.instance().getThreadPool();

        threadPool.execute(new BTEngineInitializer(Ref.weak(this)));

        Librarian.create();
        Engine.instance().onApplicationCreate(this);

        ImageLoader.start(this, threadPool);

        threadPool.execute(new CrawlPagedWebSearchPerformerInitializer(this));

        LocalSearchEngine.create();

        threadPool.execute(new TempCleaner());
    }

    @Override
    public void onLowMemory() {
        ImageCache.getInstance(this).evictAll();
        ImageLoader.getInstance(this).clear();
        super.onLowMemory();
    }

    private void onCreateSafe() {
        // some phones still can configure an external button as the
        // permanent menu key
        ignoreHardwareMenu();

        AbstractActivity.setMenuIconsVisible(true);

        PlayStore.getInstance().initialize(this);

        NetworkManager.create(this);
        NetworkManager.instance().queryNetworkStatus();
    }

    private void ignoreHardwareMenu() {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field f = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (f != null) {
                f.setAccessible(true);
                f.setBoolean(config, false);
            }
        } catch (Throwable e) {
            // ignore
        }
    }

    private static class CrawlPagedWebSearchPerformerInitializer implements Runnable {
        private WeakReference<Context> ctxRef;

        CrawlPagedWebSearchPerformerInitializer(Context context) {
            ctxRef = Ref.weak(context);
        }

        @Override
        public void run() {
            if (Ref.alive(ctxRef)) {
                CrawlPagedWebSearchPerformer.setCache(new DiskCrawlCache(ctxRef.get()));
            } else {
                LOG.warn("CrawlPagedWebSearchPerformer cache not set, lost reference to MainApplication");
            }
            CrawlPagedWebSearchPerformer.setMagnetDownloader(new LibTorrentMagnetDownloader());
        }
    }

    private static class BTEngineInitializer implements Runnable {
        private final WeakReference<Context> mainAppRef;

        BTEngineInitializer(WeakReference<Context> mainAppRef) {
            this.mainAppRef = mainAppRef;
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

            BTEngine.ctx = ctx;
            BTEngine.onCtxSetupComplete();
            BTEngine.getInstance().start();

            syncMediaStore();
        }

        private void syncMediaStore() {
            if (Ref.alive(mainAppRef)) {
                Librarian.instance().syncMediaStore(mainAppRef);
            } else {
                LOG.warn("syncMediaStore() failed, lost MainApplication reference");
            }
        }
    }

    private static class TempCleaner implements Runnable {
        public void run() {
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
}
