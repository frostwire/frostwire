/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2013, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.services;

import java.io.File;
import java.util.concurrent.ExecutorService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.*;
import android.os.Process;
import android.util.Log;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.logging.Logger;
import com.frostwire.util.ThreadPool;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class EngineService extends Service implements IEngineService {

    private static final Logger LOG = Logger.getLogger(EngineService.class);

    private static final String TAG = "FW.EngineService";

    private final static long[] VENEZUELAN_VIBE = buildVenezuelanVibe();

    private final IBinder binder;

    static final ExecutorService threadPool = ThreadPool.newThreadPool("Engine");

    // services in background

    private final CoreMediaPlayer mediaPlayer;

    private byte state;

    private OnSharedPreferenceChangeListener preferenceListener;

    public EngineService() {
        binder = new EngineServiceBinder();

        mediaPlayer = new ApolloMediaPlayer(this);

        state = STATE_DISCONNECTED;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOG.debug("EngineService onDestroy");

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
        stopServices(false);

        mediaPlayer.stop();
        mediaPlayer.shutdown();

        BTEngine.getInstance().stop();

        ImageLoader.getInstance(this).shutdown();

        new Thread("shutdown-halt") {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                Process.killProcess(Process.myPid());
            }
        }.start();
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

        state = STATE_STARTING;

        Librarian.instance().invalidateCountCache();

        BTEngine.getInstance().resume();

        state = STATE_STARTED;
        Log.v(TAG, "Engine started");
    }

    public synchronized void stopServices(boolean disconnected) {
        if (isStopped() || isStopping() || isDisconnected()) {
            return;
        }

        state = STATE_STOPPING;

        BTEngine.getInstance().pause();

        state = disconnected ? STATE_DISCONNECTED : STATE_STOPPED;
        Log.v(TAG, "Engine stopped, state: " + state);
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void notifyDownloadFinished(String displayName, File file) {
        try {
            Context context = getApplicationContext();

            Intent i = new Intent(context, MainActivity.class);

            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION, true);
            i.putExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH, file.getAbsolutePath());

            PendingIntent pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            Notification notification = new Notification.Builder(context)
                    .setWhen(System.currentTimeMillis())
                    .setContentText(getString(R.string.download_finished))
                    .setContentTitle(getString(R.string.download_finished))
                    .setSmallIcon(getNotificationIcon())
                    .setContentIntent(pi)
                    .build();

            notification.vibrate = ConfigurationManager.instance().vibrateOnFinishedDownload() ? VENEZUELAN_VIBE : null;
            notification.number = TransferManager.instance().getDownloadsToReview();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            manager.notify(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED, notification);
        } catch (Throwable e) {
            Log.e(TAG, "Error creating notification for download finished", e);
        }
    }

    private int getNotificationIcon() {
        return Build.VERSION.SDK_INT >= 21 ? R.drawable.frostwire_notification_flat : R.drawable.frostwire_notification;
    }

    @Override
    public void shutdown() {
        stopForeground(true);
        stopSelf();
    }

    private static long[] buildVenezuelanVibe() {

        long shortVibration = 80;
        long mediumVibration = 100;
        long shortPause = 100;
        long mediumPause = 150;
        long longPause = 180;

        return new long[] { 0, shortVibration, longPause, shortVibration, shortPause, shortVibration, shortPause, shortVibration, mediumPause, mediumVibration };
    }

    public class EngineServiceBinder extends Binder {
        public EngineService getService() {
            return EngineService.this;
        }
    }
}
