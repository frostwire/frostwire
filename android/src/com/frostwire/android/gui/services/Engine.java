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

package com.frostwire.android.gui.services;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Engine implements IEngineService {

    private static final Logger LOG = Logger.getLogger(Engine.class);

    private static final ExecutorService MAIN_THREAD_POOL = new EngineThreadPool();

    //private EngineIntentService service;

    private EngineBroadcastReceiver receiver;

    private static final Object pythonStarterLock = new Object();
    private static final CountDownLatch pythonStarterLatch = new CountDownLatch(1);
    private static Python pythonInstance;

    // the startServices call is a special call that can be made
    // to early (relatively speaking) during the application startup
    // the creation of the service is not (and can't be) synchronized
    // with the main activity resume.
    private boolean pendingStartServices = false;

    private EngineForegroundService engineForegroundService;

    private boolean wasShutdown;

    private CoreMediaPlayer mediaPlayer;

    private Engine() {
    }

    public boolean wasShutdown() {
        return wasShutdown;
    }

    private static class Loader {
        static final Engine INSTANCE = new Engine();
    }

    public static Engine instance() {
        return Engine.Loader.INSTANCE;
    }

    /**
     * Initialize Engine during application creation.
     *
     * @param application the Application context
     */
    public void onApplicationCreate(Application application) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            LOG.info("Engine::onApplicationCreate(): Starting EngineForegroundService...");
            mediaPlayer = new ApolloMediaPlayer();
            startEngineService(application);
        });
    }

    @Override
    public CoreMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public byte getState() {
        return engineForegroundService != null ? engineForegroundService.getState() : IEngineService.STATE_INVALID;
    }

    public boolean isStarted() {
        return engineForegroundService != null && engineForegroundService.isStarted();
    }

    public boolean isStarting() {
        return engineForegroundService != null && engineForegroundService.isStarting();
    }

    public boolean isStopped() {
        return engineForegroundService != null && engineForegroundService.isStopped();
    }

    public boolean isStopping() {
        return engineForegroundService != null && engineForegroundService.isStopping();
    }

    public boolean isDisconnected() {
        return engineForegroundService != null && engineForegroundService.isDisconnected();
    }

    @Override
    public void startServices() {
        LOG.info("Engine::startServices(): Requesting startServices from EngineForegroundService");
        if (wasShutdown) {
            LOG.info("Extract string resource Restarting EngineForegroundService after shutdown...");
            startEngineService(getApplication());
            wasShutdown = false;
        } else {
            pendingStartServices = true;
        }
    }

    public static void startPython() {
        if (Python.isStarted()) {
            LOG.info("Engine::startPython aborted, already started.");
            return;
        }
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Engine::startPython);
            return;
        }
        try {

            long a = System.currentTimeMillis();
            AndroidPlatform androidPlatform = new AndroidPlatform(SystemUtils.getApplicationContext());
            synchronized (pythonStarterLock) {
                LOG.info("Engine::startPython Python runtime first instantiation in synchronized space...");
                Python.start(androidPlatform);
                pythonInstance = Python.getInstance();
                if (pythonStarterLatch.getCount() > 0) {
                    pythonStarterLatch.countDown();
                }
                if (pythonInstance != null) {
                    LOG.info("Engine::startPython Python runtime first instantiated in synchronized space.");
                } else {
                    LOG.warn("Engine::startPython Python runtime first instantiation in synchronized space FAILED.");
                }
            }
            long b = System.currentTimeMillis();
            LOG.info("Engine::startPython Python runtime first instantiated in " + (b - a) + " ms");
        } catch (Throwable t) {
            LOG.error("Engine::startPython Python runtime first instantiation FAILED.", t);
            // keep trying every 10 seconds until Python is started
            if (!Python.isStarted()) {
                LOG.info("Engine::startPython Python runtime first instantiation FAILED, retrying in 10 seconds...");
                SystemUtils.postToHandlerDelayed(SystemUtils.HandlerThreadName.MISC, Engine::startPython, 10000);
            }
        }
    }

    public static Python getPythonInstance() {
        try {
            pythonStarterLatch.await();
        } catch (InterruptedException e) {
            LOG.error("Engine::getPythonInstance() ", e);
        }
        return pythonInstance;
    }

    public void stopServices(boolean disconnected) {
        LOG.info("Stopping Engine services...");
        TellurideCourier.abortCurrentQuery();
        stopEngineService();
    }

    private void startEngineService(Context context) {
        Intent serviceIntent = new Intent(context, EngineForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check if conditions are appropriate for starting the service
            if (!SystemUtils.isAppInForeground(context)) {
                LOG.warn("Engine::startEngineService() - App is not in foreground, delaying start.");
                return; // Delay or prevent the start if app is not in foreground
            }
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent);
            registerStatusReceiver(context);

        } catch (Throwable t) {
            LOG.error("Engine::startEngineService() - Failed starting foreground service: " + t.getMessage(), t);
        }
    }

    private void stopEngineService() {
        Context context = getApplication();
        if (context != null) {
            Intent serviceIntent = new Intent(context, EngineForegroundService.class);
            context.stopService(serviceIntent);
        }
        wasShutdown = true;
    }

    @Override
    public Application getApplication() {
        return (Application) MainApplication.context();
    }

    /**
     * Tip: Try using SystemUtils.HandlerFactory.postTo(one of few predetermined threads, run) if possible
     */
    public ExecutorService getThreadPool() {
        return MAIN_THREAD_POOL;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void notifyDownloadFinished(String displayName, File file, String optionalInfoHash) {
        if (engineForegroundService != null) {
            engineForegroundService.notifyDownloadFinished(displayName, file, optionalInfoHash);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void notifyDownloadFinished(String displayName, File file) {
        notifyDownloadFinished(displayName, file, null);
    }


    @Override
    public void shutdown() {
        LOG.info("Engine::shutdown() Shutting down EngineForegroundService...");
        stopEngineService();
    }

    private void registerStatusReceiver(Context context) {
        receiver = new EngineBroadcastReceiver();

        IntentFilter fileFilter = new IntentFilter();

        fileFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
        fileFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        fileFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        fileFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        fileFilter.addDataScheme("file");

        IntentFilter connectivityFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        IntentFilter audioFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        IntentFilter telephonyFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        try {
            ContextCompat.registerReceiver(context, receiver, fileFilter, ContextCompat.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            ContextCompat.registerReceiver(context, receiver, connectivityFilter, ContextCompat.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            ContextCompat.registerReceiver(context, receiver, audioFilter, ContextCompat.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            ContextCompat.registerReceiver(context, receiver, telephonyFilter, ContextCompat.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }
}