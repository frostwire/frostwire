/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui;

import static com.frostwire.android.util.RunStrict.runStrict;

import android.content.Context;
import android.os.Build;

import androidx.multidex.MultiDexApplication;

import com.andrew.apollo.cache.ImageCache;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.RunStrict;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.util.Logger;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Locale;
import java.util.Random;

import dalvik.system.ZipPathValidator;

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
        // schedule our forever‐running status notification update exactly once
        runStrict(this::onCreateStrict);
        new NotificationUpdateDaemon(this).start();

        RunStrict.enableStrictModePolicies(BuildConfig.DEBUG);
        //RunStrict.disableStrictModePolicyForUnbufferedIO();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ZipPathValidator.clearCallback();
        }

        Platforms.set(new AndroidPlatform(this));

        LOG.info("MainApplication::onCreate waiting for appContextLock");
        synchronized (appContextLock) {
            if (appContext == null) {
                appContext = this;
            }
        }
        LOG.info("MainApplication::onCreate DONE waiting for appContextLock");
        //asyncFirebaseInitialization(appContext);

        RunStrict.enableStrictModePolicies(BuildConfig.DEBUG);
        //RunStrict.disableStrictModePolicyForUnbufferedIO();

        // Start the engine
        Engine.instance().onApplicationCreate(this);

        new Thread(new BTEngineInitializer()).start();

        ThemeManager.loadSavedThemeModeAsync(ThemeManager::applyThemeMode);

        ImageLoader.start(this);

        //fetchGoogleAdvertisingIdAsync();

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, () -> this.initializeCrawlPagedWebSearchPerformer(this));

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, SearchMediator::instance);

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


    private void onCreateStrict() {
        ConfigurationManager.create(this);

        AbstractActivity.setMenuIconsVisible(true);

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
