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

package com.frostwire.android.gui.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.andrew.apollo.MediaButtonIntentReceiver;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.NotificationUpdateDemon;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.bloom_filter_256;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.frostwire.util.http.OKHTTPClient;

import java.io.File;

import okhttp3.ConnectionPool;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public class EngineService extends Service implements IEngineService {
    private static final Logger LOG = Logger.getLogger(EngineService.class);
    private final static long[] VENEZUELAN_VIBE = buildVenezuelanVibe();

    private static final String SHUTDOWN_ACTION = "com.frostwire.android.engine.SHUTDOWN";

    private final IBinder binder;
    // services in background
    private final CoreMediaPlayer mediaPlayer;
    private byte state;
    private NotificationUpdateDemon notificationUpdateDemon;

    private NotifiedStorage notifiedStorage;

    public EngineService() {
        binder = new EngineServiceBinder();

        mediaPlayer = new ApolloMediaPlayer();

        state = STATE_DISCONNECTED;
    }

    @Override
    public void onCreate() {
        notifiedStorage = new NotifiedStorage(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        async(this, EngineService::cancelAllNotificationsTask);
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if (SHUTDOWN_ACTION.equals(intent.getAction())) {
            LOG.info("onStartCommand() - Received SHUTDOWN_ACTION");
            new Thread("EngineService-onStartCommand(SHUTDOWN_ACTION) -> shutdownSupport") {
                @Override
                public void run() {
                    shutdownSupport();
                }
            }.start();
            return START_NOT_STICKY;
        }
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent.toString());
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);
        async(this, EngineService::enableComponentsTask, true);
        async(this, EngineService::startPermanentNotificationUpdatesTask);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOG.debug("onDestroy");
    }

    private void shutdownSupport() {
        LOG.debug("shutdownSupport");
        enableComponentsTask(this, false); // we're already on a shutdown thread
        stopPermanentNotificationUpdates();
        cancelAllNotificationsTask(this);
        stopServices(false);

        if (BTEngine.ctx != null) {
            LOG.debug("onDestroy, stopping BTEngine...");
            BTEngine.getInstance().stop();
            LOG.debug("onDestroy, BTEngine stopped");
        } else {
            LOG.debug("onDestroy, BTEngine didn't have a chance to start, no need to stop it");
        }

        ImageLoader.getInstance(this).shutdown();
        PlayStore.getInstance().dispose();
        stopOkHttp();

        stopForeground(true);
        stopSelf();
    }

    // what a bad design to properly shutdown the framework threads!
    // TODO: deal with potentially active connections
    private void stopOkHttp() {
        ConnectionPool pool = OKHTTPClient.CONNECTION_POOL;
        try {
            pool.evictAll();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            synchronized (OKHTTPClient.CONNECTION_POOL) {
                pool.notifyAll();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public CoreMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public byte getState() {
        return state;
    }

    public boolean isStarted() {
        return getState() == STATE_STARTED;
    }

    public boolean isStarting() {
        return getState() == STATE_STARTING;
    }

    public boolean isStopped() {
        return getState() == STATE_STOPPED;
    }

    public boolean isStopping() {
        return getState() == STATE_STOPPING;
    }

    public boolean isDisconnected() {
        return getState() == STATE_DISCONNECTED;
    }

    public synchronized void startServices() {
        // hard check for TOS
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            return;
        }

        if (!SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }

        if (isStarted() || isStarting()) {
            return;
        }

        async(this, EngineService::resumeBTEngineTask);
    }

    private static void resumeBTEngineTask(EngineService engineService) {
        engineService.state = STATE_STARTING;
        BTEngine.getInstance().resume();
        engineService.state = STATE_STARTED;
        LOG.info("resumeBTEngineTask(): Engine started", true);
    }

    public synchronized void stopServices(boolean disconnected) {
        if (isStopped() || isStopping() || isDisconnected()) {
            return;
        }

        state = STATE_STOPPING;

        LOG.info("Pausing BTEngine...");
        BTEngine.getInstance().pause();
        LOG.info("Pausing BTEngine paused");

        state = disconnected ? STATE_DISCONNECTED : STATE_STOPPED;
        LOG.info("Engine stopped, state: " + state);
    }

    public void notifyDownloadFinished(String displayName, File file, String infoHash) {
        try {
            if (notifiedStorage.contains(infoHash)) {
                // already notified
                return;
            } else {
                notifiedStorage.add(infoHash);
            }

            Context context = getApplicationContext();
            Intent i = new Intent(context, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION, true);
            i.putExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH, file.getAbsolutePath());
            PendingIntent pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Notification notification = new NotificationCompat.Builder(context, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                    .setWhen(System.currentTimeMillis())
                    .setContentText(getString(R.string.download_finished))
                    .setContentTitle(getString(R.string.download_finished))
                    .setSmallIcon(getNotificationIcon())
                    .setContentIntent(pi)
                    .build();
            notification.vibrate = ConfigurationManager.instance().vibrateOnFinishedDownload() ? VENEZUELAN_VIBE : null;
            notification.number = TransferManager.instance().getDownloadsToReview();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            if (manager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_MIN);
                    channel.setSound(null, null);
                    manager.createNotificationChannel(channel);
                }
                manager.notify(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED, notification);
            }
        } catch (Throwable e) {
            LOG.error("Error creating notification for download finished", e);
        }
    }

    @Override
    public void shutdown() {
        LOG.info("shutdown");

        Context ctx = getApplication();
        Intent i = new Intent(ctx, EngineService.class);
        i.setAction(SHUTDOWN_ACTION);
        ctx.startService(i);
    }

    public class EngineServiceBinder extends Binder {
        public EngineService getService() {
            return EngineService.this;
        }
    }

    private int getNotificationIcon() {
        return R.drawable.frostwire_notification_flat;
    }

    private void stopPermanentNotificationUpdates() {
        if (notificationUpdateDemon != null) {
            notificationUpdateDemon.stop();
        }
    }

    private static long[] buildVenezuelanVibe() {

        long shortVibration = 80;
        long mediumVibration = 100;
        long shortPause = 100;
        long mediumPause = 150;
        long longPause = 180;

        return new long[]{0, shortVibration, longPause, shortVibration, shortPause, shortVibration, shortPause, shortVibration, mediumPause, mediumVibration};
    }

    private static final class NotifiedStorage {

        // this is a preference key to be used only by this class
        private static final String PREF_KEY_NOTIFIED_HASHES = "frostwire.prefs.gui.notified_hashes";

        // not using ConfigurationManager to avoid setup/startup timing issues
        private final SharedPreferences preferences;
        private final bloom_filter_256 hashes;

        NotifiedStorage(Context context) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
            hashes = new bloom_filter_256();

            loadHashes();
        }

        public boolean contains(String infoHash) {
            if (infoHash == null || infoHash.length() != 40) {
                // not a valid info hash
                return false;
            }

            try {

                byte[] arr = Hex.decode(infoHash);
                sha1_hash ih = new sha1_hash(Vectors.bytes2byte_vector(arr));
                return hashes.find(ih);

            } catch (Throwable e) {
                LOG.warn("Error checking if info hash was notified", e);
            }

            return false;
        }

        public void add(String infoHash) {
            if (infoHash == null || infoHash.length() != 40) {
                // not a valid info hash
                return;
            }

            try {

                byte[] arr = Hex.decode(infoHash);
                sha1_hash ih = new sha1_hash(Vectors.bytes2byte_vector(arr));
                hashes.set(ih);

                byte_vector v = hashes.to_bytes();
                arr = Vectors.byte_vector2bytes(v);
                String s = Hex.encode(arr);

                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(PREF_KEY_NOTIFIED_HASHES, s);
                editor.apply();

            } catch (Throwable e) {
                LOG.warn("Error adding info hash to notified storage", e);
            }
        }

        private void loadHashes() {
            String s = preferences.getString(PREF_KEY_NOTIFIED_HASHES, null);
            if (s != null) {
                try {
                    byte[] arr = Hex.decode(s);
                    hashes.from_bytes(Vectors.bytes2byte_vector(arr));
                } catch (Throwable e) {
                    LOG.warn("Error loading notified storage from preference data", e);
                }
            }
        }
    }

    private static void enableComponent(EngineService engineService, PackageManager pm, Class<?> clazz, boolean enable) {
        int newState = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        ComponentName receiver = new ComponentName(engineService, clazz);
        int currentState = pm.getComponentEnabledSetting(receiver);
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            LOG.info("Receiver " + receiver + " was disabled");
        } else {
            LOG.info("Receiver " + receiver + " was enabled");
        }
        pm.setComponentEnabledSetting(receiver,
                newState,
                PackageManager.DONT_KILL_APP);
        currentState = pm.getComponentEnabledSetting(receiver);
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            LOG.info("Receiver " + receiver + " now is disabled");
        } else {
            LOG.info("Receiver " + receiver + " now is enabled");
        }
    }

    private static void enableComponentsTask(EngineService engineService, boolean enable) {
        try {
            PackageManager pm = engineService.getPackageManager();
            // receivers
            async(engineService, EngineService::enableComponent, pm, EngineBroadcastReceiver.class, enable);
            async(engineService, EngineService::enableComponent, pm, MediaButtonIntentReceiver.class, enable);
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
        }
    }

    private static void cancelAllNotificationsTask(EngineService engineService) {
        try {
            NotificationManager notificationManager = (NotificationManager) engineService.getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
        } catch (SecurityException ignore) {
            // new exception in Android 7
        }
    }

    private static void startPermanentNotificationUpdatesTask(EngineService engineService) {
        try {
            if (engineService.notificationUpdateDemon == null) {
                engineService.notificationUpdateDemon = new NotificationUpdateDemon(engineService.getApplicationContext());
            }
            engineService.notificationUpdateDemon.start();
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
        }
    }
}
