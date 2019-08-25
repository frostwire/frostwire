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

package com.frostwire.android.gui.services;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.telephony.TelephonyManager;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.EngineService.EngineServiceBinder;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Engine implements IEngineService {

    private static final ExecutorService MAIN_THREAD_POOL = new EngineThreadPool();

    private EngineService service;
    private ServiceConnection connection;
    private EngineBroadcastReceiver receiver;

    private FWVibrator vibrator;

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

    public void stopServices(boolean disconnected) {
        if (service != null) {
            service.stopServices(disconnected);
        }
    }

    public ExecutorService getThreadPool() {
        return MAIN_THREAD_POOL;
    }

    public void notifyDownloadFinished(String displayName, File file, String optionalInfoHash) {
        if (service != null) {
            service.notifyDownloadFinished(displayName, file, optionalInfoHash);
        }
    }

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
            Engine.startForegroundService(context, i);
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
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(()->UIUtils.showLongMessage(context, R.string.frostwire_start_engine_service_security_exception));
            execution.printStackTrace();
        }
    }

    private void registerStatusReceiver(Context context) {
        receiver = new EngineBroadcastReceiver();

        IntentFilter fileFilter = new IntentFilter();

        fileFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
        fileFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        fileFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        fileFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        fileFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        fileFilter.addDataScheme("file");

        IntentFilter connectivityFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        IntentFilter audioFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        IntentFilter telephonyFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        context.registerReceiver(receiver, fileFilter);
        context.registerReceiver(receiver, connectivityFilter);
        context.registerReceiver(receiver, audioFilter);
        context.registerReceiver(receiver, telephonyFilter);
    }

    @Override
    public Application getApplication() {
        Application r = null;
        if (service != null) {
            r = service.getApplication();
        }
        return r;
    }

    public void onHapticFeedbackPreferenceChanged() {
        if (vibrator != null) {
            vibrator.onPreferenceChanged();
        }
    }

    public void hapticFeedback() {
        if (vibrator != null) {
            vibrator.hapticFeedback();
        }
    }

    public static void startForegroundService(final Context context, final Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Whenever there's a call to ContextCompat.startForegroundService(context, intent)
     * the service that's supposed to be started in the foreground is expected to perform
     * a service.startForeground() call along with a notification within the next 5 seconds,
     * otherwise you get an IllegalState exception crash for not following the 'contract'
     * @param service
     */
    public static void foregroundServiceStartForAndroidO(Service service) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationService = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(
                    Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID,
                    "FrostWire",
                    NotificationManager.IMPORTANCE_NONE);

            if (notificationService != null) {
                notificationService.createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(
                    service,
                    Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID).
                    setContentTitle("").
                    setContentText("").
                    build();
            service.startForeground(1337, notification);
        }
    }

    private static class FWVibrator {
        private final Vibrator vibrator;
        private boolean enabled;

        public FWVibrator(Application context) {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            enabled = isActive();
        }

        public void hapticFeedback() {
            if (!enabled) return;
            try {
                vibrator.vibrate(50);
            } catch (Throwable ignored) {
            }
        }

        public void onPreferenceChanged() {
            enabled = isActive();
        }

        public boolean isActive() {
            boolean hapticFeedback = false;
            ConfigurationManager cm = ConfigurationManager.instance();
            if (cm != null) {
                hapticFeedback = cm.getBoolean(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON);
            }
            return vibrator != null && hapticFeedback;
        }
    }

    private class EngineApplicationRefsHolder {
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
            engine.vibrator = new FWVibrator(application);
            engine.startEngineService(application);
        }
    }
}
