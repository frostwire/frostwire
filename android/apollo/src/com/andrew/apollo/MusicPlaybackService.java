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

package com.andrew.apollo;

import static com.frostwire.android.util.RunStrict.runStrict;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.StaleDataException;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.AudioEffect;
import androidx.media3.session.MediaSession;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.AudioColumns;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.cache.ImageCache;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.HomeActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.TaskThrottle;
import com.frostwire.util.UrlUtils;
import com.google.android.gms.common.internal.Asserts;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A background {@link Service} used to keep music playing between activities
 * and when the user moves Apollo into the background.
 * <p>
 * TODO:
 * - assertInMusicPlayerHandlerThread()
 */
public class MusicPlaybackService extends Service {
    private final Object cursorLock = new Object();

    public static void safePost(Runnable runnable) {
        if (MusicPlaybackService.mPlayerHandler == null) {
            LOG.error("Check your logic, trying to safePost and mPlayerHandler hasn't yet been created");
            throw new RuntimeException("MusicPlaybackService.safePost can't post without a mPlayerHandler instance");
        }
        SystemUtils.exceptionSafePost(MusicPlaybackService.mPlayerHandler, runnable);
    }

    private static final Logger LOG = Logger.getLogger(MusicPlaybackService.class);
    private static final boolean D = BuildConfig.DEBUG;

    private static MusicPlaybackService INSTANCE = null;

    private static final CountDownLatch initLatch = new CountDownLatch(1);

    private static final LocalBinder binder = new LocalBinder();

    /**
     * Indicates that the music has paused or resumed
     */
    public static final String PLAYSTATE_CHANGED = "com.andrew.apollo.playstatechanged";

    /**
     * Indicates has been stopped
     */
    private static final String PLAYSTATE_STOPPED = "com.andrew.apollo.playstatestopped";

    /**
     * Indicates that music playback position within
     * a title was changed
     */
    public static final String POSITION_CHANGED = "com.android.apollo.positionchanged";

    /**
     * Indicates the meta data has changed in some way, like a track change
     */
    public static final String META_CHANGED = "com.andrew.apollo.metachanged";

    /**
     * Indicates the queue has been updated
     */
    public static final String QUEUE_CHANGED = "com.andrew.apollo.queuechanged";

    /**
     * Indicates the repeat mode changed
     */
    public static final String REPEATMODE_CHANGED = "com.andrew.apollo.repeatmodechanged";

    /**
     * Indicates the shuffle mode changed
     */
    public static final String SHUFFLEMODE_CHANGED = "com.andrew.apollo.shufflemodechanged";

    /**
     * For backwards compatibility reasons, also provide sticky
     * broadcasts under the music package
     */
    private static final String APOLLO_PACKAGE_NAME = "com.andrew.apollo";
    private static final String MUSIC_PACKAGE_NAME = "com.android.music";

    /**
     * Called to indicate a general service command. Used in
     * {@link MediaButtonIntentReceiver}
     */
    public static final String SERVICECMD = "com.andrew.apollo.musicservicecommand";

    /**
     * Called to go toggle between pausing and playing the music
     */
    static final String TOGGLEPAUSE_ACTION = "com.andrew.apollo.togglepause";

    /**
     * Called to go to pause the playback
     */
    private static final String PAUSE_ACTION = "com.andrew.apollo.pause";

    /**
     * Called to go to stop the playback
     */
    static final String STOP_ACTION = "com.andrew.apollo.stop";

    /**
     * Called to go to the previous track
     */
    public static final String PREVIOUS_ACTION = "com.andrew.apollo.previous";

    /**
     * Called to go to the next track
     */
    static final String NEXT_ACTION = "com.andrew.apollo.next";

    /**
     * Called to change the repeat mode
     */
    private static final String REPEAT_ACTION = "com.andrew.apollo.repeat";

    /**
     * Called to change the shuffle mode
     */
    private static final String SHUFFLE_ACTION = "com.andrew.apollo.shuffle";

    /**
     * Called to update the service about the foreground state of Apollo's activities
     */
    public static final String FOREGROUND_STATE_CHANGED = "com.andrew.apollo.fgstatechanged";

    public static final String NOW_IN_FOREGROUND = "nowinforeground";

    static final String FROM_MEDIA_BUTTON = "frommediabutton";

    /**
     * Used to easily notify a list that it should refresh. i.e. A playlist
     * changes
     */
    public static final String REFRESH = "com.andrew.apollo.refresh";

    /**
     * Used by the alarm intent to shutdown the service after being idle
     */
    public static final String SHUTDOWN_ACTION = "com.andrew.apollo.shutdown";

    /**
     * Simple player stopped playing the sound (completed or was stopped)
     */
    public static final String SIMPLE_PLAYSTATE_STOPPED = "com.andrew.apollo.simple.stopped";

    public static final String CMDNAME = "command";

    static final String CMDTOGGLEPAUSE = "togglepause";
    static final String CMDSTOP = "stop";
    static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    static final String CMDPREVIOUS = "previous";
    static final String CMDNEXT = "next";

    private static final int IDCOLIDX = 0;

    private static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    private static final int TRACK_ENDED = 1;
    private static final int TRACK_WENT_TO_NEXT = 2;
    private static final int RELEASE_WAKELOCK = 3;
    private static final int SERVER_DIED = 4;
    private static final int FOCUS_CHANGE = 5;
    private static final int FADE_DOWN = 6;
    private static final int FADE_UP = 7;
    private static final long REWIND_INSTEAD_PREVIOUS_THRESHOLD = 3000;

    private static final String AUDIO_ID_COLUMN_NAME = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ? "_id" : "audio._id AS _id";

    private static final String[] PROJECTION = new String[]{
            AUDIO_ID_COLUMN_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };

    // AUDIO_FOCUS_REQUEST removed — ExoPlayer in MultiPlayer handles audio focus internally.

    private static final String[] ALBUM_PROJECTION = new String[]{
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.LAST_YEAR
    };

    private static final String[] SIMPLE_PROJECTION = new String[]{
            "_id",
            MediaStore.Audio.Media.DATA
    };

