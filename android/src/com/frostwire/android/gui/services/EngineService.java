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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class EngineService extends Service implements IEngineService {

    public final static int FROSTWIRE_STATUS_NOTIFICATION = 0x4ac4642a; // just a random number
    private static final Logger LOG = Logger.getLogger(EngineService.class);
    private static final String TAG = "FW.EngineService";
    private final static long[] VENEZUELAN_VIBE = buildVenezuelanVibe();
    private static boolean isVPNactive = false;
    private final IBinder binder;
    static final ExecutorService threadPool = ThreadPool.newThreadPool("Engine");
    // services in background
    private final CoreMediaPlayer mediaPlayer;
    private byte state;

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
        if (intent==null) {
            return 0;
        }
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent.toString());
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);
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
                    Thread.sleep(2000);
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

        updatePermanentStatusNotification(new WeakReference<Context>(this),
                null,
                0, "0 " + UIUtils.GENERAL_UNIT_KBPSEC,
                0, "0 " + UIUtils.GENERAL_UNIT_KBPSEC);

        Log.v(TAG, "Engine started");
    }

    public static void updatePermanentStatusNotification(WeakReference<Context> contextRef,
                                                         WeakReference<View> viewRef,
                                                         int downloads,
                                                         String sDown,
                                                         int uploads,
                                                         String sUp) {
        if (!Ref.alive(contextRef) || !Ref.alive(viewRef) ||
            !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION)) {
            return;
        }
        final Context context = contextRef.get();
        final View view = viewRef.get();
        asyncCheckVPNStatus(view);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                R.layout.view_permanent_status_notification);

        PendingIntent showFrostWireIntent = PendingIntent.getActivity(context,
                0,
                new Intent(context,
                           MainActivity.class).
                        setAction(Constants.ACTION_SHOW_TRANSFERS).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);

//        PendingIntent showVPNIntent = PendingIntent.getActivity(context,
//                1,
//                new Intent(context,
//                        VPNStatusDetailActivity.class).
//                        setAction(isVPNactive ?
//                                Constants.ACTION_SHOW_VPN_STATUS_PROTECTED :
//                                Constants.ACTION_SHOW_VPN_STATUS_UNPROTECTED).
//                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
//                0);

        PendingIntent shutdownIntent = PendingIntent.getActivity(context,
                1,
                new Intent(context,
                        MainActivity.class).
                        setAction(Constants.ACTION_REQUEST_SHUTDOWN).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);


        // VPN status
//        remoteViews.setImageViewResource(R.id.view_permanent_status_vpn_icon, isVPNactive ?
//                R.drawable.notification_shutdown : R.drawable.notification_shutdown);
//        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_vpn_icon, showVPNIntent);


        // Click on shutdown image button.
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);

        // Click on title takes to transfers.
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

        // Transfers status.
        remoteViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
        remoteViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);

        Notification notification = new NotificationCompat.Builder(context).
                setSmallIcon(R.drawable.frostwire_notification_flat).
                setContentIntent(showFrostWireIntent).
                setContent(remoteViews).
                build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(FROSTWIRE_STATUS_NOTIFICATION, notification);
        }
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

    @Override
    public void shutdown() {
        stopForeground(true);
        stopSelf();
    }

    public static void asyncCheckVPNStatus(View view) {
        asyncCheckVPNStatus(view, null);
    }

    public static void asyncCheckVPNStatus(final View view, final VpnStatusUICallback callback) {
        threadPool.submit(new Runnable() {
            @Override
            public void run() {
                isVPNactive = isTunnelUp();

                if (callback != null && view != null) {
                    // execute the UI update on the UI thread.
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onVpnStatus(isVPNactive);
                        }
                    });

                }
            }
        });
    }

    public static void asyncCheckDHTPeers(final View view, final CheckDHTUICallback callback) {
        threadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BTEngine engine = BTEngine.getInstance();
                    final Session session = engine.getSession();
                    if (session != null && session.isDHTRunning()) {
                        session.postDHTStats();
                        final int totalDHTNodes = engine.getTotalDHTNodes();
                        if (totalDHTNodes != -1 && view != null) {
                            view.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onCheckDHT(true, totalDHTNodes);
                                }
                            });

                        }
                    } else {
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onCheckDHT(false, 0);
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }

            }
        });
    }

    public class EngineServiceBinder extends Binder {
        public EngineService getService() {
            return EngineService.this;
        }
    }

    public interface VpnStatusUICallback {
        // Code inside this method must be for UI changes, meant to be executed on UI Thread
        void onVpnStatus(final boolean vpnActive);
    }

    public interface CheckDHTUICallback {
        // Code inside this method must be for UI changes, meant to be executed on UI Thread
        void onCheckDHT(final boolean dhtEnabled, final int dhtPeers);
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

        return new long[] { 0, shortVibration, longPause, shortVibration, shortPause, shortVibration, shortPause, shortVibration, mediumPause, mediumVibration };
    }

    /** takes 0ms (135052 ns) - checks diretory, and reads corresponding file to create NetworkInterface object. */
    private static boolean isTunnelUp() {
        NetworkInterface tun0 = null;
        try {
            tun0 = NetworkInterface.getByName("tun0");
        } catch (SocketException e) {
            LOG.error(e.getMessage(), e);
            return isAnyNetworkInterfaceATunnel();
        }
        return tun0 != null;
    }

    /* takes between 15ms to 65ms up to 300ms, will use as fallback. */
    private static boolean isAnyNetworkInterfaceATunnel() {
        boolean result = false;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                //LOG.info("isAnyNetworkInterfaceATunnel() -> " + iface.getDisplayName());
                if (iface.getDisplayName().contains("tun")) {
                    result = true;
                    break;
                }
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }

        return result;
    }
}
