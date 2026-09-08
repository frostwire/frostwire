/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import android.app.ForegroundServiceStartNotAllowedException;
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
import androidx.work.WorkManager;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;


public class EngineForegroundService extends Service implements IEngineService {
    private static final Logger LOG = Logger.getLogger(EngineForegroundService.class);

    private static final long[] VENEZUELAN_VIBE = buildVenezuelanVibe();

    private static final String SHUTDOWN_ACTION = "com.frostwire.android.engine.SHUTDOWN";

    private volatile byte state = STATE_UNSTARTED;
    private volatile static EngineForegroundService instance;
    private final AtomicReference<Byte> stateReference = new AtomicReference<>(STATE_UNSTARTED);
    private static final Object INSTANCE_LOCK = new Object();
    private final Object relayLock = new Object();
    private final RelayServiceGeneration engineGeneration = new RelayServiceGeneration();
    private final RelayServiceGeneration relayGeneration = new RelayServiceGeneration();
    private final ConfigurationRepository.OnPreferenceChangeListener relayPreferences = key -> {
        if (Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY.equals(key)
                || Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY.equals(key)) {
            if (!AndroidRelayStack.isNetworkAllowed()) {
                Engine.instance().stopServices(true);
            } else {
                Engine.instance().resumeServicesIfDisconnected();
            }
            ensureRelayStack(true, null);
        } else if (Constants.PREF_KEY_ICEBRIDGE_ENABLED.equals(key)) {
            ensureRelayStack(true, null);
        }
    };
    public byte STATE_DISCONNECTED = 14;
    private NotificationUpdateDaemon notificationUpdateDaemon;
    private NotifiedStorage notifiedStorage;
    private volatile AndroidRelayStack relayStack;
    private volatile boolean foregroundReady;

    public static EngineForegroundService getInstance() {
        return instance;
    }

    private static void resumeBTEngineTask(EngineForegroundService engineForegroundService, boolean wasShutdown,
                                         long generation) {
        if (!engineForegroundService.engineGeneration.isCurrent(generation)
                || instance != engineForegroundService || Engine.instance().wasShutdown()
                || !AndroidRelayStack.isNetworkAllowed()) {
            return;
        }
        LOG.info("resumeBTEngineTask(wasShutdown=" + wasShutdown, true);
        engineForegroundService.updateState(STATE_STARTING);
        BTEngine btEngine = BTEngine.getInstance();
        if (wasShutdown || btEngine.swig() == null) {
            btEngine.start();
        }
        if (wasShutdown) {
            TransferManager.instance().reset();
        }
        btEngine.resume();
        if (!engineForegroundService.engineGeneration.isCurrent(generation)
                || Engine.instance().wasShutdown() || !AndroidRelayStack.isNetworkAllowed()) {
            btEngine.pause();
            return;
        }
        TransferManager.instance().ensureTorrentsRestored();
        if (!engineForegroundService.engineGeneration.isCurrent(generation)) return;
        engineForegroundService.startRelayStack();
        if (!wasShutdown) {
            TransferManager.instance().forceReannounceTorrents();
        }
        if (!engineForegroundService.engineGeneration.isCurrent(generation)) return;
        engineForegroundService.updateState(STATE_STARTED);
        LOG.info("resumeBTEngineTask(): Engine started", true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (INSTANCE_LOCK) {
            instance = this;
        }
        ConfigurationManager.instance().registerOnPreferenceChange(relayPreferences);

        LOG.info("EngineForegroundService::onCreate() - Initializing service");

        // Initialize state
        updateState(STATE_UNSTARTED);

        // Initialize helpers

        // Initialize Notified Storage in a background thread
        initializeNotifiedStorage();
        // Start notification daemon
        startPermanentNotificationUpdatesTask(this);

        // Schedule initial tasks
        scheduleNotificationWork();
    }

    private void initializeNotifiedStorage() {
        LOG.info("EngineForegroundService::initializeNotifiedStorage() - Initializing in background thread");
        final long generation = engineGeneration.current();
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> {
            if (!engineGeneration.isCurrent(generation)) return;
            NotifiedStorage storage = new NotifiedStorage(getApplicationContext());
            if (engineGeneration.isCurrent(generation)) notifiedStorage = storage;
            LOG.info("EngineForegroundService::initializeNotifiedStorage() - Initialization complete");
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.info("EngineForegroundService::onStartCommand() - Starting foreground service");
        LOG.info("EngineForegroundService::onStartCommand() - intent: " + intent + " flags: " + flags + " startId: " + startId);

        boolean isNullIntentRestart = intent == null;
        boolean isAppInForeground = SystemUtils.isAppInForeground(this);
        EngineForegroundStartPolicy.Action startAction = EngineForegroundStartPolicy.resolve(
                Build.VERSION.SDK_INT,
                isNullIntentRestart,
                isAppInForeground);

        if (startAction == EngineForegroundStartPolicy.Action.STOP_BACKGROUND_RESTART) {
            LOG.warn("EngineForegroundService::onStartCommand() - Skipping foreground promotion for background sticky restart");
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        Notification notification = createPersistentNotification();
        if (!tryShowPersistentNotification(notification, startId, isNullIntentRestart, isAppInForeground)) {
            return START_NOT_STICKY;
        }
        foregroundReady = true;

        if (intent != null && SHUTDOWN_ACTION.equals(intent.getAction())) {
            LOG.info("EngineForegroundService::onStartCommand() - Received SHUTDOWN_ACTION");
            shutdown();
            return START_NOT_STICKY;
        }
        Engine.instance().onForegroundServiceCreated(this);

        if (!acceptsStarts() || Engine.instance().wasShutdown()) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            if (instance == this && acceptsStarts()) cancelAllNotificationsTask(this);
        });

        startServices();
        if (intent == null) return START_NOT_STICKY;
        LOG.info("FrostWire's EngineService started by this intent:");
        LOG.info("FrostWire:" + intent);
        LOG.info("FrostWire: flags:" + flags + " startId: " + startId);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> startPermanentNotificationUpdatesTask(this));

        return START_STICKY;
    }