    private static final ArrayDeque<Integer> mHistory = new ArrayDeque<>();
    private static final char[] HEX_DIGITS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'
    };

    private MultiPlayer mPlayer;
    // SimplePlayer wraps a lightweight ExoPlayer for single-file preview playback
    // (distinct from the queued MultiPlayer used for full music playback).
    private ExoPlayer mSimplePlayer;
    private String mSimplePlayerPlayingFile;

    // mWakeLock removed — ExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL) handles this internally.
    private Cursor mCursor;
    private Cursor mAlbumCursor;
    private volatile AudioManager mAudioManager;
    private SharedPreferences mPreferences;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQueueIsSaveable = true;
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean musicPlaybackActivityInForeground = false;
    private MediaSession mMediaSession;
    private ComponentName mMediaButtonReceiverComponent;
    private int mCardId;
    private volatile int mPlayListLen = 0;
    private volatile int mPlayPos = -1;
    private int mNextPlayPos = -1;
    private int mOpenFailedCounter = 0;
    private int mMediaMountedCount = 0;
    private boolean mShuffleEnabled = false;
    private int mRepeatMode = REPEAT_ALL;
    private int mServiceStartId = -1;
    private long[] mPlayList = null;
    private static volatile MusicPlayerHandler mPlayerHandler;
    private BroadcastReceiver mUnmountReceiver = null;
    private ImageFetcher mImageFetcher;
    private NotificationHelper mNotificationHelper;
    private RecentStore mRecentsCache;
    private FavoritesStore mFavoritesCache;
    private boolean launchPlayerActivity;
    private final Random r = new Random();

    private final static HashMap<String, Long> notifyChangeIntervals = new HashMap<>();

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // We handle command intents via onStartCommand
            // The old code tried to call onCreate and initService here, but onCreate is a lifecycle method of Service, no need to call it again.
            LOG.info("mIntentReceiver received intent, delegating to handleCommandIntent()");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                handleCommandIntent(intent);
            }
        }
    };

    private final MediaButtonIntentReceiver mMediaButtonReceiver = new MediaButtonIntentReceiver();

    // mAudioFocusListener removed — ExoPlayer in MultiPlayer handles audio focus internally.

    static {
        notifyChangeIntervals.put(QUEUE_CHANGED, 1000L);
        notifyChangeIntervals.put(META_CHANGED, 150L);
        notifyChangeIntervals.put(POSITION_CHANGED, 300L);
        notifyChangeIntervals.put(PLAYSTATE_CHANGED, 150L);
        notifyChangeIntervals.put(REPEATMODE_CHANGED, 150L);
        notifyChangeIntervals.put(SHUFFLEMODE_CHANGED, 150L);
        notifyChangeIntervals.put(REFRESH, 1000L);
    }

    private static CountDownLatch initServiceLatch = new CountDownLatch(1);
    private final AtomicBoolean serviceInitialized = new AtomicBoolean(false);

    private final Object  mediaButtonLock = new Object();

    private static MusicPlayerHandler setupMPlayerHandler() {
        if (mPlayerHandler != null) {
            try {
                mPlayerHandler.removeCallbacksAndMessages(null);
                HandlerThread oldThread = (HandlerThread) mPlayerHandler.getLooper().getThread();
                oldThread.quitSafely();
                mPlayerHandler = null;
            } catch (Throwable ignore) {
            }
        }

        final HandlerThread handlerThread = new HandlerThread(
                "MusicPlaybackService::MusicPlayerHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();

        mPlayerHandler = new MusicPlayerHandler(handlerThread.getLooper());
        return mPlayerHandler;
    }

    private static void initRepeatModeAndShuffleTask(MusicPlaybackService service) {
        ConfigurationManager CM = ConfigurationManager.instance();
        service.setRepeatMode(CM.getInt(Constants.PREF_KEY_GUI_PLAYER_REPEAT_MODE));
        service.enableShuffle(CM.getBoolean(Constants.PREF_KEY_GUI_PLAYER_SHUFFLE_ENABLED));
    }

    public static MusicPlaybackService getInstance() {
        return INSTANCE;
    }

    public static boolean instanceReady() {
        return INSTANCE != null && initServiceLatch.getCount() == 0;
    }

    private static CountDownLatch getInitLatch() {
        return initLatch;
    }

    /**
     * If we are on the UI thread, we go to a background service and wait for the
     * MusicPlaybackService instance latch to let us know we have an instance ready
     * then calls onCreate.
     */
    public static void onCreateSafe() {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                try {
                    MusicPlaybackService.getInitLatch().await();
                    assert MusicPlaybackService.getInstance() != null;
                    MusicPlaybackService.getInstance().onCreate();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            try {
                MusicPlaybackService.getInitLatch().await();
                assert MusicPlaybackService.getInstance() != null;
                MusicPlaybackService.getInstance().onCreate();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static MusicPlayerHandler getMusicPlayerHandler() {
        return MusicPlaybackService.mPlayerHandler;
    }

    private static class LocalBinder extends Binder {
        MusicPlaybackService getMusicPlaybackService() {
            return MusicPlaybackService.INSTANCE;
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        updateNotification();
        mServiceInUse = true;
        return binder;
    }

    @Override
    public void onRebind(final Intent intent) {
        INSTANCE = this;
        initServiceLatch.countDown();
        mServiceInUse = true;
    }

    public void onNotificationCreated(Notification notification) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (notification != null) {
                LOG.info("MusicPlaybackService::onNotificationCreated() invoking startForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)");
                try {
                    startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } catch (Throwable t) {
                    LOG.error("MusicPlaybackService:: error onNotificationCreated(SDK > Q) " + t.getMessage(), t);
                }
            } else {
                LOG.error("MusicPlaybackService::MusicPlaybackService:: error onNotificationCreated() received null notification");
            }
        } else {
            if (notification != null) {
                LOG.info("onNotificationCreated() invoking startForeground()");
                try {
                    startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification);
                } catch (Throwable t) {
                    LOG.error("error onNotificationCreated(SDK < Q) " + t.getMessage(), t);
                }
            } else {
                LOG.error("error onNotificationCreated() received null notification");
            }
        }
    }

    @Override
    public void onCreate() {
        INSTANCE = this;
        initServiceLatch.countDown();
        if (D) LOG.info("onCreate: Creating service");
        super.onCreate();
        // Audio focus is handled internally by ExoPlayer in MultiPlayer.
        String permission = SystemUtils.hasAndroid13OrNewer() ?
                Manifest.permission.READ_MEDIA_AUDIO :
                Manifest.permission.READ_EXTERNAL_STORAGE;
        boolean mediaReadPermissionsGranted = runStrict(() ->
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permission)
        );

        boolean postNotificationsPermissionGranted = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsPermissionGranted = runStrict(() ->
                    PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            );
        }

        if (!postNotificationsPermissionGranted && HomeActivity.instance() != null) {
            // See HomeActivity::onCreate() for the permission request
            // See HomeActivity::onRequestPermissionsResult() for the service initialization, which calls this again when the permission is granted
            LOG.info("MusicPlaybackService::onCreate() requesting post notifications permission from HomeActivity.instance().requestForPostNotificationPermission()");
            HomeActivity.instance().requestForPostNotificationsPermission();
        }

        if (mediaReadPermissionsGranted) {
            mNotificationHelper = new NotificationHelper(this);
            // let's send a dummy notification asap to not get shutdown for not sending startForeground in time
            if (postNotificationsPermissionGranted) {
                Notification tempNotification = mNotificationHelper.buildBasicNotification(
                        this,
                        "Loading...",
                        "Preparing music player.",
                        Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID);
                try {
                    startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, tempNotification);
                } catch (Throwable t) {
                    LOG.error("MusicPlaybackService::onCreate() error calling startForeground() " + t.getMessage(), t);
                }
            }

            mPlayerHandler = setupMPlayerHandler();
            SystemUtils.exceptionSafePost(mPlayerHandler, this::initService);
        } else {
            LOG.warn("onCreate: service couldn't be initialized correctly, " + permission + " not granted");
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        // For Android 14+ (API 34), check if we can legitimately start as foreground service
        boolean canStartForeground = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // On Android 14+, be more cautious about when we try to start foreground
            boolean isAppInForeground = SystemUtils.isAppInForeground(this);
            boolean isMediaButtonIntent = intent != null && intent.getBooleanExtra(FROM_MEDIA_BUTTON, false);
            boolean isServiceCommand = intent != null && SERVICECMD.equals(intent.getAction());
            
            // Allow foreground start if app is in foreground, or if this is a media button/command
            canStartForeground = isAppInForeground || isMediaButtonIntent || isServiceCommand;
            
            if (!canStartForeground) {
                LOG.warn("onStartCommand: Cannot start foreground service - app not in foreground and no media intent. Will skip temp notification.");
            }
        }
        
        if (canStartForeground) {
            NotificationUpdateDaemon.showTempNotification(this);
        }
        LOG.info("onStartCommand: Got new intent " + intent + ", startId = " + startId, true);
        mServiceStartId = startId;

        if (intent != null) {
            final String action = intent.getAction();

            if (intent.hasExtra(NOW_IN_FOREGROUND)) {
                musicPlaybackActivityInForeground = intent.getBooleanExtra(NOW_IN_FOREGROUND, false);
                updateNotification();
                // We'll start foreground in onNotificationCreated callback once notification is ready.
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                handleCommandIntent(intent);
            }

            if (SHUTDOWN_ACTION.equals(action)) {
                return START_NOT_STICKY;
            }
        }

        // completeWakefulIntent removed — no longer using WakefulBroadcastReceiver
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void handleCommandIntent(Intent intent) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> handleCommandIntent(intent));
            return;
        }

        SystemUtils.ensureBackgroundThreadOrCrash("MusicPlaybackService.handleCommandIntent");

        if (!serviceInitialized.get()) {
            SystemUtils.exceptionSafePost(mPlayerHandler, this::initService);
        }

        Asserts.checkNotNull(intent);
        LOG.info("MusicPlaybackService::handleCommandIntent waiting for initServiceLatch...", true);
        try {
            initServiceLatch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("handleCommandIntent aborted: " + e.getMessage(), e);
            return;
        }
        LOG.info("MusicPlaybackService::handleCommandIntent done waiting for initServiceLatch", true);

        final String action = intent.getAction();
        final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

        if (action == null) {
            LOG.info("MusicPlaybackService::handleCommandIntent: nothing to be done here, exiting.");
            return;
        }

        LOG.info("MusicPlaybackService::handleCommandIntent: action = " + action + ", command = " + command, true);

        if (SHUTDOWN_ACTION.equals(action)) {
            boolean exiting = intent.hasExtra("force");
            releaseServiceUiAndStop(exiting);
            return;
        }

        if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
            gotoNext(true);
        } else if (CMDPREVIOUS.equals(command) || PREVIOUS_ACTION.equals(action)) {
            gotoPrev();
        } else if (CMDTOGGLEPAUSE.equals(command) || TOGGLEPAUSE_ACTION.equals(action)) {
            if (isPlaying()) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else {
                play();
            }
        } else if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
        } else if (CMDPLAY.equals(command)) {
            play();
        } else if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            releaseServiceUiAndStop(false);
        } else if (REPEAT_ACTION.equals(action)) {
            cycleRepeat();
        }
    }

    @Override
    public void onDestroy() {
        LOG.info("onDestroy() destroying MusicPlaybackService");
        super.onDestroy();

        try {
            final Intent audioEffectsIntent = new Intent(
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
            audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            sendBroadcast(audioEffectsIntent);
        } catch (Throwable ignored) {
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.removeCallbacksAndMessages(null);
        }

        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }

        if (mSimplePlayer != null) {
            mSimplePlayer.release();
            mSimplePlayer = null;
        }

        if (mMediaSession != null) {
            try {
                mMediaSession.release();
            } catch (Throwable ignored) {}
            mMediaSession = null;
        }

        closeCursor();
        unregisterReceivers();

        // WakeLock release not needed — ExoPlayer handles it internally via WAKE_MODE_LOCAL.

        try {
            stopSelf();
            initServiceLatch = new CountDownLatch(1);
            serviceInitialized.set(false);
        } catch (Throwable ignored) {
        }
        INSTANCE = null;
        mServiceInUse = false;
    }

    public void updateNotification() {
        if (mNotificationHelper == null) {
            LOG.error("MusicPlaybackService::updateNotification() failed, mNotificationHelper == null");
            return;
        }

        if (isPlaying()) {
            if (TaskThrottle.isReadyToSubmitTask("MusicPlaybackService::updateNotificationTask", 1000)) {
                SystemUtils.exceptionSafePost(mPlayerHandler, () -> {
                    Bitmap albumArt = getAlbumArt();
                    SystemUtils.postToUIThread(() -> {
                        try {
                            buildNotificationWithAlbumArtPost(albumArt);
                        } catch (Throwable t) {
                            LOG.error("MusicPlaybackService::updateNotification() error " + t.getMessage(), t, true);
                        }
                    });
                });
            }
        }

        if (musicPlaybackActivityInForeground) {
            if (!isPlaying() && isStopped()) {
                updateRemoteControlClient(PLAYSTATE_STOPPED);
            } else if (!isPlaying() && !isStopped()) {
                updateRemoteControlClient(PLAYSTATE_CHANGED);
            }
        }
    }

    /**
     * Stops the player, not the foreground service
     */
    public void stopPlayer() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            LOG.info("stopPlayer()");
            mPlayer.stop();
            mIsSupposedToBePlaying = false;
        }
    }

    public void playSimple(String path) {
        String justStoppedFile = mSimplePlayerPlayingFile;
        if (mSimplePlayer != null) {
            stopSimplePlayer();
        }
        if (!path.equals(justStoppedFile)) {
            final String pathCopy = path;
            try {
                mSimplePlayer = new ExoPlayer.Builder(this).build();
                mSimplePlayer.setMediaItem(
                        MediaItem.fromUri(Uri.parse(path)));
                mSimplePlayer.prepare();
                mSimplePlayer.play();
                mSimplePlayerPlayingFile = path;
                mSimplePlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_ENDED) {
                            mSimplePlayerPlayingFile = null;
                            notifySimpleStopped(pathCopy);
                            if (mSimplePlayer != null) {
                                mSimplePlayer.release();
                                mSimplePlayer = null;
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                LOG.error("playSimple() error: " + t.getMessage(), t);
                if (mSimplePlayer != null) {
                    mSimplePlayer.release();
                    mSimplePlayer = null;
                }
                mSimplePlayerPlayingFile = null;
            }
        }
    }

    public void stopSimplePlayer() {
        if (mSimplePlayer != null) {
            try {
                mSimplePlayer.stop();
                mSimplePlayer.release();
            } catch (Throwable ignored) {}
            notifySimpleStopped(mSimplePlayerPlayingFile);
            mSimplePlayer = null;
            mSimplePlayerPlayingFile = null;
        }
    }

    // prepareAudioFocusRequest() removed — ExoPlayer in MultiPlayer handles audio focus internally.

    private void setUpMediaSession() {
        if (mPlayer == null || mPlayer.mExoPlayer == null) {
            LOG.warn("setUpMediaSession() called before ExoPlayer is ready — deferring");
            return;
        }
        try {
            if (mMediaSession != null) {
                mMediaSession.release();
            }
            mMediaSession = new MediaSession.Builder(this, mPlayer.mExoPlayer)
                    .setId("FrostWireApollo")
                    .build();
            // MediaSession automatically handles lock-screen controls, Bluetooth,
            // and media button routing when connected to an ExoPlayer instance.
            LOG.info("setUpMediaSession() MediaSession created successfully");
        } catch (Throwable t) {
            LOG.error("setUpMediaSession() error: " + t.getMessage(), t);
        }
    }

    private void releaseServiceUiAndStop(boolean force) {
        if (mPlayerHandler == null
                || (!force && isPlaying())
                || mPausedByTransientLossOfFocus
                || (mPlayerHandler.hasMessages(TRACK_ENDED))) {
            LOG.info("releaseServiceUiAndStop(force=" + force + ") aborted: isPlaying()=" + isPlaying());
            return;
        }

        if (force && isPlaying()) {
            LOG.info("releaseServiceUiAndStop(force=true) : isPlaying()=" + isPlaying());
            stopPlayer();
        }
        if (D) LOG.info("Nothing is playing anymore, releasing notification");
        if (mNotificationHelper != null) {
            mNotificationHelper.killNotification();
        }
        if (mAudioManager != null) {
            // Audio focus abandoned automatically by ExoPlayer on release().
        }
        updateRemoteControlClient(PLAYSTATE_STOPPED);
        if (!mServiceInUse || force) {
            unregisterReceivers();
            saveQueue(true);
            stopSelf(mServiceStartId);
            stop(false);
        }
        if (force) {
            mPreferences.edit().putInt("curpos", -1).apply();
        }
        if (mPlayerHandler != null) {
            mPlayerHandler.removeCallbacksAndMessages(null);
        }
    }

    private void unregisterReceivers() {
        try {
            unregisterReceiver(mIntentReceiver);
        } catch (Throwable ignored) {
        }

        if (mUnmountReceiver != null) {
            try {
                unregisterReceiver(mUnmountReceiver);
            } catch (Throwable ignored) {
            }
            mUnmountReceiver = null;
        }
    }

    private void buildNotificationWithAlbumArtPost(final Bitmap bitmap) {
        SystemUtils.ensureUIThreadOrCrash("MusicPlaybackService::buildNotificationWithAlbumArtPost");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mNotificationHelper.buildNotification(
                    getAlbumName(),
                    getArtistName(),
                    getTrackName(),
                    bitmap,
                    isPlaying());
        }
    }

    private void safeRegisterMediaButton(ComponentName comp) {
        synchronized (mediaButtonLock) {
            mAudioManager.registerMediaButtonEventReceiver(comp);
        }
    }

    private void initService() {
        if (mPlayerHandler == null) {
            throw new RuntimeException("check your logic, can't init service without mPlayerHandler.");
        }
        LOG.info("initService() invoked", true);

        mFavoritesCache = FavoritesStore.getInstance(this);
        mRecentsCache = RecentStore.getInstance(this);

        mImageFetcher = ImageFetcher.getInstance(this);
        mImageFetcher.setImageCache(ImageCache.getInstance(this));

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mMediaButtonReceiverComponent = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        try {
            if (mAudioManager != null) {
                safeRegisterMediaButton(mMediaButtonReceiverComponent);
            }
        } catch (SecurityException e) {
            LOG.error("initService() safeRegisterMediaButton error: " + e.getMessage(), e);
        }

        // MediaSession setup deferred until ExoPlayer is ready (called from setCurrentDataSource)
        // setUpMediaSession() is called after mPlayer is initialized in reloadQueue/openCurrentAndNext

        mPreferences = getSharedPreferences("Service", 0);
        mCardId = getCardId();

        registerExternalStorageListener();

        mPlayer = new MultiPlayer();
        MusicPlaybackService.initRepeatModeAndShuffleTask(this);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mIntentReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mIntentReceiver, filter);
            }
        } catch (Throwable t) {
            // this can happen if the service got destroyed and didn't have a chance to
            // register the receiver, current workarounds are extending receiver classes
            // to track if they've been registered, or using a static map<Receiver,boolean>
            // for now will just catch the exception and check the code on how this service
            // is getting destroyed and making sure the unregisterReceiver code is invoked.
            LOG.error("initService() registerReceiver error: " + t.getMessage(), t);
        }

        IntentFilter filterMediaButton = new IntentFilter();
        filterMediaButton.addAction(Intent.ACTION_MEDIA_BUTTON);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mMediaButtonReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mMediaButtonReceiver, filterMediaButton);
            }
        } catch (Throwable t) {
            // this can happen if the service got destroyed and didn't have a chance to
            // register the receiver, current workarounds are extending receiver classes
            // to track if they've been registered, or using a static map<Receiver,boolean>
            // for now will just catch the exception and check the code on how this service
            // is getting destroyed and making sure the unregisterReceiver code is invoked.
            LOG.error("initService() registerReceiver error: " + t.getMessage(), t);
        }

        // WakeLock initialization removed — ExoPlayer handles it via WAKE_MODE_LOCAL.

        reloadQueue();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);

        updateNotification();
        serviceInitialized.set(true);
        initServiceLatch.countDown();
    }

    /**
     * @return A card ID used to save and restore playlists, i.e., the queue.
     */
    private int getCardId() {
        int mCardId = -1;
        try {
            final ContentResolver resolver = getContentResolver();
            Cursor cursor = resolver.query(Uri.parse("content://media/external/fs_id"), null, null,
                    null, null);
            if (cursor != null && cursor.moveToFirst()) {
                mCardId = cursor.getInt(0);
                cursor.close();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            // it seems that content://media/external/fs_id is not accessible
            // from Android 6.0 in some phones or phone states (who knows)
            // this is an undocumented URI
        }
        return mCardId;
    }

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     */
    private void closeExternalStorageFiles() {
        stop(true);
        MusicUtils.requestMusicPlaybackServiceShutdown(this);
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The
     * intent will call closeExternalStorageFiles() if the external media is
     * going to be ejected, so applications can clean up any files they have
     * open.
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    final String action = intent.getAction();
                    if (action != null) {
                        if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                            saveQueue(true);
                            mQueueIsSaveable = false;
                            closeExternalStorageFiles();
                        } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                            mMediaMountedCount++;
                            mCardId = getCardId();
                            reloadQueue();
                            mQueueIsSaveable = true;
                            notifyChange(QUEUE_CHANGED);
                            notifyChange(META_CHANGED);
                        }
                    }
                }
            };
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addDataScheme("file");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(mUnmountReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(mUnmountReceiver, filter);
                }
            } catch (Throwable notABiggie) {
                LOG.error("registerExternalStorageListener() could not register ACTION_MEDIA_EJECT|MOUNTED intent filter", notABiggie);
            }
        }
    }

    /**
     * Stops playback and the service if removeNotification=true
     */
    private void stop(final boolean removeNotification) {
        if (D) LOG.info("Stopping playback, removeNotification = " + removeNotification);
        stopPlayer();
        closeCursor();
        boolean isStopped = isStopped();
        if (removeNotification) {
            updateRemoteControlClient(PLAYSTATE_STOPPED);
            stopForeground(true);

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP ||
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
                stopService(new Intent(this, MusicPlaybackService.class));
            }
        } else {
            stopForeground(isStopped);
        }
    }

    /**
     * Removes the range of tracks specified from the play list. If a file
     * within the range is the file currently being played, playback will move
     * to the next file after the range.
     *
     * @param first The first file to be removed
     * @param last  The last file to be removed
     * @return the number of tracks deleted
     */
    private int removeTracksInternal(int first, int last) {
        if (last < first) {
            return 0;
        } else if (first < 0) {
            first = 0;
        } else if (last >= mPlayListLen) {
            last = mPlayListLen - 1;
        }

        boolean gotonext = false;
        if (first <= mPlayPos && mPlayPos <= last) {
            mPlayPos = first;
            gotonext = true;
        } else if (mPlayPos > last) {
            mPlayPos -= last - first + 1;
        }
        final int num = mPlayListLen - last - 1;
        for (int i = 0; i < num; i++) {
            mPlayList[first + i] = mPlayList[last + 1 + i];
        }
        mPlayListLen -= last - first + 1;

        if (mPlayListLen < 0) {
            mPlayListLen = 0;
        }

        if (gotonext) {
            if (mPlayListLen == 0) {
                mPlayPos = -1;
                stop(true);
                MusicUtils.requestMusicPlaybackServiceShutdown(this);
                return 0;
            } else {
                if (mShuffleEnabled) {
                    mPlayPos = getNextPosition(true, isShuffleEnabled());
                } else if (mPlayPos >= mPlayListLen) {
                    mPlayPos = 0;
                }
                final boolean wasPlaying = isPlaying();
                stop(false);
                if (openCurrentAndNext()) {
                    if (wasPlaying) {
                        play();
                    }
                }
            }
            notifyChange(META_CHANGED);
        }
        return last - first + 1;
    }

    /**
     * Adds a list to the playlist
     *
     * @param list     The list to add
     * @param position The position to place the tracks
     */
    private void addToPlayList(final long[] list, int position) {
        final int addlen = list.length;
        if (position < 0) {
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }

        if (mPlayList != null && mPlayList.length > 0) {
            final int tailsize = mPlayListLen - position;
            for (int i = tailsize; i > 0; i--) {
                if (checkBounds(position + i, mPlayList.length) &&
                        checkBounds(position + i - addlen, mPlayList.length)) {
                    mPlayList[position + i] = mPlayList[position + i - addlen];
                }
            }
            for (int i = 0; i < addlen; i++) {
                if (checkBounds(position + i, mPlayList.length) &&
                        checkBounds(i, list.length)) {
                    mPlayList[position + i] = list[i];
                }
            }
            mPlayListLen += addlen;
        }

        if (mPlayListLen == 0) {
            closeCursor();
            notifyChange(META_CHANGED);
        }
    }

    private boolean checkBounds(int i, int arrayLen) {
        return i >= 0 && i < arrayLen;
    }

    /**
     * @param trackId The track ID
     */
    private void updateCursor(final long trackId) {
        updateCursor("_id=" + trackId, null);
    }

    private void updateCursor(final String selection, final String[] selectionArgs) {
        closeCursor();
        mCursor = openCursorAndGoToFirst(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                PROJECTION, selection, selectionArgs);
        updateAlbumCursor();
    }

    private void updateCursor(final Uri uri) {
        closeCursor();

        Cursor cursor = openCursorAndGoToFirst(
                uri,
                PROJECTION,
                null,
                null);

        synchronized (cursorLock) {
            mCursor = cursor;
        }
        updateAlbumCursor();
    }

    private void updateAlbumCursor() {
        long albumId = getAlbumId();
        if (albumId >= 0) {
            mAlbumCursor = openCursorAndGoToFirst(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    ALBUM_PROJECTION,
                    "_id=" + albumId,
                    null);
        } else {
            mAlbumCursor = null;
        }
    }

    private Cursor openCursorAndGoToFirst(Uri uri,
                                          String[] projection,
                                          String selection,
                                          String[] selectionArgs) {
        Cursor c;
        try {
            c = getContentResolver().query(uri, projection, selection, selectionArgs, null);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
        if (c == null) {
            return null;
        }
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        return c;
    }

    private void closeCursor() {
        if (mCursor != null) {
            try {
                mCursor.close();
            } catch (Throwable ignored) {
            }
            synchronized (cursorLock) {
                mCursor = null;
            }
        }
        if (mAlbumCursor != null) {
            try {
                mAlbumCursor.close();
            } catch (Throwable ignored) {
            }
            mAlbumCursor = null;
        }
    }

    private boolean openCurrentAndNext() {
        return openCurrentAndMaybeNext(true);
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     *
     * @param openNext True to prepare the next track for playback, false
     *                 otherwise.
     */
    private boolean openCurrentAndMaybeNext(final boolean openNext) {
        closeCursor();
        if (mPlayListLen == 0 || mPlayList == null) {
            return false;
        }
        stopPlayer();

        long trackId;
        synchronized (mPlayList) {
            mPlayPos = Math.min(mPlayPos, mPlayList.length - 1);
            try {
                trackId = mPlayList[mPlayPos];
            } catch (ArrayIndexOutOfBoundsException t) {
                LOG.warn("Aborting openCurrentAndMaybeNext(openNext=" + openNext + ")");
                LOG.error(t.getMessage(), t);
                return false;
            }
        }
        updateCursor(trackId);

        boolean hasOpenCursor = mCursor != null && !mCursor.isClosed();
        if (!hasOpenCursor) {
            if (openNext) {
                setNextTrack();
            }
        } else {
            long fileId = mCursor.getLong(IDCOLIDX);
            String contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString();
            if (SystemUtils.hasAndroid10OrNewer()) {
                contentUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).toString();
            }
            final String path = String.format("%s/%d", contentUri, fileId);
            LOG.info("openCurrentAndMaybeNext(openNext=" + openNext + ") path=" + path);
            if (openFile(path) > 0) {
                if (openNext) {
                    setNextTrack();
                }
                return true;
            } else {
                // on !openFile(path)
                // if we get here then opening the file failed. We can close the
                // cursor now, because
                // we're either going to create a new one next, or stop trying
                closeCursor();
                if (mPlayListLen > 1 && mOpenFailedCounter < 10) {
                    final int pos = getNextPosition(false, isShuffleEnabled());

                    if (invalidPosition(pos)) return false;

                    mPlayPos = pos;
                    stopPlayer();
                    mPlayPos = pos;
                    updateCursor(mPlayList[mPlayPos]);
                    hasOpenCursor = mCursor != null && !mCursor.isClosed();
                    if (!hasOpenCursor) {
                        if (openNext) {
                            setNextTrack();
                        }
                    } else {
                        if (openFile(path) > 0) {
                            // should do something on positive?
                            // there used to be some sort of recursive callback
                            LOG.info("openCurrentAndMaybeNext(): openFile(path=" + path + ") succeeded");
                        } else {
                            // do something on negative?
                            LOG.error("openCurrentAndMaybeNext(): openFile(path=" + path + ") failed!");
                        }
                    }
                } else {
                    mOpenFailedCounter = 0;
                    LOG.warn("Failed to open file for playback");
                    if (mIsSupposedToBePlaying) {
                        mIsSupposedToBePlaying = false;
                        notifyChange(PLAYSTATE_CHANGED);
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param force True to force the player onto the track next, false
     *              otherwise.
     * @return The next position to play.
     */
    private int getNextPosition(final boolean force, final boolean shuffleEnabled) {
        if (!force && mRepeatMode == REPEAT_CURRENT) {
            return Math.max(mPlayPos, 0);
        } else if (shuffleEnabled) {
            if (mPlayListLen <= 0) {
                return -1;
            }
            return r.nextInt(mPlayListLen);
        } else {
            if (mPlayPos >= mPlayListLen - 1) {
                if (mRepeatMode == REPEAT_NONE && !force) {
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    /**
     * Sets the track track to be played
     */
    private void setNextTrack() {
        mNextPlayPos = getNextPosition(false, isShuffleEnabled());
        if (D) LOG.info("setNextTrack: next play position = " + mNextPlayPos);

        if (mPlayer != null) {
            if (mNextPlayPos >= 0 && mPlayList != null && mPlayList.length > 0 && mPlayPos < mPlayList.length) {
                final long id = mPlayList[mNextPlayPos];
                mPlayer.setNextDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
            } else {
                mPlayer.setNextDataSource(null);
            }
        } else {
            LOG.warn("setNextTrack() -> no mPlayer instance available.");
        }
    }

    /**
     * Makes sure the playlist has enough space to hold all of the songs
     *
     * @param size The size of the playlist
     */
    private void ensurePlayListCapacity(final int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            final int len = mPlayList != null ? mPlayList.length : mPlayListLen;
            long[] newlist = new long[size * 2];

            if (mPlayList != null) {
                System.arraycopy(mPlayList, 0, newlist, 0, len);
            }
            mPlayList = newlist;
        }
    }

    /**
     * Notify the change-receivers that something has changed.
     */
    private void notifyChange(final String change) {
        LOG.info("notifyChange(" + change + ") trying...");
        Runnable notifyChangeTaskRunnable = () -> MusicPlaybackService.notifyChangeTask(this, change);
        long interval = 250;
        try {
            interval = notifyChangeIntervals.get(change);
        } catch (Throwable t) {
            LOG.error("notifyChange() change=" + change + " interval not defined in notifyChangeIntervals, defaulting to 250ms", t);
        }
        if (TaskThrottle.isReadyToSubmitTask(change, interval)) {
            SystemUtils.exceptionSafePost(mPlayerHandler, notifyChangeTaskRunnable);
        }
    }


    private static void notifyChangeTask(MusicPlaybackService musicPlaybackService, String change) {
        LOG.info("notifyChangeTask(" + change + ")!");
        // Update the lock screen controls
        musicPlaybackService.updateRemoteControlClient(change);
        if (POSITION_CHANGED.equals(change) && musicPlaybackService.position() != 0) {
            return;
        }
        if (SHUFFLEMODE_CHANGED.equals(change)) {
            return;
        }
        if (REPEATMODE_CHANGED.equals(change)) {
            return;
        }
        final Intent intent = new Intent(change);
        long audioId = musicPlaybackService.getAudioId();
        String artistName = musicPlaybackService.getArtistName();
        String albumName = musicPlaybackService.getAlbumName();
        String trackName = musicPlaybackService.getTrackName();
        boolean isPlaying = musicPlaybackService.isPlaying();
        boolean isStopped = musicPlaybackService.isStopped();
        boolean favorite = musicPlaybackService.isFavorite();
        intent.putExtra("id", audioId);
        intent.putExtra("artist", artistName);
        intent.putExtra("album", albumName);
        intent.putExtra("track", trackName);
        intent.putExtra("playing", isPlaying);
        intent.putExtra("isfavorite", favorite);
        musicPlaybackService.sendStickyBroadcast(intent);
        final Intent musicIntent = new Intent(intent);
        musicIntent.setAction(change.replace(APOLLO_PACKAGE_NAME, MUSIC_PACKAGE_NAME));
        musicPlaybackService.sendStickyBroadcast(musicIntent);
        if (META_CHANGED.equals(change)) {
            // Increase the play count for favorite songs.
            if (musicPlaybackService.mFavoritesCache != null && musicPlaybackService.mFavoritesCache.getSongId(audioId) != null) {
                musicPlaybackService.mFavoritesCache.addSongId(audioId, trackName, albumName, artistName);
            }
            // Add the track to the recently played list.
            recentsStoreAddSongIdTask(musicPlaybackService);
        } else if (QUEUE_CHANGED.equals(change)) {
            musicPlaybackService.saveQueue(true);
            if (isPlaying) {
                musicPlaybackService.setNextTrack();
            }
        } else {
            musicPlaybackService.saveQueue(false);
        }

        // PLAYSTATE_CHANGED = PLAYING or PAUSED (not stopped)
        if (PLAYSTATE_CHANGED.equals(change) && musicPlaybackService.mNotificationHelper != null) {
            musicPlaybackService.mNotificationHelper.updatePlayState(isPlaying, isStopped);
        }
    }

    private static void recentsStoreAddSongIdTask(MusicPlaybackService musicPlaybackService) {
        if (musicPlaybackService.mRecentsCache == null) {
            musicPlaybackService.mRecentsCache = RecentStore.getInstance(musicPlaybackService);
        }
        long songId = musicPlaybackService.getAudioId();
        String songName = musicPlaybackService.getTrackName();
        String artistName = musicPlaybackService.getArtistName();
        String albumName = musicPlaybackService.getAlbumName();
        long duration = musicPlaybackService.duration();
        if (musicPlaybackService.mRecentsCache != null) {
            musicPlaybackService.mRecentsCache.addSongId(songId, songName, artistName,
                    albumName, duration, musicPlaybackService.getString(R.string.unknown));
        }
    }

    /**
     * Notify the change-receivers that simple player has stopped
     */
    private void notifySimpleStopped(final String path) {
        if (D) LOG.info("notifySimplePlayerStopped");

        final Intent intent = new Intent(SIMPLE_PLAYSTATE_STOPPED);
        intent.putExtra("path", path);
        sendStickyBroadcast(intent);
    }

    /**
     * Updates the MediaSession and notification with current playback state and metadata.
     * Replaces the old RemoteControlClient update path.
     *
     * @param what The broadcast event that triggered the update (e.g. PLAYSTATE_CHANGED, META_CHANGED)
     */
    void updateRemoteControlClient(final String what) {
        if (what == null) {
            LOG.info("updateRemoteControlClient() skipped — what is null");
            return;
        }
        LOG.info("updateRemoteControlClient(what=" + what + ")");

        final boolean isPlaying = isPlaying();
        final boolean isStopped = isStopped();

        if (isStopped && !isPlaying && mNotificationHelper != null) {
            mNotificationHelper.killNotification();
        }

        switch (what) {
            case PLAYSTATE_CHANGED:
            case POSITION_CHANGED:
            case PLAYSTATE_STOPPED:
                if (mNotificationHelper != null) {
                    try {
                        mNotificationHelper.updatePlayState(isPlaying, isStopped);
                    } catch (Throwable ignored) {}
                }
                updateMediaSessionPlaybackState();
                break;
            case META_CHANGED:
            case QUEUE_CHANGED:
                // Asynchronously fetch album art then push full metadata to MediaSession
                SystemUtils.exceptionSafePost(mPlayerHandler, this::updateMediaSessionMetadata);
                break;
        }
    }

    /** Pushes the current playback state into the MediaSession. */
    private void updateMediaSessionPlaybackState() {
        if (mMediaSession == null) return;
        // MediaSession backed by ExoPlayer — state is automatically reflected.
        // No manual PlaybackStateCompat update needed when using media3 MediaSession.
    }

    /**
     * Pushes current track metadata into the MediaSession via a refreshed MediaItem.
     * Media3's MediaSession reads metadata directly from ExoPlayer's current MediaItem —
     * the system lock screen and Bluetooth devices receive it automatically.
     */
    private void updateMediaSessionMetadata() {
        if (mMediaSession == null || mPlayer == null || mPlayer.mExoPlayer == null) return;
        try {
            // Rebuild the current MediaItem with updated display metadata.
            // ExoPlayer propagates this to MediaSession automatically.
            String path = getPath();
            if (path == null) return;
            androidx.media3.common.MediaMetadata meta =
                    new androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(getTrackName())
                            .setArtist(getArtistName())
                            .setAlbumTitle(getAlbumName())
                            .setAlbumArtist(getAlbumArtistName())
                            .build();
            MediaItem updatedItem = new MediaItem.Builder()
                    .setUri(MultiPlayer.resolveUri(path))
                    .setMediaMetadata(meta)
                    .build();
            // Replace in-place without interrupting playback
            mPlayer.mExoPlayer.replaceMediaItem(
                    mPlayer.mExoPlayer.getCurrentMediaItemIndex(), updatedItem);
            LOG.info("updateMediaSessionMetadata() pushed metadata for: " + getTrackName());
        } catch (Throwable t) {
            LOG.error("updateMediaSessionMetadata() error: " + t.getMessage(), t);
        }
    }

    /**
     * Saves the queue
     *
     * @param full True if the queue is full
     */
    private void saveQueue(final boolean full) {
        if (!mQueueIsSaveable || mPreferences == null) {
            return;
        }

        final SharedPreferences.Editor editor = mPreferences.edit();
        if (full) {
            final StringBuilder q = new StringBuilder();
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                long n = mPlayList[i];
                if (n == 0) {
                    q.append("0;");
                } else {
                    while (n != 0) {
                        final int digit = (int) (n & 0xf);
                        n >>>= 4;
                        q.append(HEX_DIGITS[digit]);
                    }
                    q.append(";");
                }
            }
            editor.putString("queue", q.toString());
            editor.putInt("cardid", mCardId);
        }
        editor.putInt("curpos", mPlayPos);
        if (mPlayer != null && mPlayer.isInitialized()) {
            try {
                final long pos = mPlayer.position();
                editor.putLong("seekpos", pos);
            } catch (Throwable e) {
                // usually an IllegalStateException coming
                // from com.andrew.apollo.MusicPlaybackService$MultiPlayer.position
                // which comes from a native call to MediaPlayer.getCurrentPosition()
            }
        }
        editor.putInt("repeatmode", mRepeatMode);
        editor.putBoolean("shufflemode", mShuffleEnabled);
        editor.apply();
    }

    /**
     * Reloads the queue as the user left it the last time they stopped using
     * Apollo
     */
    private void reloadQueue() {
        String q = null;
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            q = mPreferences.getString("queue", "");
        }
        int qlen = q != null ? q.length() : 0;
        if (qlen > 1) {
            int plen = 0;
            int n = 0;
            int shift = 0;
            for (int i = 0; i < qlen; i++) {
                final char c = q.charAt(i);
                if (c == ';') {
                    ensurePlayListCapacity(plen + 1);
                    mPlayList[plen] = n;
                    plen++;
                    n = 0;
                    shift = 0;
                } else {
                    if (c >= '0' && c <= '9') {
                        n += c - '0' << shift;
                    } else if (c >= 'a' && c <= 'f') {
                        n += 10 + c - 'a' << shift;
                    } else {
                        plen = 0;
                        break;
                    }
                    shift += 4;
                }
            }
            mPlayListLen = plen;
            final int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= mPlayListLen) {
                mPlayListLen = 0;
                return;
            }
            mPlayPos = pos;
            updateCursor(mPlayList[mPlayPos]);
            if (mCursor == null) {
                SystemClock.sleep(3000);
                try {
                    // TODO: well, this is garbage, since
                    // there is a 3 seconds sleep, all sort
                    // of things could happen to the mutable
                    // variable mPlayPos, including set it to -1
                    // this need to be recoded
                    updateCursor(mPlayList[mPlayPos]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // ignore and return
                    return;
                }
            }
            closeCursor();
            mOpenFailedCounter = 20;
            openCurrentAndNext();

            if (mPlayer == null || !mPlayer.isInitialized()) {
                mPlayListLen = 0;
                return;
            }

            final long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

            if (D) {
                LOG.info("restored queue, currently at position "
                        + position() + "/" + duration()
                        + " (requested " + seekpos + ")");
            }

            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;
        }
    }

    interface OpenFileResultCallback {
        void openFileResult(boolean result);
    }

    long getIdFromContextUri(String path) {
        Uri uri = Uri.parse(path);
        long id = -1;
        try {
            id = Long.parseLong(uri.getLastPathSegment().replace("msf:", ""));
        } catch (NumberFormatException ex) {
            // Ignore
        }
        return id;
    }

    /**
     * Opens a file and prepares it for playback
     *
     * @param path The path of the file to open
     */
    public long openFile(String path) {
        if (D) LOG.info("MusicPlaybackService::openFile: path = " + path);
        if (path == null) {
            return -1;
        }

        long id = -1;

        // content://com.frostwire.android.fileprovider/external/Download/FrostWire/...audiofile.ext
        if (path.startsWith("content://com.frostwire.android.fileprovider/external")) {
            path = path.replace("content://com.frostwire.android.fileprovider/external", "/storage/emulated/0");
        }

        if (path.startsWith("content://com.android.providers.downloads.documents/document/raw")) {
            try {
                id = MusicUtils.getFileIdFromComAndroidProvidersDownloadsDocumentsPath(this, path);
            } catch (Throwable ignored) {
            }
        }
        if (id == -1 && path.startsWith("content://media/external_primary/audio/media")) {
            try {
                id = Long.parseLong(Uri.parse(path).getLastPathSegment());
            } catch (Throwable ignored) {

            }
        }

        // Google "Files"
        if (id == -1 && path.startsWith("content://com.google.android.apps.nbu.files.provider")) {
            try {
                id = Long.parseLong(Uri.parse(path).getLastPathSegment());
            } catch (Throwable ignored) {

            }
        }

        if (id == -1 && path.contains("msf")) {
            id = getIdFromContextUri(path);
        }

        // Explorers that pass the file path like
        // OnePlus File Explorer (content://com.oneplus.filemanager/.../storage/emulated/...)
        // Paths can be /root/storage/ or /storage
        if (id == -1 && path.startsWith("content://") && path.contains("/storage/emulated/0/")) {
            //debugPrintAllFilesInDownloads();
            String prefix = path.substring(0, path.indexOf("/storage"));
            LOG.info("MusicPlaybackService::openFile prefix=" + prefix, true);
            String fixedPath = path.replace(prefix, "");
            try {
                fixedPath = URLDecoder.decode(fixedPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error("MusicPlaybackService::openFile decoding error " + e.getMessage(), e, true);
            }
            LOG.info("MusicPlaybackService::openFile - updated path -> " + fixedPath);
            id = getFileIdFromPath(fixedPath, Uri.parse("content://media/external/downloads"), false);
            if (id == -1) {
                id = getFileIdFromPath(fixedPath, Uri.parse("content://media/internal/downloads"), false);
                if (id != -1) {
                    LOG.info("MusicPlaybackService::openFile got an ID from media internal URI, id = " + id);
                }
            }
            LOG.info("MusicPlaybackService::openFile got an ID? id = " + id);
        }

        // Are we talking about a regular file path?
        if (path.startsWith("/storage") || path.startsWith("content://")) {
            if (path.startsWith("/storage")) {
                path = UrlUtils.decode(path);
                LOG.info("MusicPlaybackService.openFile decoded path -> " + path);
            }
            mPlayer.setCurrentDataSource(path);
            ensurePlayListCapacity(1);
            if (mPlayer != null && mPlayer.isInitialized()) {
                mOpenFailedCounter = 0;
            } else {
                mOpenFailedCounter++;
                stopPlayer();
            }
            return id;
        }
        // If mCursor is null, try to associate path with a database cursor
        else if (mCursor == null) {
            if (id != -1 && path.startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())) {
                Uri uri = Uri.parse(path);
                updateCursor(uri);
            } else if (id != -1 && path.startsWith(MediaStore.Files.getContentUri("external").toString())) {
                updateCursor(id);
            } else {
                String where = MediaStore.Audio.Media.DATA + "=?";
                String[] selectionArgs = new String[]{path};
                updateCursor(where, selectionArgs);
            }
            try {
                if (mCursor != null) {
                    ensurePlayListCapacity(1);
                    mPlayListLen = 1;
                    mPlayList[0] = mCursor.getLong(IDCOLIDX);
                    mPlayPos = 0;
                }
            } catch (UnsupportedOperationException e) {
                LOG.error("Error while opening file for play", e);
                return -1;
            } catch (StaleDataException | IllegalStateException e) {
                LOG.error("Error with database cursor while opening file for play", e);
                return -1;
            }
        }

        if (mPlayerHandler == null) {
            throw new RuntimeException("check your logic, mPlayerHandler can't be null");
        }
        if (mPlayer == null) {
        mPlayer = new MultiPlayer();

        }
        mPlayer.setCurrentDataSource(path);
        if (mPlayer != null && mPlayer.isInitialized()) {
            mOpenFailedCounter = 0;
            return id;
        } else {
            mOpenFailedCounter++;
            stopPlayer();
            return id;
        }
    }

    /**
     * Returns the audio session ID
     *
     * @return The current media player audio session ID
     */
    public int getAudioSessionId() {
        if (mPlayer == null) {
            return -1;
        }
        return mPlayer.getAudioSessionId();
    }

    /**
     * Indicates if the media storage device has been mounted or not
     *
     * @return 1 if Intent.ACTION_MEDIA_MOUNTED is called, 0 otherwise
     */
    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    /**
     * Returns the shuffle mode
     *
     * @return The current shuffle mode (all, party, none)
     */
    public boolean isShuffleEnabled() {
        return mShuffleEnabled;
    }

    /**
     * Returns the repeat mode
     *
     * @return The current repeat mode (all, one, none)
     */
    public int getRepeatMode() {
        return mRepeatMode;
    }

    /**
     * Removes all instances of the track with the given ID from the playlist.
     *
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    public int removeTrack(final long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlayListLen; i++) {
                if (mPlayList[i] == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    /**
     * Removes the range of tracks specified from the play list. If a file
     * within the range is the file currently being played, playback will move
     * to the next file after the range.
     *
     * @param first The first file to be removed
     * @param last  The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(final int first, final int last) {
        final int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    /**
     * Returns the position in the queue
     *
     * @return the current position in the queue
     */
    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    /**
     * Returns the path to current song
     *
     * @return The path to the current song
     */
    public String getPath() {
        synchronized (this) {
            if (mCursor == null || mCursor.isClosed()) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.DATA));
        }
    }

    /**
     * Returns the album name
     *
     * @return The current song album Name
     */
    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null || mCursor.isClosed()) {
                return null;
            }
            try {
                return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM));
            } catch (Throwable e) {
                e.printStackTrace();
                return "---";
            }
        }
    }

    /**
     * Returns the song name
     *
     * @return The current song name
     */
    public String getTrackName() {
        //synchronized (cursorLock) {
        if (mCursor == null || mCursor.isClosed()) {
            return null;
        }
        try {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
        } catch (Throwable e) {
            e.printStackTrace();
            return "---";
        }
        //}
    }

    /**
     * Returns the artist name
     *
     * @return The current song artist name
     */
    public String getArtistName() {
        try {
            //synchronized (cursorLock) {
            if (mCursor == null || mCursor.isClosed()) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
            //}
        } catch (Throwable e) {
            // what else
            return "";
        }
    }

    /**
     * Returns the artist name
     *
     * @return The current song artist name
     */
    private String getAlbumArtistName() {
        try {
            //synchronized (cursorLock) {
            if (mAlbumCursor == null || mAlbumCursor.isClosed()) {
                return null;
            }
            return mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(AlbumColumns.ARTIST));
            //}
        } catch (Throwable e) {
            // avoid crash due to IllegalStateException, or any other exception
            return "";
        }
    }

    /**
     * Returns the album ID
     *
     * @return The current song album ID
     */
    public long getAlbumId() {
//        synchronized (cursorLock) {
        try {
            if (mCursor == null || mCursor.isClosed()) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM_ID));
        } catch (IllegalStateException | StaleDataException e) {
            // this is what happens when a cursor is stored as a state
            LOG.error("Error using db cursor to get album id", e);
            synchronized (cursorLock) {
                mCursor = null;
            }
            return -1;
        }
