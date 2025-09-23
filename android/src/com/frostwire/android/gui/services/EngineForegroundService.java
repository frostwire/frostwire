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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.workers.NotificationWorker;
import com.frostwire.android.gui.workers.TorrentEngineWorker;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;
import com.frostwire.util.TaskThrottle;
import com.frostwire.util.http.OkHttpClientWrapper;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.ConnectionPool;

public class EngineForegroundService extends Service implements IEngineService {
    private static final Logger LOG = Logger.getLogger(EngineForegroundService.class);

    private static final long[] VENEZUELAN_VIBE = buildVenezuelanVibe();

    private static final String SHUTDOWN_ACTION = "com.frostwire.android.engine.SHUTDOWN";

    private static volatile byte state = STATE_UNSTARTED;
    private volatile static EngineForegroundService instance;
    private final AtomicReference<Byte> stateReference = new AtomicReference<>(STATE_UNSTARTED);
    private final Object instanceLock = new Object();
    public byte STATE_DISCONNECTED = 14;
    private NotificationUpdateDaemon notificationUpdateDaemon;
    private NotifiedStorage notifiedStorage;

    public static EngineForegroundService getInstance() {
        return instance;
    }

    private static void resumeBTEngineTask(EngineForegroundService engineForegroundService, boolean wasShutdown) {
        LOG.info("resumeBTEngineTask(wasShutdown=" + wasShutdown, true);
        engineForegroundService.updateState(STATE_STARTING);
        BTEngine btEngine = BTEngine.getInstance();
        if (!wasShutdown) {
            btEngine.resume();
            TransferManager.instance().forceReannounceTorrents();
        } else {
            btEngine.start();
            TransferManager.instance().reset();
            btEngine.resume();
        }
        engineForegroundService.updateState(STATE_STARTED);
        LOG.info("resumeBTEngineTask(): Engine started", true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (instanceLock) {
            instance = this;
        }

        LOG.info("EngineForegroundService::onCreate() - Initializing service");

        // Initialize state
        updateState(STATE_UNSTARTED);

        // Initialize helpers

        // Initialize Notified Storage in a background thread
        initializeNotifiedStorage();
        // Start notification daemon
        startPermanentNotificationUpdatesTask(this);

        // Schedule initial tasks
        scheduleTorrentEngineWork();
        scheduleNotificationWork();
    }

    private void initializeNotifiedStorage() {
        LOG.info("EngineForegroundService::initializeNotifiedStorage() - Initializing in background thread");
        new Thread(() -> {
            notifiedStorage = new NotifiedStorage(this);
            LOG.info("EngineForegroundService::initializeNotifiedStorage() - Initialization complete");
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.info("EngineForegroundService::onStartCommand() - Starting foreground service");
        LOG.info("EngineForegroundService::onStartCommand() - intent: " + intent + " flags: " + flags + " startId: " + startId);

        if (intent != null && SHUTDOWN_ACTION.equals(intent.getAction())) {
            LOG.info("EngineForegroundService::onStartCommand() - Received SHUTDOWN_ACTION");
            new Thread("EngineForegroundService::onStartCommand(SHUTDOWN_ACTION) -> shutdownSupport") {
                @Override
                public void run() {
                    shutdownSupport();
                }
            }.start();
            return START_NOT_STICKY;
        }

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> cancelAllNotificationsTask(this));

        if (intent == null) {
            return START_NOT_STICKY;
        }
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent);
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);

        // Create and display persistent notification
        Notification notification = createPersistentNotification();
        showPersistentNotification(notification);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> startPermanentNotificationUpdatesTask(this));

