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

package com.frostwire.android.gui.services;

import static com.frostwire.android.core.Constants.JOB_ID_ENGINE_SERVICE;
import static com.frostwire.android.util.Asyncs.async;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.JobIntentService;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.EngineService.EngineServiceBinder;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Engine implements IEngineService {

    private static final Logger LOG = Logger.getLogger(Engine.class);

    private static final ExecutorService MAIN_THREAD_POOL = new EngineThreadPool();

    private EngineService service;
    private ServiceConnection connection;
    private EngineBroadcastReceiver receiver;

    private static final Object pythonStarterLock = new Object();
    private static final CountDownLatch pythonStarterLatch = new CountDownLatch(1);
    private static Python pythonInstance;

    // the startServices call is a special call that can be made
    // to early (relatively speaking) during the application startup
    // the creation of the service is not (and can't be) synchronized
    // with the main activity resume.
    private boolean pendingStartServices = false;
    private boolean wasShutdown;

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
     * Don't call this method directly, it's called by {@link MainApplication#onCreate()}.
     * See {@link Application#onCreate()} documentation for general restrictions on the
     * type of operations that are suitable to run here.
     *
     * @param application the application object
     */
    public void onApplicationCreate(Application application) {
        async(new EngineApplicationRefsHolder(this, application), Engine::engineServiceStarter);
    }

    @Override
    public CoreMediaPlayer getMediaPlayer() {
        return service != null ? service.getMediaPlayer() : null;
    }

    public byte getState() {
        return service != null ? service.getState() : IEngineService.STATE_INVALID;
    }

    public boolean isStarted() {
        return service != null && service.isStarted();
    }

    public boolean isStarting() {
        return service != null && service.isStarting();
    }

    public boolean isStopped() {
        return service != null && service.isStopped();
    }

    public boolean isStopping() {
        return service != null && service.isStopping();
    }

    public boolean isDisconnected() {
        return service != null && service.isDisconnected();
    }

    public void startServices() {
        if (service != null || wasShutdown) {
            if (service != null) {
                service.startServices(wasShutdown);
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Engine::startPython);
            }
            if (wasShutdown) {
                async(new EngineApplicationRefsHolder(this, getApplication()),
                        Engine::engineServiceStarter);
            }
            wasShutdown = false;
        } else {
            // save pending startServices call
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
            e.printStackTrace();
        }
        return pythonInstance;
    }

    public void stopServices(boolean disconnected) {
        if (service != null) {
            service.stopServices(disconnected);
        }
        TellurideCourier.abortCurrentQuery();
    }

    /**
     * Tip: Try using SystemUtils.HandlerFactory.postTo(one of few predetermined threads, run) if possible
     */
    public ExecutorService getThreadPool() {
        return MAIN_THREAD_POOL;
    }


    @RequiresApi(api = Build.VERSION_CODES.S)
    public void notifyDownloadFinished(String displayName, File file, String optionalInfoHash) {
        if (service != null) {
            service.notifyDownloadFinished(displayName, file, optionalInfoHash);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void notifyDownloadFinished(String displayName, File file) {
        notifyDownloadFinished(displayName, file, null);
    }

    @Override
    public void shutdown() {
        if (service != null) {
            if (connection != null) {
                try {
                    getApplication().unbindService(connection);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }

            if (receiver != null) {
                try {
                    getApplication().unregisterReceiver(receiver);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            service.shutdown();
            wasShutdown = true;
        }
    }

    /**
     * @param context This must be the application context, otherwise there will be a leak.
     */
    private void startEngineService(final Context context) {
        Intent i = new Intent();
        i.setClass(context, EngineService.class);
        try {
            Engine.enqueueServiceJob(context, i);
            context.bindService(i, connection = new ServiceConnection() {
                public void onServiceDisconnected(ComponentName name) {
                }

                public void onServiceConnected(ComponentName name, IBinder service) {
                    // avoids: java.lang.ClassCastException: android.os.BinderProxy cannot be cast to com.frostwire.android.gui.services.EngineService$EngineServiceBinder
                    if (service instanceof EngineServiceBinder) {
                        Engine.this.service = ((EngineServiceBinder) service).getService();
                        registerStatusReceiver(context);
                        if (pendingStartServices) {
                            pendingStartServices = false;
                            Engine.this.service.startServices();
                        }
                    }
                }
            }, 0);
        } catch (SecurityException execution) {
            WeakReference<Context> contextRef = Ref.weak(context);
            SystemUtils.postToUIThread(() -> {
                try {
                    if (Ref.alive(contextRef)) {
                        UIUtils.showLongMessage(context, R.string.frostwire_start_engine_service_security_exception);
                    }
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) {
                        throw t;
                    }
                    LOG.error("Engine::startEngineService() failed posting UIUtils.showLongMessage error to main looper: " + t.getMessage(), t);
                }
            });
            execution.printStackTrace();
        }
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
            context.registerReceiver(receiver, fileFilter);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            context.registerReceiver(receiver, connectivityFilter);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            context.registerReceiver(receiver, audioFilter);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }

        try {
            context.registerReceiver(receiver, telephonyFilter);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    @Override
    public Application getApplication() {
        Application r = null;
        if (service != null) {
            r = service.getApplication();
        }
        return r;
    }

    public static void enqueueServiceJob(final Context context, final Intent intent) {
        JobIntentService.enqueueWork(context, EngineService.class, JOB_ID_ENGINE_SERVICE, intent);
    }

    private static class EngineApplicationRefsHolder {
        WeakReference<Engine> engineRef;
        WeakReference<Application> appRef;

        EngineApplicationRefsHolder(Engine engine, Application application) {
            engineRef = Ref.weak(engine);
            appRef = Ref.weak(application);
        }
    }

    private static void engineServiceStarter(EngineApplicationRefsHolder refsHolder) {
        if (!Ref.alive(refsHolder.engineRef)) {
            return;
        }
        if (!Ref.alive(refsHolder.appRef)) {
            return;
        }
        Engine engine = refsHolder.engineRef.get();
        Application application = refsHolder.appRef.get();
        if (application != null) {
            engine.startEngineService(application);
        }
    }
}