    private boolean tryShowPersistentNotification(Notification notification,
                                                  int startId,
                                                  boolean isNullIntentRestart,
                                                  boolean isAppInForeground) {
        try {
            showPersistentNotification(notification);
            return true;
        } catch (RuntimeException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e instanceof ForegroundServiceStartNotAllowedException) {
                LOG.warn("EngineForegroundService::tryShowPersistentNotification() - Foreground promotion not allowed. " +
                                "nullIntentRestart=" + isNullIntentRestart +
                                " appInForeground=" + isAppInForeground, e);
                stopSelfResult(startId);
                return false;
            }
            throw e;
        }
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
        foregroundReady = false;
        engineGeneration.retire();
        relayGeneration.retire();
        stopRelayStack();
        ConfigurationManager.instance().unregisterOnPreferenceChange(relayPreferences);
        synchronized (INSTANCE_LOCK) {
            if (instance == this) {
                instance = null;
            }
        }
        super.onDestroy();
        Engine.instance().onForegroundServiceDestroyed(this);
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
        // A recreated service gets a new generation. No public sockets belong
        // to a destroyed service, even when libtorrent remains in the process.
    }

    private void startRelayStack() {
        ensureRelayStack(false, null);
    }

    /**
     * Whether the distributed-search / IceBridge stack is running.
     */
    public boolean isRelayStackRunning() {
        return relayStack != null && mayParticipate(relayGeneration.current());
    }

    boolean acceptsStarts() {
        return foregroundReady && engineGeneration.isCurrent(engineGeneration.current());
    }

    /**
     * Live relay stack, or {@code null} if not started. Used by Distributed Search settings.
     */
    @Nullable
    public AndroidRelayStack getRelayStack() {
        return isRelayStackRunning() ? relayStack : null;
    }

    /**
     * Start the relay stack if missing, or force-restart it. Runs off the main
     * thread. Used from Settings when identity shows "Not initialized" or the
     * peer directory is unavailable.
     *
     * @param forceRestart if true, tear down an existing stack first
     * @param done         optional callback on the main thread (may be null)
     */
    public void ensureRelayStack(boolean forceRestart, Runnable done) {
        if (forceRestart) {
            stopRelayStack();
        }
        final long generation = relayGeneration.current();
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                if (relayStack == null && mayParticipate(generation)) {
                    BTEngine btEngine = BTEngine.getInstance();
                    File homeDir = BTEngine.ctx != null ? BTEngine.ctx.homeDir : null;
                    if (homeDir == null) {
                        LOG.warn("EngineForegroundService::ensureRelayStack: no libtorrent homeDir");
                    } else {
                        // First-run PoW identity mining can take several seconds.
                        AndroidRelayStack started = AndroidRelayStack.start(this, homeDir, btEngine,
                                () -> mayParticipate(generation));
                        boolean adopted = false;
                        synchronized (relayLock) {
                            if (started != null && mayParticipate(generation)) {
                                relayStack = started;
                                adopted = true;
                            }
                        }
                        if (started != null && !adopted) {
                            started.close();
                        }
                    }
                }
            } catch (Throwable t) {
                LOG.warn("EngineForegroundService::ensureRelayStack failed", t);
            }
            if (done != null) {
                SystemUtils.postToUIThread(done);
            }
        });
    }

    private void stopRelayStack() {
        final AndroidRelayStack stack;
        synchronized (relayLock) {
            relayGeneration.invalidate();
            stack = relayStack;
            relayStack = null;
            if (stack != null) {
                stack.deactivate();
            }
        }
        if (stack != null) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, stack::close);
        }
    }

    private boolean mayParticipate(long generation) {
        return foregroundReady && relayGeneration.isCurrent(generation) && instance == this
                && !Engine.instance().wasShutdown() && AndroidRelayStack.isParticipationEnabled();
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

        if (EngineStatusNotificationStylePolicy.resolve(Build.VERSION.SDK_INT)
                == EngineStatusNotificationStylePolicy.Style.SIMPLE_NOTIFICATION) {
            return buildSimplePersistentNotification(showFrostWireIntent);
        }

        try {
            RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.view_permanent_status_notification);
            remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);
            remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

            Notification notification = new NotificationCompat.Builder(this, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.frostwire_notification_flat)
                    .setCustomContentView(remoteViews)
                    .setContentIntent(showFrostWireIntent)
                    .setOngoing(true)
                    .build();

            LOG.info("createPersistentNotification() created notification with RemoteViews successfully");
            return notification;
        } catch (Throwable e) {
            LOG.error("Failed to create notification with RemoteViews in EngineForegroundService, using fallback", e);
            return buildSimplePersistentNotification(showFrostWireIntent);
        }
    }

    private Notification buildSimplePersistentNotification(PendingIntent showFrostWireIntent) {
        return new NotificationCompat.Builder(this, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.frostwire_notification_flat)
                .setContentTitle("FrostWire")
                .setContentText("FrostWire is running")
                .setContentIntent(showFrostWireIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
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
        Engine.instance().shutdown();
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private static void cancelAllNotificationsTask(EngineForegroundService engineForegroundService) {
        try {
            NotificationManager notificationManager = (NotificationManager) engineForegroundService.getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                for (int notificationId : Constants.engineOwnedNotificationIds()) {
                    notificationManager.cancel(notificationId);
                }
            } else {
                LOG.warn("EngineForegroundService::cancelAllNotificationsTask(EngineForegroundService) notificationManager is null");
            }
        } catch (Throwable t) {
            LOG.warn("EngineForegroundService::cancelAllNotificationsTask(EngineForegroundService)" + t.getMessage(), t);
        }
    }

    private static void startPermanentNotificationUpdatesTask(EngineForegroundService engineForegroundService) {
        if (instance != engineForegroundService
                || !engineForegroundService.engineGeneration.isCurrent(engineForegroundService.engineGeneration.current())) {
            return;
        }
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
        final long generation = engineGeneration.current();
        if (!acceptsStarts() || !engineGeneration.isCurrent(generation) || Engine.instance().wasShutdown()
                || instance != this || !AndroidRelayStack.isNetworkAllowed()) {
            return;
        }
        // hard check for TOS
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            return;
        }

        if (!SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }

        if (isStarted()) {
            if (relayStack == null) {
                LOG.info("startServices() - engine up but IceBridge down, restarting relay stack");
                startRelayStack();
            } else {
                LOG.info("startServices() - aborting, it's already started", true);
            }
            return;
        }

        if (isStarting()) {
            LOG.info("startServices() - aborting, it's already starting", true);
            return;
        }

        LOG.info("startServices() - invoking resumeBTEngineTask, wasShutdown=" + wasShutdown);
        updateState(STATE_STARTING);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,
                () -> resumeBTEngineTask(this, wasShutdown, generation));
    }

    public synchronized void stopServices(boolean disconnected) {
        engineGeneration.retire();
        relayGeneration.retire();
        stopRelayStack();
        if (state == STATE_STOPPED || state == STATE_STOPPING) {
            LOG.info("EngineForegroundService::stopServices() - Already stopped or stopping");
            return;
        }
        updateState(STATE_STOPPING);
        LOG.info("EngineForegroundService::stopServices() - Pausing BTEngine");
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
            TransferManager.instance().onShutdown(disconnected);
            BTEngine.getInstance().pause();
            updateState(disconnected ? STATE_DISCONNECTED : STATE_STOPPED);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void notifyDownloadFinished(String displayName, File file, String infoHash) {
        try {
            NotifiedStorage storage = notifiedStorage;
            if (storage != null && storage.contains(infoHash)) {
                return;
            }
            if (storage != null) {
                storage.add(infoHash);
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
                    .setNumber(TransferManager.instance().getDownloadsToReview())
                    .setAutoCancel(true)
                    .build();
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
