/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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
import android.content.Context;
import android.view.ViewConfiguration;
import com.andrew.apollo.cache.ImageCache;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.HttpResponseCache;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public class MainApplication extends Application {

    private static final Logger LOG = Logger.getLogger(MainApplication.class);

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            //android.os.Debug.waitForDebugger();
            PlayStore.getInstance().initialize(this); // as early as possible

            ignoreHardwareMenu();
            AbstractActivity.setMenuIconsVisible(true);
            installHttpCache();

            ConfigurationManager.create(this);

            Platforms.set(new AndroidPlatform(this));

            setupBTEngine();

            NetworkManager.create(this);
            Librarian.create(this);
            Engine.instance().onApplicationCreate(this);

            ImageLoader.getInstance(this);
            CrawlPagedWebSearchPerformer.setCache(new DiskCrawlCache(this));
            CrawlPagedWebSearchPerformer.setMagnetDownloader(null); // this effectively turn off magnet downloads

            LocalSearchEngine.create();

            cleanTemp();

            Librarian.instance().syncMediaStore();
        } catch (Throwable e) {
            throw new RuntimeException("Unable to initialized main components", e);
        }
    }

    @Override
    public void onLowMemory() {
        ImageCache.getInstance(this).evictAll();
        ImageLoader.getInstance(this).clear();
        super.onLowMemory();
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

    private void installHttpCache() {
        ExecutorService threadPool = Engine.instance().getThreadPool();
        if (threadPool != null) {
            threadPool.submit(new InstallHttpCacheRunnable(this));
        }
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

        BTEngine.ctx = ctx;
        BTEngine.getInstance().start();

        boolean enable_dht = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_ENABLE_DHT);
        if (!enable_dht) {
            BTEngine.getInstance().stopDht();
        } else {
            // just make sure it's started otherwise.
            // (we could be coming back from a crash on an unstable state)
            //dht.start();
        }
    }

    private void cleanTemp() {
        try {
            File tmp = Platforms.get().systemPaths().temp();
            if (tmp.exists()) {
                FileUtils.cleanDirectory(tmp);
            }
        } catch (Throwable e) {
            LOG.error("Error during setup of temp directory", e);
        }
    }

    private static class InstallHttpCacheRunnable implements Runnable {
        private WeakReference<Context> ctxRef;

        InstallHttpCacheRunnable(Context context) {
            ctxRef = Ref.weak(context);
        }

        @Override
        public void run() {
            if (!Ref.alive(ctxRef)) {
                return;
            }
            try {
                HttpResponseCache.install(ctxRef.get());
            } catch (IOException e) {
                LOG.error("Unable to install global http cache", e);
            }
        }
    }
}
