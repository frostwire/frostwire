/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025 FrostWire(R). All rights reserved.
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
import androidx.core.app.NotificationCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.workers.NotificationWorker;
import com.frostwire.android.gui.workers.TorrentEngineWorker;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EngineForegroundService extends Service {
    private static final Logger LOG = Logger.getLogger(EngineForegroundService.class);

    private static final byte STATE_UNSTARTED = 0;
    private static final byte STATE_STARTED = 1;
    private static final byte STATE_STOPPED = 2;
    private static final byte STATE_STOPPING = 3;

    private static volatile byte state = STATE_UNSTARTED;
    private final AtomicReference<Byte> stateReference = new AtomicReference<>(STATE_UNSTARTED);

    private NotificationUpdateDaemon notificationDaemon;
    private NotifiedStorage notifiedStorage;

    @Override
    public void onCreate() {
        super.onCreate();
        LOG.info("EngineForegroundService::onCreate() - Initializing service");

        // Initialize state
        updateState(STATE_UNSTARTED);

        // Initialize helpers
        notificationDaemon = new NotificationUpdateDaemon(this);
        // Start notification daemon
        notificationDaemon.start();

        // Initialize Notified Storage in a background thread
        initializeNotifiedStorage();

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

        // Create and display persistent notification
        Notification notification = createPersistentNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_FROSTWIRE_STATUS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Constants.NOTIFICATION_FROSTWIRE_STATUS, notification);
        }

        if (intent != null && "com.frostwire.android.engine.SHUTDOWN".equals(intent.getAction())) {
            LOG.info("EngineForegroundService::onStartCommand() - Received SHUTDOWN action");
            shutdown();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOG.info("EngineForegroundService::onDestroy() - Stopping service");
        if (notificationDaemon != null) {
            notificationDaemon.stop();
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
        WorkManager.getInstance(this)
                .enqueue(new OneTimeWorkRequest.Builder(TorrentEngineWorker.class).build());
    }

    private void scheduleNotificationWork() {
        WorkManager.getInstance(this)
                .enqueue(new androidx.work.PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build());
    }

    private void shutdown() {
        LOG.info("EngineForegroundService::shutdown() - Performing shutdown");
        if (notificationDaemon != null) {
            notificationDaemon.stop();
        }
        stopServices(false);
        stopForeground(true);
        stopSelf();
    }

    private void stopServices(boolean disconnected) {
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

    private void updateState(byte newState) {
        stateReference.set(newState);
        state = stateReference.get();
        LOG.info("EngineForegroundService::updateState() - Updated state to: " + newState);
    }
}