//        }
    }

    /**
     * Returns the artist ID
     *
     * @return The current song artist ID
     */
    public long getArtistId() {
        //synchronized (cursorLock) {
        if (mCursor == null || mCursor.isClosed()) {
            return -1;
        }
        return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST_ID));
        //}
    }

    /**
     * Returns the current audio ID
     *
     * @return The current track ID
     */
    public long getAudioId() {
        if (mPlayList != null &&
                mPlayPos >= 0 &&
                mPlayPos < mPlayList.length &&
                mPlayer != null && mPlayer.isInitialized()) {
            try {
                return mPlayList[mPlayPos];
            } catch (IndexOutOfBoundsException ioob) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Returns the current audio for simple player ID
     *
     * @return The current simple player track ID
     */
    public long getCurrentSimplePlayerAudioId() {
        //synchronized (cursorLock) {
        long id = -1;
        if (mSimplePlayerPlayingFile != null) {
            id = getFileIdFromPath(mSimplePlayerPlayingFile, MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
            if (id == -1) {
                id = getFileIdFromPath(mSimplePlayerPlayingFile, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
            }
        }
        return id;
        //}
    }

    /**
     * Uses exact data path match when querying the media store db.
     * See getFileIdFromPath to use LIKE %<my path>% by passing exact=false parameter.
     */
    private long getFileIdFromPath(String path, Uri uri) {
        return getFileIdFromPath(path, uri, true);
    }

    /**
     * Returns the current id of file at given path in Selected uri or -1 if not found
     *
     * @return File id
     */
    private long getFileIdFromPath(String path, Uri uri, boolean exact) {
        String selectionClause = MediaStore.Audio.Media.DATA + (exact ? " = ?" : " LIKE ?");
        String[] selectionArgs = {(exact) ? path : "%" + path + "%"};
        Cursor cursor = getContentResolver().query(uri, SIMPLE_PROJECTION, selectionClause, selectionArgs, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToNext();
            long id = cursor.getLong(IDCOLIDX);
            cursor.close();
            return id;
        }
        return -1;
    }

    @SuppressWarnings("unused")
/** Use this to print all the _id and _data of the files in Downloads as stored by the Media Store
 * Useful for debugging */
    private void debugPrintAllFilesInDownloads() {
        ContentResolver contentResolver = getContentResolver();
        Uri downloadsExternalUri = Uri.parse("content://media/external/downloads");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadsExternalUri = MediaStore.Downloads.getContentUri("external");
        }
        Cursor query = contentResolver.query(downloadsExternalUri,
                SIMPLE_PROJECTION,
                "_data LIKE ?",
                new String[]{"%/Download/%"},
                null, null);
        if (query != null && query.moveToFirst()) {
            do {
                LOG.info("MusicPlaybackService::debugPrintAllFilesInDownloads _id: " + query.getLong(0) + " _data:" + query.getString(1));
            } while (query.moveToNext());
            query.close();
        }
    }

    /**
     * Seeks the current track to a specific time
     *
     * @param position The time to seek to
     * @return The time to play the track at
     */
    public long seek(long position) {
        if (mPlayer != null && mPlayer.isInitialized()) {
            if (position < 0) {
                position = 0;
            } else if (position > mPlayer.duration()) {
                position = mPlayer.duration();
            }
            long result = mPlayer.seek(position);
            notifyChange(POSITION_CHANGED);
            return result;
        }
        return -1;
    }

    /**
     * Returns the current position in time of the current track
     *
     * @return The current playback position in milliseconds
     */
    public long position() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Expensive method, do not use in main thread.
     * <p>
     * Returns the full duration of the current track
     *
     * @return The duration of the current track in milliseconds
     */
    public long duration() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the queue
     *
     * @return The queue as a long[]
     */
    public long[] getQueue() {
        if (mPlayList == null) {
            return new long[0];
        }
        final long[] list = new long[mPlayListLen];
        System.arraycopy(mPlayList, 0, list, 0, mPlayListLen);
        return list;
    }

    /**
     * @return True if music is playing, false otherwise
     */
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    /**
     * This is not the same as being paused. This means there's no track loaded.
     *
     * @return True if there's no track loaded.
     */
    public boolean isStopped() {
        return mPlayer == null || (!mPlayer.isInitialized() && !mPlayer.isPlaying());
        //return mCursor == null || mCursor.isClosed();
    }

    /**
     * True if the current track is a "favorite", false otherwise
     */
    public boolean isFavorite() {
        if (mFavoritesCache != null) {
            //synchronized (mFavoritesCacheLock) {
            final Long id = mFavoritesCache.getSongId(getAudioId());
            return id != null;
            //}
        }
        return false;
    }

    /**
     * Opens a list for playback
     *
     * @param list     The list of tracks to open
     * @param position The position to start playback at
     */
    public void open(final long[] list, final int position) {
        LOG.info("MusicPlaybackService.open at position=" + position, true);
        launchPlayerActivity = true;
        final long oldId = getAudioId();
        final int listlength = list.length;
        boolean newlist = true;
        if (mPlayListLen == listlength) {
            newlist = false;
            for (int i = 0; i < listlength; i++) {
                if (list[i] != mPlayList[i]) {
                    newlist = true;
                    break;
                }
            }
        }
        if (newlist) {
            addToPlayList(list, -1);
            notifyChange(QUEUE_CHANGED);
        }
        if (position == -1) {
            mPlayPos = 0;
        }
        if (position >= 0) {
            mPlayPos = position;
        }
        synchronized (mHistory) {
            mHistory.clear();
        }
        if (openCurrentAndNext() && oldId != getAudioId()) {
            play();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Stops playback and requests service shutdown along with the removal of the player notification
     */
    public void stop() {
        stop(true);
    }

    /**
     * Starts playback.
     */
    public void play() {
        stopSimplePlayer();
        // Audio focus is managed internally by ExoPlayer — no manual requestAudioFocus needed.
        try {
            safeRegisterMediaButton(new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName()));
        } catch (SecurityException e) {
            LOG.error("play() " + e.getMessage(), e);
            // see explanation in initService
        }
        if (mPlayer != null && mPlayer.isInitialized()) {
            setNextTrack();
            synchronized (mHistory) {
                if (mShuffleEnabled && (mHistory.isEmpty() || mHistory.peek() != mPlayPos)) {
                    mHistory.push(mPlayPos);
                }
            }
            // it may happen that the service is destroyed between the NPE check above and the following
            // block, so we'll ask again, and make sure we don't assign mPlayer to null when we try to
            // start playback
            if (mPlayer != null) {
                long duration;
                duration = mPlayer.duration();
                if (mRepeatMode != REPEAT_CURRENT &&
                        duration > 2000 &&
                        mPlayer.position() >= duration - 2000) {
                    gotoNext(true);
                }
                mPlayer.start();
            }
            if (mPlayerHandler != null) {
                mPlayerHandler.removeMessages(FADE_DOWN);
                mPlayerHandler.sendEmptyMessage(FADE_UP);
            }
            mIsSupposedToBePlaying = true;
            notifyChange(PLAYSTATE_CHANGED);
            updateNotification();
        }
    }

    /**
     * Temporarily pauses playback.
     */
    public void pause() {
        if (D) LOG.info("Pausing playback");
        //synchronized (this) {
        if (mPlayerHandler != null) {
            mPlayerHandler.removeMessages(FADE_UP);
        }
        if (mIsSupposedToBePlaying && mPlayer != null) {
            mPlayer.pause();
            mIsSupposedToBePlaying = false;
            if (musicPlaybackActivityInForeground) { // this isn't working as it should.
                updateRemoteControlClient(PLAYSTATE_CHANGED);
            } else {
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
        //}
    }

    public void resume() {
        if (mPlayer != null) {
            mPlayer.start();
            mIsSupposedToBePlaying = true;
            updateRemoteControlClient(PLAYSTATE_CHANGED);
            notifyChange(PLAYSTATE_CHANGED);
        }
    }

    /**
     * Changes from the current track to the next track
     *
     * @param force -> set to true when gotoNext is invoked by an user action
     */
    public void gotoNext(final boolean force) {
        if (D) LOG.info("Going to next track");

        int currentRepeatMode = mRepeatMode;
        if (force && mRepeatMode == REPEAT_CURRENT) {
            setRepeatMode(REPEAT_ALL);
        }

        if (mPlayListLen <= 0) {
            if (D) LOG.info("No play queue");
            return;
        }
        final int pos = getNextPosition(force, isShuffleEnabled());
        if (invalidPosition(pos)) return;
        mPlayPos = pos;
        if (openCurrentAndNext()) {
            play();
            if (force) {
                // make sure repeat mode is restored
                setRepeatMode(currentRepeatMode);
            }
            notifyChange(META_CHANGED);
        }
    }

    public void gotoPrev() {
        long position = position();
        if (position < REWIND_INSTEAD_PREVIOUS_THRESHOLD) {
            stop(false);
            prev();
        } else {
            seek(0);
            play();
        }
    }

    private boolean invalidPosition(int pos) {
        if (pos < 0) { // this looks funky
            if (mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
            return true;
        }
        return false;
    }

    /**
     * Changes from the current track to the previous played track
     */
    private void prev() {
        if (D) LOG.info("Going to previous track");

        if (mRepeatMode == REPEAT_CURRENT) {
            setRepeatMode(REPEAT_ALL);
        }

        synchronized (this) {
            if (!mShuffleEnabled) {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            } else {
                synchronized (mHistory) {
                    if (!mHistory.isEmpty()) {
                        mHistory.pop();
                        if (!mHistory.isEmpty()) {
                            mPlayPos = mHistory.peek();
                        }
                    }
                }
            }
        }

        openCurrent();
        play();
        notifyChange(META_CHANGED);
    }

    /**
     * We don't want to open the current and next track when the user is using
     * the {@code #prev()} method because they won't be able to travel back to
     * the previously listened track if they're shuffling.
     */
    private void openCurrent() {
        openCurrentAndMaybeNext(false);
    }

    /**
     * Toggles the current song as a favorite.
     */
    public void toggleFavorite() {
        if (mFavoritesCache != null) {
            synchronized (this) {
                mFavoritesCache.toggleSong(getAudioId(), getTrackName(), getAlbumName(),
                        getArtistName());
            }
        }
    }

    /**
     * Moves an item in the queue from one position to another
     *
     * @param from The position the item is currently at
     * @param to   The position the item is being moved to
     */
    public void moveQueueItem(int from, int to) {
        synchronized (this) {
            if (from >= mPlayListLen) {
                from = mPlayListLen - 1;
            }
            if (to >= mPlayListLen) {
                to = mPlayListLen - 1;
            }
            if (from < to) {
                final long tmp = mPlayList[from];
                System.arraycopy(mPlayList, from + 1, mPlayList, from, to - from);
//                for (int i = from; i < to; i++) {
//                    mPlayList[i] = mPlayList[i + 1];
//                }
                mPlayList[to] = tmp;
                if (mPlayPos == from) {
                    mPlayPos = to;
                } else if (mPlayPos >= from && mPlayPos <= to) {
                    mPlayPos--;
                }
            } else if (to < from) {
                final long tmp = mPlayList[from];
                //noinspection ManualArrayCopy
                for (int i = from; i > to; i--) {
                    mPlayList[i] = mPlayList[i - 1];
                }
                mPlayList[to] = tmp;
                if (mPlayPos == from) {
                    mPlayPos = to;
                } else if (mPlayPos >= to && mPlayPos <= from) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Sets the repeat mode
     *
     * @param repeatMode The repeat mode to use
     */
    public void setRepeatMode(final int repeatMode) {
        mRepeatMode = repeatMode;
        setNextTrack();
        saveQueue(false);
        notifyChange(REPEATMODE_CHANGED);
        SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastRepeatStateAsync(repeatMode));
    }

    private static void saveLastRepeatStateAsync(int repeatMode) {
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setInt(Constants.PREF_KEY_GUI_PLAYER_REPEAT_MODE, repeatMode);
    }

    /**
     * Sets the shuffle mode
     *
     * @param on The shuffle mode to use
     */
    public void enableShuffle(boolean on) {
        mShuffleEnabled = on;
        SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastShuffleStateAsync(on));
        notifyChange(SHUFFLEMODE_CHANGED);
    }

    private static void saveLastShuffleStateAsync(boolean shuffleEnabled) {
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setBoolean(Constants.PREF_KEY_GUI_PLAYER_SHUFFLE_ENABLED, shuffleEnabled);
    }

    /**
     * Sets the position of a track in the queue
     *
     * @param index The position to place the track
     */
    public void setQueuePosition(final int index) {
        //synchronized (this) {
        stopPlayer();
        mPlayPos = index;
        if (openCurrentAndNext()) {
            play();
            notifyChange(META_CHANGED);
        }
        //}
    }

    /**
     * Queues a new list for playback
     *
     * @param list   The list to queue
     * @param action The action to take
     */
    public void enqueue(final long[] list, final int action) {
        synchronized (this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    if (openCurrentAndNext()) {
                        play();
                        notifyChange(META_CHANGED);
                    }
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
            }
            if (!isPlaying() && openCurrentAndNext()) {
                play();
                pause(); // don't auto start playback after enqueing a track
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Cycles through the different repeat modes
     */
    private void cycleRepeat() {
        if (mRepeatMode == REPEAT_NONE) {
            setRepeatMode(REPEAT_ALL);
        } else if (mRepeatMode == REPEAT_ALL) {
            setRepeatMode(REPEAT_CURRENT);
        } else {
            setRepeatMode(REPEAT_NONE);
        }
    }

    /**
     * @return The album art for the current album.
     */
    public Bitmap getAlbumArt() {
        try {
            // Return the cached artwork
            return mImageFetcher.getArtwork(
                    getAlbumName(),
                    getAlbumId(),
                    getArtistName());
        } catch (Throwable e) {
            e.printStackTrace();
            // due to the lifecycle of android components,
            // mImageFetcher could be null at the moment of call
            // updateRemoveControlClient.
        }
        return null;
    }

    /**
     * Called when one of the lists should refresh or requery.
     */
    public void refresh() {
        notifyChange(REFRESH);
    }

    public void shutdown() {
        mPlayPos = -1;
        MusicUtils.requestMusicPlaybackServiceShutdown(this);
    }

    // MediaPlayerAction, mediaPlayerAction, and mediaPlayerAsyncAction removed —
    // ExoPlayer in MultiPlayer manages its own lifecycle.


    public static final class MusicPlayerHandler extends Handler {
        private final static Logger LOG = Logger.getLogger(MusicPlayerHandler.class);
        private float mCurrentVolume = 1.0f;

        /**
         * Constructor of <code>MusicPlayerHandler</code>
         *
         * @param looper The thread to run on.
         */
        MusicPlayerHandler(final Looper looper) {
            super(looper);
        }

        public Thread getLooperThread() {
            return getLooper().getThread();
        }

        @Override
        public void handleMessage(final Message msg) {
            final MusicPlaybackService service = MusicPlaybackService.INSTANCE;
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case FADE_DOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        sendEmptyMessageDelayed(FADE_DOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADE_UP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        sendEmptyMessageDelayed(FADE_UP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case SERVER_DIED:
                    if (service.isPlaying()) {
                        service.gotoNext(true);
                    } else {
                        service.openCurrentAndNext();
                    }
                    break;
                case TRACK_WENT_TO_NEXT:
                    if (service.mNextPlayPos == -1) {
                        service.mNextPlayPos = 0;
                    }
                    service.mPlayPos = service.mNextPlayPos;
                    if (service.mCursor != null) {
                        service.mCursor.close();
                    }

                    if (service.mPlayPos < service.mPlayList.length) {
                        service.updateCursor(service.mPlayList[service.mPlayPos]);
                        service.notifyChange(META_CHANGED);
                        service.updateNotification();
                        service.setNextTrack();
                    }
                    break;
                case TRACK_ENDED:
                    if (service.mRepeatMode == REPEAT_CURRENT) {
                        service.seek(1);
                    } else {
                        service.gotoNext(false);
                    }
                    break;
                // RELEASE_WAKELOCK and FOCUS_CHANGE removed —
                // ExoPlayer handles wake lock and audio focus internally.
                default:
                    break;
            }
        }
    }

    /**
     * MultiPlayer wraps a single ExoPlayer instance.
     * ExoPlayer handles gapless playback, audio focus, wake mode, and
     * audio-becoming-noisy natively — removing the entire dual-MediaPlayer
     * swap and manual WakeLock management from the original implementation.
     */
    static final class MultiPlayer {
        private final static Logger LOG = Logger.getLogger(MultiPlayer.class);
        ExoPlayer mExoPlayer; // package-private for MediaSession metadata updates in outer class
        private boolean mIsInitialized = false;

        MultiPlayer() {
            // ExoPlayer is built lazily on first setCurrentDataSource call
            // so that MusicPlaybackService.INSTANCE is guaranteed non-null.
        }

        private void ensurePlayer() {
            if (mExoPlayer != null) return;
            if (MusicPlaybackService.INSTANCE == null) return;
            mExoPlayer = new ExoPlayer.Builder(MusicPlaybackService.INSTANCE)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build();
            mExoPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                    true /* handleAudioFocus — ExoPlayer manages focus internally */);
            mExoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) {
                        LOG.info("MultiPlayer: onPlaybackStateChanged STATE_ENDED — track complete");
                        if (MusicPlaybackService.mPlayerHandler != null) {
                            MusicPlaybackService.mPlayerHandler.sendEmptyMessage(TRACK_ENDED);
                        }
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    MusicPlaybackService svc = MusicPlaybackService.getInstance();
                    if (svc != null) {
                        svc.notifyChange(PLAYSTATE_CHANGED);
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    LOG.error("MultiPlayer: onPlayerError: " + error.getMessage(), error);
                    mIsInitialized = false;
                    if (MusicPlaybackService.mPlayerHandler != null) {
                        MusicPlaybackService.mPlayerHandler.sendMessageDelayed(
                                MusicPlaybackService.mPlayerHandler.obtainMessage(SERVER_DIED), 2000);
                    }
                }
            });
        }

        /**
         * Resolve a path string to a Uri suitable for ExoPlayer.
         * Handles content://, http/https, and bare file paths.
         */
        static Uri resolveUri(String path) {
            if (path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://")) {
                return Uri.parse(path);
            }
            // Bare file path — may be URL-encoded
            try {
                String decoded = UrlUtils.decode(path);
                return Uri.fromFile(new File(decoded));
            } catch (Throwable t) {
                return Uri.parse(path);
            }
        }

        /**
         * Opens the given path as the current track and prepares for playback.
         */
        void setCurrentDataSource(final String path) {
            if (MusicPlaybackService.INSTANCE == null) {
                LOG.warn("MultiPlayer::setCurrentDataSource() aborted, INSTANCE is null");
                mIsInitialized = false;
                return;
            }
            String permission = SystemUtils.hasAndroid13OrNewer() ?
                    Manifest.permission.READ_MEDIA_AUDIO :
                    Manifest.permission.READ_EXTERNAL_STORAGE;
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
                    MusicPlaybackService.INSTANCE, permission)) {
                LOG.error("MultiPlayer::setCurrentDataSource failed, " + permission + " not granted");
                mIsInitialized = false;
                return;
            }
            ensurePlayer();
            if (mExoPlayer == null) {
                mIsInitialized = false;
                return;
            }
            // Lazily set up MediaSession now that ExoPlayer exists
            MusicPlaybackService.INSTANCE.setUpMediaSession();
            try {
                MediaItem mediaItem =
                        MediaItem.fromUri(resolveUri(path));
                mExoPlayer.setMediaItem(mediaItem);
                mExoPlayer.prepare();
                mIsInitialized = true;
                // Launch AudioPlayerActivity when the player is ready if requested
                if (MusicPlaybackService.INSTANCE.launchPlayerActivity) {
                    MusicPlaybackService.INSTANCE.launchPlayerActivity = false;
                    LOG.info("MultiPlayer::setCurrentDataSource() launching AudioPlayerActivity");
                    Intent i = new Intent(MusicPlaybackService.INSTANCE, AudioPlayerActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    MusicPlaybackService.INSTANCE.startActivity(i);
                }
            } catch (Throwable e) {
                LOG.error("MultiPlayer::setCurrentDataSource() error: " + e.getMessage(), e);
                mIsInitialized = false;
            }
        }

        /**
         * Enqueues the next track for gapless playback.
         * ExoPlayer's built-in queue handles the seamless transition.
         */
        void setNextDataSource(final String path) {
            if (mExoPlayer == null) return;
            try {
                // Remove any previously queued next item beyond the current one
                int currentIdx = mExoPlayer.getCurrentMediaItemIndex();
                int itemCount = mExoPlayer.getMediaItemCount();
                if (itemCount > currentIdx + 1) {
                    mExoPlayer.removeMediaItems(currentIdx + 1, itemCount);
                }
                if (path == null) return;
                mExoPlayer.addMediaItem(
                        MediaItem.fromUri(resolveUri(path)));
                LOG.info("MultiPlayer::setNextDataSource() queued next track successfully");
            } catch (Throwable e) {
                LOG.error("MultiPlayer::setNextDataSource() error: " + e.getMessage(), e);
            }
        }

        /** @return True if the player is ready to go, false otherwise */
        boolean isInitialized() {
            return mIsInitialized;
        }

        /** Starts or resumes playback. */
        public void start() {
            if (mExoPlayer != null) {
                try { mExoPlayer.play(); } catch (Throwable ignored) {}
            }
        }

        /** Stops playback and resets the player to an uninitialized state. */
        public void stop() {
            if (mExoPlayer != null) {
                try {
                    mExoPlayer.stop();
                    mExoPlayer.clearMediaItems();
                    mIsInitialized = false;
                } catch (Throwable ignored) {}
            }
        }

        /** Releases all ExoPlayer resources. */
        public void release() {
            if (mExoPlayer != null) {
                try {
                    mExoPlayer.release();
                } catch (Throwable ignored) {}
                mExoPlayer = null;
                mIsInitialized = false;
            }
        }

        /** Pauses playback. Call start() to resume. */
        public void pause() {
            if (mExoPlayer != null) {
                try { mExoPlayer.pause(); } catch (Throwable ignored) {}
            }
        }

        /** @return The duration of the current track in milliseconds, or -1. */
        public long duration() {
            if (mExoPlayer != null) {
                try {
                    long d = mExoPlayer.getDuration();
                    return d == C.TIME_UNSET ? -1 : d;
                } catch (Throwable t) { return -1; }
            }
            return -1;
        }

        /** @return The current playback position in milliseconds. */
        public long position() {
            if (mExoPlayer != null) {
                try { return mExoPlayer.getCurrentPosition(); } catch (Throwable ignored) {}
            }
            return 0;
        }

        /** Seeks to the given position in milliseconds. */
        long seek(final long whereto) {
            if (mExoPlayer != null) {
                try { mExoPlayer.seekTo(whereto); } catch (Throwable ignored) {}
            }
            return whereto;
        }

        /** Sets the playback volume (0.0–1.0). */
        public void setVolume(final float vol) {
            if (mExoPlayer != null) {
                try { mExoPlayer.setVolume(vol); } catch (Throwable ignored) {}
            }
        }

        /** @return True if the player is currently playing. */
        public boolean isPlaying() {
            return mExoPlayer != null && mExoPlayer.isPlaying();
        }

        /**
         * Returns the audio session ID for equalizer / AudioEffect integration.
         */
        int getAudioSessionId() {
            if (mExoPlayer != null) {
                try { return mExoPlayer.getAudioSessionId(); } catch (Throwable ignored) {}
            }
            return 0;
        }

        /** No-op stubs kept for call-site compatibility during transition. */
        public void setAudioSessionId(final int sessionId) {
            // ExoPlayer manages its own audio session; this is a no-op.
        }
    }
}
