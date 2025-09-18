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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;

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
import com.frostwire.util.Logger;
import com.frostwire.util.TaskThrottle;
import com.frostwire.util.http.OkHttpClientWrapper;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.ConnectionPool;

/**
 * @author gubatron
 * @author aldenml
 */
public class EngineIntentService extends JobIntentService implements IEngineService {
    // members:
    private static final Logger LOG = Logger.getLogger(EngineIntentService.class);
    private static final long[] VENEZUELAN_VIBE = buildVenezuelanVibe();
    private static final String SHUTDOWN_ACTION = "com.frostwire.android.engine.SHUTDOWN";
    private final IBinder binder;
    private final CoreMediaPlayer mediaPlayer;
    private final AtomicReference<Byte> stateReference = new AtomicReference<>(STATE_UNSTARTED);
    private static volatile byte state;
    private NotificationUpdateDaemon notificationUpdateDaemon;
    private NotifiedStorage notifiedStorage;

    // public:

    public EngineIntentService() {
        binder = new EngineServiceBinder();
        mediaPlayer = new ApolloMediaPlayer();
        updateState(STATE_DISCONNECTED);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(@SuppressWarnings("NullableProblems") Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.info("EngineService::onStartCommand() - intent: " + intent + " flags: " + flags + " startId: " + startId);
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

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> cancelAllNotificationsTask(this));

        if (intent == null) {
            return START_NOT_STICKY;
        }
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent);
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);

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
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> resumeBTEngineTask(this, wasShutdown));
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
            notification.vibrate = ConfigurationManager.instance().vibrateOnFinishedDownload() ? VENEZUELAN_VIBE : null;
            notification.number = TransferManager.instance().getDownloadsToReview();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_MIN);
                channel.setSound(null, null);
                manager.createNotificationChannel(channel);
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
        Intent i = new Intent(ctx, EngineIntentService.class);
        i.setAction(SHUTDOWN_ACTION);
        ctx.startService(i);
    }

    public class EngineServiceBinder extends Binder {
        public EngineIntentService getService() {
            return EngineIntentService.this;
        }
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        LOG.info("EngineService::onHandleWork() - intent: " + intent);
        onStartCommand(intent, 0, 1);
    }

    @Override
    public boolean onStopCurrentWork() {
        //shutdown();
        return true;
    }

    public void updateState(byte newState) {
        stateReference.set(newState);
        //LOG.info("updateState(old=" + getStateString() + " => new=" + getStateString(newState), true);
        state = stateReference.get();
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

    // What a bad design to properly shutdown the framework threads!
    // TODO: deal with potentially active connections
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

    private String getStateString() {
        return getStateString(state);
    }

    private String getStateString(byte _state) {
        return switch (_state) {
            case STATE_UNSTARTED -> "STATE_UNSTARTED";
            case STATE_INVALID -> "STATE_INVALID";
            case STATE_STARTED -> "STATE_STARTED";
            case STATE_STARTING -> "STATE_STARTING";
            case STATE_STOPPED -> "STATE_STOPPED";
            case STATE_STOPPING -> "STATE_STOPPING";
            case STATE_DISCONNECTED -> "STATE_DISCONNECTED";
            default -> "<UNKNOWN_STATE:" + _state + " - Check your logic!>";
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // STATIC SECTION

    /// /////////////////////////////////////////////////////////////////////////////////////////////
    private static void resumeBTEngineTask(EngineIntentService engineIntentService, boolean wasShutdown) {
        LOG.info("resumeBTEngineTask(wasShutdown=" + wasShutdown, true);
        engineIntentService.updateState(STATE_STARTING);
        BTEngine btEngine = BTEngine.getInstance();
        if (!wasShutdown) {
            btEngine.resume();
            TransferManager.instance().forceReannounceTorrents();
        } else {
            btEngine.start();
            TransferManager.instance().reset();
            btEngine.resume();
        }
        engineIntentService.updateState(STATE_STARTED);
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



    private static void cancelAllNotificationsTask(EngineIntentService engineIntentService) {
        try {
            NotificationManager notificationManager = (NotificationManager) engineIntentService.getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            } else {
                LOG.warn("EngineService::cancelAllNotificationsTask(EngineService) notificationManager is null");
            }
        } catch (Throwable t) {
            LOG.warn("EngineService::cancelAllNotificationsTask(EngineService)" + t.getMessage(), t);
        }
    }
}
