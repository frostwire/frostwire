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
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;

/**
 * NotificationUpdateDaemon handles updates to the FrostWire notification using
 * a dedicated background thread that periodically refreshes transfer statistics.
 *
 * @author gubatron
 * @author aldenml
 */
public final class NotificationUpdateDaemon {

    private static final Logger LOG = Logger.getLogger(NotificationUpdateDaemon.class);

    private static final long ACTIVE_UPDATE_INTERVAL_MS = 1000;
    private static final long IDLE_UPDATE_INTERVAL_MS = 15000;

    private final Context mParentContext;
    private RemoteViews notificationViews;
    private Notification notificationObject;
    private final Object lock = new Object();
    private HandlerThread handlerThread;
    private Handler handler;
    private boolean running;

    public NotificationUpdateDaemon(Context parentContext) {
        mParentContext = parentContext.getApplicationContext();
        setupNotification();
    }

    public void start() {
        if (!ensureNotificationEnabled()) {
            LOG.info("NotificationUpdateDaemon start aborted - permanent notification disabled");
            return;
        }

        synchronized (lock) {
            if (running) {
                LOG.debug("NotificationUpdateDaemon is already running");
                return;
            }
            running = true;
            ensureHandlerLocked();
            LOG.info("NotificationUpdateDaemon started");
            handler.post(updateRunnable);
        }
    }

    public void stop() {
        LOG.info("Stopping NotificationUpdateDaemon");
        HandlerThread threadToQuit = null;
        synchronized (lock) {
            if (!running && handlerThread == null) {
                return;
            }
            running = false;
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
            threadToQuit = handlerThread;
            handlerThread = null;
            handler = null;
        }

        if (threadToQuit != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                threadToQuit.quitSafely();
            } else {
                threadToQuit.quit();
            }
        }

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

        // Call startForeground early with proper error handling
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                service.startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification);
            }
        } catch (Exception e) {
            LOG.error("showTempNotification() failed to start foreground service: " + e.getMessage(), e);
            // If we can't start as foreground, the service will likely be killed by the system
            // but we should not crash the app. The service will retry when conditions are better.
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

    private boolean ensureNotificationEnabled() {
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION)) {
            return false;
        }

        if (notificationViews == null || notificationObject == null) {
            setupNotification();
        }

        return notificationViews != null && notificationObject != null;
    }

    private void ensureHandlerLocked() {
        if (handlerThread == null) {
            handlerThread = new HandlerThread("NotificationUpdateDaemon");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            long delay = IDLE_UPDATE_INTERVAL_MS;
            try {
                delay = updateNotification();
            } catch (Exception e) {
                LOG.error("Failed to update notification", e);
            }

            synchronized (lock) {
                if (!running || handler == null) {
                    return;
                }
                handler.postDelayed(this, delay);
            }
        }
    };

    private long updateNotification() {
        if (!ensureNotificationEnabled()) {
            return IDLE_UPDATE_INTERVAL_MS;
        }

        TransferManager transferManager = TransferManager.instance();
        if (transferManager == null) {
            LOG.warn("TransferManager instance is null. Skipping notification update.");
            return IDLE_UPDATE_INTERVAL_MS;
        }

        int downloads = transferManager.getActiveDownloads();
        int uploads = transferManager.getActiveUploads();

        double downloadRate = (double) transferManager.getDownloadsBandwidth() / 1024d;
        double uploadRate = (double) transferManager.getUploadsBandwidth() / 1024d;

        String sDown = UIUtils.rate2speed(downloadRate);
        String sUp = UIUtils.rate2speed(uploadRate);

        if (notificationViews != null) {
            notificationViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
            notificationViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);
        }

        NotificationManager manager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationObject != null) {
            try {
                manager.notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, notificationObject);
            } catch (SecurityException t) {
                LOG.warn(t.getMessage(), t);
            }
        } else {
            LOG.warn("NotificationManager or notificationObject is null. Cannot update notification.");
        }

        return (downloads > 0 || uploads > 0) ? ACTIVE_UPDATE_INTERVAL_MS : IDLE_UPDATE_INTERVAL_MS;
    }
}
