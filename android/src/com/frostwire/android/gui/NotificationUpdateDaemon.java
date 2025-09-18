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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;

import java.util.concurrent.TimeUnit;

/**
 * NotificationUpdateDaemon handles updates to the FrostWire notification
 * using WorkManager for periodic background tasks.
 *
 * @author gubatron
 * @author aldenml
 */
public final class NotificationUpdateDaemon {

    private static final Logger LOG = Logger.getLogger(NotificationUpdateDaemon.class);

    private final Context mParentContext;
    private RemoteViews notificationViews;
    private Notification notificationObject;

    public NotificationUpdateDaemon(Context parentContext) {
        mParentContext = parentContext;
        setupNotification();
    }

    public void start() {
        LOG.info("Starting NotificationUpdateDaemon with WorkManager");
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class,
                15, // Minimum interval for PeriodicWorkRequest
                TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(mParentContext).enqueueUniquePeriodicWork(
                "NotificationUpdateDaemon",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
        );
    }

    public void stop() {
        LOG.info("Stopping NotificationUpdateDaemon with WorkManager");
        WorkManager.getInstance(mParentContext).cancelUniqueWork("NotificationUpdateDaemon");

        NotificationManager manager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
            } catch (SecurityException t) {
                LOG.warn(t.getMessage(), t);
            }
        }
    }

    public static void showTempNotification(MusicPlaybackService service) {
        // Prepare a temporary notification for startForeground
        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("FrostWire Music Player")
                .setContentText("Player starting...")
                .setSmallIcon(R.drawable.menu_icon_my_music) // Replace with your app icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true); // Mark it as ongoing for a foreground service
        Notification notification = builder.build();

        // Call startForeground early
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            service.startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification);
        }
    }

    private void setupNotification() {
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION)) {
            LOG.info("setupNotification() aborted, PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION=false");
            return;
        }

        RemoteViews remoteViews = new RemoteViews(mParentContext.getPackageName(),
                R.layout.view_permanent_status_notification);

        PendingIntent showFrostWireIntent = createShowFrostWireIntent();
        PendingIntent shutdownIntent = createShutdownIntent();

        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

        Notification notification = new NotificationCompat.Builder(mParentContext, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.frostwire_notification_flat)
                .setContentIntent(showFrostWireIntent)
                .setContent(remoteViews)
                .setOngoing(true) // Makes the notification persistent
                .build();

        notification.flags |= Notification.FLAG_NO_CLEAR;

        notificationViews = remoteViews;
        notificationObject = notification;
    }

    private PendingIntent createShowFrostWireIntent() {
        return PendingIntent.getActivity(mParentContext,
                0,
                new Intent(mParentContext, MainActivity.class)
                        .setAction(Constants.ACTION_SHOW_TRANSFERS)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createShutdownIntent() {
        return PendingIntent.getActivity(mParentContext,
                1,
                new Intent(mParentContext, MainActivity.class)
                        .setAction(Constants.ACTION_REQUEST_SHUTDOWN)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE);
    }

    public Notification getNotificationObject() {
        return notificationObject;
    }

    public RemoteViews getNotificationViews() {
        return notificationViews;
    }

    public Context getParentContext() {
        return mParentContext;
    }

    /**
     * Worker for updating the FrostWire notification periodically.
     */
    public static class NotificationWorker extends Worker {

        public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            LOG.info("Executing NotificationWorker");

            Context context = getApplicationContext();
            NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);

            TransferManager transferManager = TransferManager.instance();

            if (transferManager == null) {
                LOG.warn("TransferManager instance is null. Skipping notification update.");
                return Result.success();
            }

            int downloads = transferManager.getActiveDownloads();
            int uploads = transferManager.getActiveUploads();

            if (downloads == 0 && uploads == 0) {
                LOG.info("No active transfers. Clearing notification.");
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
                }
                return Result.success();
            }

            // Update notification
            String sDown = UIUtils.rate2speed((double) transferManager.getDownloadsBandwidth() / 1024);
            String sUp = UIUtils.rate2speed(transferManager.getUploadsBandwidth() / 1024);

            RemoteViews notificationViews = daemon.getNotificationViews();
            if (notificationViews != null) {
                notificationViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
                notificationViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, daemon.getNotificationObject());
            }

            return Result.success();
        }
    }
}