        return START_STICKY;
    }

    private void showPersistentNotification(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_FROSTWIRE_STATUS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Constants.NOTIFICATION_FROSTWIRE_STATUS, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOG.info("EngineForegroundService::onDestroy() - Stopping service and cleaning up WorkManager jobs");
        
        // Cancel all WorkManager jobs to prevent alarm limit issues
        try {
            WorkManager.getInstance(this).cancelUniqueWork("TorrentEngineWork");
            WorkManager.getInstance(this).cancelUniqueWork("NotificationWork");
            LOG.info("EngineForegroundService::onDestroy() - Cancelled WorkManager jobs");
        } catch (Exception e) {
            LOG.warn("EngineForegroundService::onDestroy() - Error cancelling WorkManager jobs: " + e.getMessage(), e);
        }
        
        if (notificationUpdateDaemon != null) {
            cancelAllNotificationsTask(this);
            notificationUpdateDaemon.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding required
    }

    private Notification createPersistentNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            NotificationChannel channel = notificationManager.getNotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);
                notificationManager.createNotificationChannel(channel);
            }
        }

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.view_permanent_status_notification);

        PendingIntent showFrostWireIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class)
                        .setAction(Constants.ACTION_SHOW_TRANSFERS)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent shutdownIntent = PendingIntent.getActivity(
                this,
                1,
                new Intent(this, MainActivity.class)
                        .setAction(Constants.ACTION_REQUEST_SHUTDOWN)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
        );

        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

        return new NotificationCompat.Builder(this, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.frostwire_notification_flat)
                .setContent(remoteViews)
                .setContentIntent(showFrostWireIntent)
                .setOngoing(true)
                .build();
    }

    private void scheduleTorrentEngineWork() {
        // Use TaskThrottle to prevent rapid re-scheduling of the same work
        if (!TaskThrottle.isReadyToSubmitTask("TorrentEngineWork", 30000)) { // 30 second minimum interval
            LOG.info("TorrentEngineWork throttled - too soon since last execution");
            return;
        }
        
        // Use unique work name to prevent duplicate scheduling
        WorkManager.getInstance(this)
                .enqueueUniqueWork(
                    "TorrentEngineWork",
                    androidx.work.ExistingWorkPolicy.KEEP, // Don't replace if already running
                    new OneTimeWorkRequest.Builder(TorrentEngineWorker.class).build()
                );
        LOG.info("TorrentEngineWork scheduled");
    }

    private void scheduleNotificationWork() {
        // Cancel any existing notification work to prevent duplicates
        WorkManager.getInstance(this).cancelUniqueWork("NotificationWork");
        
        // The NotificationUpdateDaemon is already being started, so we don't need this duplicate work
        // Commenting out to prevent duplicate periodic work scheduling
        /*
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "NotificationWork",
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    new androidx.work.PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build()
                );
        */
    }

    public void shutdown() {
        LOG.info("EngineForegroundService::shutdown() - Performing shutdown");
        if (notificationUpdateDaemon != null) {
            notificationUpdateDaemon.stop();
        }
        stopServices(false);
        stopForeground(true);
        stopSelf();
    }

    private void shutdownSupport() {
        LOG.debug("shutdownSupport");
        
        // Cancel all WorkManager jobs first to prevent scheduling conflicts
        try {
            WorkManager workManager = WorkManager.getInstance(this);
            workManager.cancelUniqueWork("TorrentEngineWork");
            workManager.cancelUniqueWork("NotificationWork");
            workManager.cancelUniqueWork("NotificationUpdateDaemon"); // From NotificationUpdateDaemon
            LOG.debug("shutdownSupport - cancelled all WorkManager jobs");
        } catch (Exception e) {
            LOG.warn("shutdownSupport - error cancelling WorkManager jobs: " + e.getMessage(), e);
        }
        
        Librarian.instance().shutdownHandler();
        stopPermanentNotificationUpdates();
        cancelAllNotificationsTask(this);
        stopServices(false);
        if (BTEngine.ctx != null) {
            LOG.debug("EngineForegroundService::shutdownSupport(), stopping BTEngine...");
            BTEngine.getInstance().stop();
            LOG.debug("EngineForegroundService::shutdownSupport(), BTEngine stopped");
        } else {
            LOG.debug("EngineForegroundService::shutdownSupport(), BTEngine didn't have a chance to start, no need to stop it");
        }
        stopOkHttp();
        updateState(STATE_STOPPED);
        stopSelf();
    }

    private void stopPermanentNotificationUpdates() {
        if (notificationUpdateDaemon != null) {
            notificationUpdateDaemon.stop();
        }
    }

    private void stopOkHttp() {
        ConnectionPool pool = OkHttpClientWrapper.CONNECTION_POOL;
        try {
            pool.evictAll();
        } catch (Throwable e) {
            LOG.error("EngineService::stopOkHttp() Error evicting all connections from OkHttp ConnectionPool", e);
        }
        try {
            synchronized (OkHttpClientWrapper.CONNECTION_POOL) {
                pool.notifyAll();
            }
        } catch (Throwable e) {
            LOG.error("EngineService::stopOkHttp() Error notifying all threads waiting on OkHttp ConnectionPool", e);
        }
    }

    private static void cancelAllNotificationsTask(EngineForegroundService engineForegroundService) {
        try {
            NotificationManager notificationManager = (NotificationManager) engineForegroundService.getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            } else {
                LOG.warn("EngineForegroundService::cancelAllNotificationsTask(EngineForegroundService) notificationManager is null");
            }
        } catch (Throwable t) {
            LOG.warn("EngineForegroundService::cancelAllNotificationsTask(EngineForegroundService)" + t.getMessage(), t);
        }
    }

    private static void startPermanentNotificationUpdatesTask(EngineForegroundService engineForegroundService) {
        try {
            if (engineForegroundService.notificationUpdateDaemon == null) {
                engineForegroundService.notificationUpdateDaemon = new NotificationUpdateDaemon(engineForegroundService.getApplicationContext());
            } else {
                LOG.warn("EngineForegroundService::startPermanentNotificationUpdatesTask(EngineService) notificationUpdateDaemon is not null");
            }
            engineForegroundService.notificationUpdateDaemon.start();
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
        }
    }


    @Override
    public CoreMediaPlayer getMediaPlayer() {
        return null;
    }

    @Override
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

    @Override
    public void startServices() {
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
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> resumeBTEngineTask(this, wasShutdown));
    }

    public void stopServices(boolean disconnected) {
        if (state == STATE_STOPPED || state == STATE_STOPPING) {
            LOG.info("EngineForegroundService::stopServices() - Already stopped or stopping");
            return;
        }
        updateState(STATE_STOPPING);
        LOG.info("EngineForegroundService::stopServices() - Pausing BTEngine");
        TransferManager.instance().onShutdown(disconnected);
        BTEngine.getInstance().pause();

        // maybe here we do something with disconnected
        updateState(STATE_STOPPED);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
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
            PendingIntent pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Notification notification = new NotificationCompat.Builder(context, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                    .setWhen(System.currentTimeMillis())
                    .setContentText(getString(R.string.download_finished))
                    .setContentTitle(getString(R.string.download_finished))
                    .setSmallIcon(getNotificationIcon())
                    .setContentIntent(pi)
                    .build();
            notification.number = TransferManager.instance().getDownloadsToReview();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_MIN);
                channel.setSound(null, null);
                channel.setVibrationPattern(ConfigurationManager.instance().vibrateOnFinishedDownload() ? VENEZUELAN_VIBE : null);
                manager.createNotificationChannel(channel);
                manager.notify(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED, notification);
            }
        } catch (Throwable e) {
            LOG.error("Error creating notification for download finished", e);
        }
    }

    private void updateState(byte newState) {
        stateReference.set(newState);
        state = stateReference.get();
        LOG.info("EngineForegroundService::updateState() - Updated state to: " + newState);
    }

    private int getNotificationIcon() {
        return R.drawable.frostwire_notification_flat;
    }

    private static long[] buildVenezuelanVibe() {

        long shortVibration = 80;
        long mediumVibration = 100;
        long shortPause = 100;
        long mediumPause = 150;
        long longPause = 180;

        return new long[]{0, shortVibration, longPause, shortVibration, shortPause, shortVibration, shortPause, shortVibration, mediumPause, mediumVibration};
    }

}
