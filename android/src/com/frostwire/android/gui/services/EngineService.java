/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020 FrostWire(R). All rights reserved.
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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.bloom_filter_256;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.frostwire.util.TaskThrottle;
import com.frostwire.util.http.OKHTTPClient;

import java.io.File;

import okhttp3.ConnectionPool;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public class EngineService extends JobIntentService implements IEngineService {
    // members:
    private static final Logger LOG = Logger.getLogger(EngineService.class);
    private static final long[] VENEZUELAN_VIBE = buildVenezuelanVibe();
    private static final String SHUTDOWN_ACTION = "com.frostwire.android.engine.SHUTDOWN";
    private final IBinder binder;
    private final CoreMediaPlayer mediaPlayer;
    private final Object stateLock = new Object();
    private static volatile byte state;
    private NotificationUpdateDaemon notificationUpdateDaemon;
    private NotifiedStorage notifiedStorage;

    // public:

    public EngineService() {
        binder = new EngineServiceBinder();
        mediaPlayer = new ApolloMediaPlayer();
        updateState(STATE_DISCONNECTED);
    }

    @Override
    public void onCreate() {
        notifiedStorage = new NotifiedStorage(this);
        super.onCreate();
        async(this, EngineService::cancelAllNotificationsTask);
        async(this, EngineService::startPermanentNotificationUpdatesTask);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && SHUTDOWN_ACTION.equals(intent.getAction())) {
            LOG.info("onStartCommand() - Received SHUTDOWN_ACTION");
            new Thread("EngineService-onStartCommand(SHUTDOWN_ACTION) -> shutdownSupport") {
                @Override
                public void run() {
                    shutdownSupport();
                }
            }.start();
            return START_NOT_STICKY;
        }

        async(this, EngineService::cancelAllNotificationsTask);

        if (intent == null) {
            return START_NOT_STICKY;
        }
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent.toString());
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);
        async(this, EngineService::startPermanentNotificationUpdatesTask);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOG.debug("onDestroy");
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
        startServices(false);
    }

    public synchronized void startServices(boolean wasShutdown) {
        LOG.info("startServices(wasShutdown=" + wasShutdown + ")", true);
        // hard check for TOS
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            return;
        }

        if (!SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }

        if (isStarted()) {
            LOG.info("startServices() - aborting, it's already started", true);
            return;
        }

        if (isStarting()) {
            LOG.info("startServices() - aborting, it's already starting", true);
            return;
        }

        LOG.info("startServices() - invoking resumeBTEngineTask, wasShutdown=" + wasShutdown);
        TaskThrottle.isReadyToSubmitTask("EngineService::resumeBTEngineTask", 5000);
        async(this, EngineService::resumeBTEngineTask, wasShutdown);
    }

    public synchronized void stopServices(boolean disconnected) {
        if (isStopped() || isStopping() || isDisconnected()) {
            LOG.info("stopServices() aborted - state:" + getStateString());
            return;
        }
        updateState(STATE_STOPPING);
        LOG.info("stopServices() Pausing BTEngine...");
        TransferManager.instance().onShutdown(disconnected);
        BTEngine.getInstance().pause();
        LOG.info("stopServices() Pausing BTEngine paused");
        updateState(disconnected ? STATE_DISCONNECTED : STATE_STOPPED);
        LOG.info("stopServices() Engine stopped, state:" + getStateString());
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

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        onStartCommand(intent, 0, 1);
    }

    @Override
    public boolean onStopCurrentWork() {
        //shutdown();
        return true;
    }

    public void updateState(byte newState) {
        synchronized (stateLock) {
            LOG.info("updateState(old=" + getStateString() + " => new=" + getStateString(newState), true);
            state = newState;
        }
    }

    // private:
    private void shutdownSupport() {
        LOG.debug("shutdownSupport");
        Librarian.instance().shutdownHandler();
        stopPermanentNotificationUpdates();
        cancelAllNotificationsTask(this);
        stopServices(false);
        if (BTEngine.ctx != null) {
            LOG.debug("shutdownSupport(), stopping BTEngine...");
            BTEngine.getInstance().stop();
            LOG.debug("shutdownSupport(), BTEngine stopped");
        } else {
            LOG.debug("shutdownSupport(), BTEngine didn't have a chance to start, no need to stop it");
        }
        stopOkHttp();
        updateState(STATE_STOPPED);
        stopSelf();
    }

    private int getNotificationIcon() {
        return R.drawable.frostwire_notification_flat;
    }

    private void stopPermanentNotificationUpdates() {
        if (notificationUpdateDaemon != null) {
            notificationUpdateDaemon.stop();
        }
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

    private String getStateString() {
        return getStateString(state);
    }

    private String getStateString(byte _state) {
        switch (_state) {
            case STATE_UNSTARTED:
                return "STATE_UNSTARTED";
            case STATE_INVALID:
                return "STATE_INVALID";
            case STATE_STARTED:
                return "STATE_STARTED";
            case STATE_STARTING:
                return "STATE_STARTING";
            case STATE_STOPPED:
                return "STATE_STOPPED";
            case STATE_STOPPING:
                return "STATE_STOPPING";
            case STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
        }
        return "<UNKNOWN_STATE:" + _state + " - Check your logic!>";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // STATIC SECTION
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private static void resumeBTEngineTask(EngineService engineService, boolean wasShutdown) {
        LOG.info("resumeBTEngineTask(wasShutdown=" + wasShutdown, true);
        engineService.updateState(STATE_STARTING);
        BTEngine btEngine = BTEngine.getInstance();
        if (!wasShutdown) {
            btEngine.resume();
            TransferManager.instance().forceReannounceTorrents();
        } else {
            btEngine.start();
            TransferManager.instance().reset();
            btEngine.resume();
        }
        engineService.updateState(STATE_STARTED);
        LOG.info("resumeBTEngineTask(): Engine started", true);
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
            if (engineService.notificationUpdateDaemon == null) {
                engineService.notificationUpdateDaemon = new NotificationUpdateDaemon(engineService.getApplicationContext());
            }
            engineService.notificationUpdateDaemon.start();
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
        }
    }
}
