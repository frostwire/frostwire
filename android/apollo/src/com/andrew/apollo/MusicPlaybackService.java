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
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RemoteControlClient;
import android.media.audiofx.AudioEffect;
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
import android.os.PowerManager.WakeLock;
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
import java.util.HashMap;
import java.util.Random;
import java.util.Stack;
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
    private static final String POSITION_CHANGED = "com.android.apollo.positionchanged";

    /**
     * Indicates the meta data has changed in some way, like a track change
     */
    public static final String META_CHANGED = "com.andrew.apollo.metachanged";

    /**
     * Indicates the queue has been updated
     */
    private static final String QUEUE_CHANGED = "com.andrew.apollo.queuechanged";

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

    private AudioFocusRequest AUDIO_FOCUS_REQUEST;

    private static final String[] ALBUM_PROJECTION = new String[]{
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.LAST_YEAR
    };

    private static final String[] SIMPLE_PROJECTION = new String[]{
            "_id",
            MediaStore.Audio.Media.DATA
    };

    private static final Stack<Integer> mHistory = new Stack<>();
    private static final char[] HEX_DIGITS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'
    };

    private MultiPlayer mPlayer;
    private MediaPlayer mSimplePlayer;
    private String mSimplePlayerPlayingFile;

    private WakeLock mWakeLock;
    private Cursor mCursor;
    private Cursor mAlbumCursor;
    private volatile AudioManager mAudioManager;
    private SharedPreferences mPreferences;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQueueIsSaveable = true;
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean musicPlaybackActivityInForeground = false;
    private RemoteControlClient mRemoteControlClient;
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
    private static MusicPlayerHandler mPlayerHandler;
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

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(final int focusChange) {
            if (mPlayerHandler != null) {
                mPlayerHandler.obtainMessage(FOCUS_CHANGE, focusChange, 0).sendToTarget();
            }
        }
    };

    static {
        notifyChangeIntervals.put(QUEUE_CHANGED, 1000L);
        notifyChangeIntervals.put(META_CHANGED, 150L);
        notifyChangeIntervals.put(POSITION_CHANGED, 300L);
        notifyChangeIntervals.put(PLAYSTATE_CHANGED, 150L);
        notifyChangeIntervals.put(REPEATMODE_CHANGED, 150L);
        notifyChangeIntervals.put(SHUFFLEMODE_CHANGED, 150L);
        notifyChangeIntervals.put(REFRESH, 1000L);
        mPlayerHandler = setupMPlayerHandler();
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
        prepareAudioFocusRequest();
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
                } catch (Exception e) {
                    LOG.error("onCreate: Failed to start foreground service: " + e.getMessage(), e);
                    // Continue without foreground service - the service may be killed by system but won't crash
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

        if (intent != null && intent.getBooleanExtra(FROM_MEDIA_BUTTON, false)) {
            MediaButtonIntentReceiver.completeWakefulIntent(intent);
        }
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

        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocusRequest(AUDIO_FOCUS_REQUEST);
            mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);
        }

        closeCursor();
        unregisterReceivers();

        if (mWakeLock != null) {
            try {
                mWakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }

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
            mSimplePlayer = MediaPlayer.create(this, Uri.parse(path));
            if (mSimplePlayer != null) {
                final String pathCopy = path;
                mSimplePlayerPlayingFile = path;
                mSimplePlayer.setOnCompletionListener(mp -> {
                    mSimplePlayerPlayingFile = null;
                    notifySimpleStopped(pathCopy);
                });
                mSimplePlayer.start();
            }
        }
    }

    public void stopSimplePlayer() {
        if (mSimplePlayer != null) {
            mSimplePlayer.reset();
            mSimplePlayer.release();
            notifySimpleStopped(mSimplePlayerPlayingFile);
            mSimplePlayer = null;
            mSimplePlayerPlayingFile = null;
        }
    }

    private void prepareAudioFocusRequest() {
        AudioAttributes.Builder attributeBuilder = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            attributeBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
        }

        AudioAttributes audioAttributes = attributeBuilder.build();
        AUDIO_FOCUS_REQUEST =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).
                        setOnAudioFocusChangeListener(mAudioFocusListener).
                        setAudioAttributes(audioAttributes).
                        setWillPauseWhenDucked(true).
                        build();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void setUpRemoteControlClient() {
        final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mMediaButtonReceiverComponent);
        mRemoteControlClient = new RemoteControlClient(
                PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

        try {
            if (mAudioManager != null) {
                mAudioManager.registerRemoteControlClient(mRemoteControlClient);
            }
        } catch (Throwable t) {
            // ignore errors due to system restrictions
        }

        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_STOP
                | RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;
        try {
            mRemoteControlClient.setOnGetPlaybackPositionListener(this::position);
            mRemoteControlClient.setPlaybackPositionUpdateListener(this::seek);
        } catch (Throwable t) {
            // older platforms might not support these calls
        }
        mRemoteControlClient.setTransportControlFlags(flags);
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mAudioManager.abandonAudioFocusRequest(AUDIO_FOCUS_REQUEST);
                } else {
                    mAudioManager.abandonAudioFocus(mAudioFocusListener);
                }
            } catch (Throwable t) {
            }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setUpRemoteControlClient();
        }

        mPreferences = getSharedPreferences("Service", 0);
        mCardId = getCardId();

        registerExternalStorageListener();

        mPlayer = new MultiPlayer(mPlayerHandler);
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

        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.setReferenceCounted(false);
        }

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
     * Updates the lock screen controls.
     *
     * @param what The broadcast
     */
    private void updateRemoteControlClient(final String what) {
        if (mRemoteControlClient == null) {
            LOG.info("MusicPlaybackService::updateRemoteControlClient() aborted. mRemoteControlClient is null, review your logic");
            return;
        }

        if (what == null) {
            LOG.info("MusicPlaybackService::updateRemoteControlClient() aborted. what is null, review your logic");
            return;
        }


        LOG.info("MusicPlaybackService::updateRemoteControlClient(what=" + what + ")");

        int playState;
        final boolean isPlaying = isPlaying();
        final boolean isStopped = isStopped();
        final boolean isPaused = !isPlaying && !isStopped;

        if (isPaused) {
            playState = RemoteControlClient.PLAYSTATE_PAUSED;
        } else if (isPlaying) {
            playState = RemoteControlClient.PLAYSTATE_PLAYING;
        } else {
            playState = RemoteControlClient.PLAYSTATE_STOPPED;
        }

        if (isStopped && !isPlaying && mNotificationHelper != null) {
            mNotificationHelper.killNotification();
        }
        final int playStateFinalCopy = playState;
        switch (what) {
            case PLAYSTATE_CHANGED:
            case POSITION_CHANGED:
            case PLAYSTATE_STOPPED:
                if (mNotificationHelper != null) {
                    try {
                        mNotificationHelper.updatePlayState(isPlaying, isStopped);
                    } catch (Throwable ignored) {
                    }
                }
                remoteControlClientSetPlaybackStateTask(mRemoteControlClient, playStateFinalCopy);
                break;
            case META_CHANGED:
            case QUEUE_CHANGED:
                // Asynchronously gets bitmap and then updates the Remote Control Client with that bitmap
                SystemUtils.exceptionSafePost(mPlayerHandler, () -> changeRemoteControlClientTask(playStateFinalCopy, position()));
                break;
        }
    }

    private static void remoteControlClientSetPlaybackStateTask(RemoteControlClient rc, int playState) {
        try {
            rc.setPlaybackState(playState);
        } catch (Throwable throwable) {
            // rare android internal NPE
            LOG.error(throwable.getMessage(), throwable);
        }
    }

    private static void changeRemoteControlClientTask(int playState, long position) {
        if (INSTANCE == null) {
            LOG.info("changeRemoteControlClientTask() aborted, no MusicPlaybackService available");
            return;
        }
        if (MusicPlaybackService.mPlayerHandler == null) {
            MusicPlaybackService.setupMPlayerHandler();
        }
        // background portion
        Bitmap albumArt = INSTANCE.getAlbumArt();
        // RemoteControlClient wants to recycle the bitmaps thrown at it, so we need
        // to make sure not to hand out our cache copy
        Bitmap.Config config = null;
        if (albumArt != null) {
            config = albumArt.getConfig();
        }
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        Bitmap bmpCopy = null;
        try {
            if (albumArt != null) {
                bmpCopy = albumArt.copy(config, false);
            }
        } catch (OutOfMemoryError e) {
            // ignore, can't do anything meaningful here
        }
        final Bitmap albumArtCopy = bmpCopy;
        final String artistName = INSTANCE.getArtistName();
        final String albumName = INSTANCE.getAlbumName();
        final String trackName = INSTANCE.getTrackName();
        final String albumArtistName = INSTANCE.getAlbumArtistName();
        final long duration = INSTANCE.duration();
        try {
            // TODO: Refactor to use MediaSession instead
            RemoteControlClient.MetadataEditor editor = INSTANCE.mRemoteControlClient
                    .editMetadata(true)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artistName)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, albumArtistName)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, albumName)
                    .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, trackName)
                    .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration);
            if (albumArtCopy != null) {
                editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, albumArtCopy);
            }
            editor.apply();
        } catch (Throwable t) {
            // possible NPE on android.media.RemoteControlClient$MetadataEditor.apply()
        }
        try {
            INSTANCE.mRemoteControlClient.setPlaybackState(playState, position, 1.0f);
        } catch (Throwable t) {
            // temporary fix for Android 4.1, we need MediaSession refactor
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
            mPlayer = new MultiPlayer(mPlayerHandler);
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
        return mPlayer == null || (!mPlayer.isInitialized() && mPlayer.mCurrentMediaPlayer == null && mPlayer.mNextMediaPlayer == null);
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
        mHistory.clear();
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
        if (mAudioManager == null) {
            LOG.info("play() aborted, mAudioManager is null");
            return;
        }
        stopSimplePlayer();
        int status;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            status = mAudioManager.requestAudioFocus(AUDIO_FOCUS_REQUEST);
        } else {
            status = mAudioManager.requestAudioFocus(mAudioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        if (D) {
            LOG.info("play(): Starting playback: audio focus request status = " + status);
        }
        if (status != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }
        try {
            safeRegisterMediaButton(new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName()));
        } catch (SecurityException e) {
            LOG.error("play() " + e.getMessage(), e);
            // see explanation in initService
        }
        if (mPlayer != null && mPlayer.isInitialized()) {
            setNextTrack();
            if (mShuffleEnabled && (mHistory.empty() || mHistory.peek() != mPlayPos)) {
                mHistory.push(mPlayPos);
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
                if (!mHistory.empty()) {
                    mHistory.pop();
                    if (!mHistory.empty()) {
                        mPlayPos = mHistory.peek();
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

    enum MediaPlayerAction {
        START,
        RELEASE,
        RESET
    }

    private static void mediaPlayerAction(MediaPlayer mediaPlayer, MediaPlayerAction action) {
        try {
            switch (action) {
                case START:
                    mediaPlayer.start();
                    return;
                case RELEASE:
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release(); //after a release, it's gone and you can end up in Illegal states
                    return;
                case RESET:
                    mediaPlayer.reset();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void mediaPlayerAsyncAction(MediaPlayer mediaPlayer,
                                               MediaPlayerAction action) {
        if (mediaPlayer != null && MusicPlaybackService.mPlayerHandler != null) {
            SystemUtils.exceptionSafePost(mPlayerHandler, () -> MusicPlaybackService.mediaPlayerAction(mediaPlayer, action));
        }
    }


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
                case RELEASE_WAKELOCK:
                    service.mWakeLock.release();
                    break;
                case FOCUS_CHANGE:
                    if (D) LOG.info("Received audio focus change event " + msg.arg1);
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (service.isPlaying()) {
                                service.mPausedByTransientLossOfFocus =
                                        msg.arg1 == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                            }
                            service.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            removeMessages(FADE_UP);
                            sendEmptyMessage(FADE_DOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying()
                                    && service.mPausedByTransientLossOfFocus) {
                                service.mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                service.mPlayer.setVolume(mCurrentVolume);
                                service.play();
                            } else {
                                removeMessages(FADE_DOWN);
                                sendEmptyMessage(FADE_UP);
                            }
                            break;
                        default:
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static final class MultiPlayer implements MediaPlayer.OnErrorListener,
            MediaPlayer.OnCompletionListener {
        private final static Logger LOG = Logger.getLogger(MultiPlayer.class);
        private MediaPlayer mCurrentMediaPlayer;
        private MediaPlayer mNextMediaPlayer;
        private boolean mIsInitialized = false;

        enum TargetPlayer {
            CURRENT,
            NEXT
        }

        MultiPlayer(MusicPlayerHandler mPlayerHandler) {
            initCurrentMediaPlayer();
            initNextMediaPlayer();
        }

        private interface OnPlayerPrepareCallback {
            void onPrepared(boolean result);
        }

        /**
         * @param path The path of the file, or the http/rtsp URL of the stream
         *             you want to play
         */
        void setCurrentDataSource(final String path) {
            if (mCurrentMediaPlayer == null) {
                initCurrentMediaPlayer();
            }
            mIsInitialized = setDataSource(TargetPlayer.CURRENT, path);
        }

        /**
         * Set the MediaPlayer to start when this MediaPlayer finishes playback.
         *
         * @param path The path of the file, or the http/rtsp URL of the stream
         *             you want to play
         */
        void setNextDataSource(final String path) {
            releaseNextMediaPlayer();
            if (path == null) {
                return;
            }
            if (!initNextMediaPlayer()) {
                return;
            }
            if (setDataSource(TargetPlayer.NEXT, path)) {
                try {
                    mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
                    LOG.info("MusicPlaybackService.setNextDataSource() set next media player successfully");
                } catch (Throwable e) {
                    LOG.error("MusicPlaybackService.setNextDataSource() Media player fatal error: " + e.getMessage(), e);
                }
            } else {
                releaseNextMediaPlayer();
                initNextMediaPlayer();
            }
        }

        private boolean setDataSource(TargetPlayer designatedPlayer, String path) {
            if (MusicPlaybackService.INSTANCE == null) {
                LOG.warn("MusicPlaybackService::MultiPlayer::setDataSourceTask() aborted, no MusicPlaybackService available");
                return false;
            }
            String permission = SystemUtils.hasAndroid13OrNewer() ?
                    Manifest.permission.READ_MEDIA_AUDIO :
                    Manifest.permission.READ_EXTERNAL_STORAGE;
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MusicPlaybackService.INSTANCE, permission)) {
                LOG.error("MusicPlaybackService::MultiPlayer::setDataSource failed, " + permission + " not granted");
                return false;
            }
            MediaPlayer player = mCurrentMediaPlayer;
            if (designatedPlayer == TargetPlayer.NEXT) {
                player = mNextMediaPlayer;
            }
            try {
                player.reset();
                player.setOnCompletionListener(this);
                player.setOnErrorListener(this);
                if (MusicPlaybackService.INSTANCE.launchPlayerActivity) {
                    player.setOnPreparedListener(new AudioOnPreparedListener(Ref.weak(MusicPlaybackService.INSTANCE)));
                }
                if (path.startsWith("content://")) {
                    final Uri pathUri = Uri.parse(path);
                    if (!trySettingDataSourceManyWays(player, pathUri)) {
                        player.release();
                        return false;
                    }
                } else {
                    // /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/TorrentData/...
                    try {
                        File f = new File(UrlUtils.decode(path));
                        FileInputStream fis = new FileInputStream(f);
                        player.setDataSource(fis.getFD());
                    } catch (Throwable t) {
                        LOG.info("MusicPlaybackService.setDataSource player.setDataSource(fileInputStream.getFD() failed -> " + t.getMessage(), true);
                        player.setDataSource(path);
                    }

                }
                player.prepare();
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
                return false;
            }
            return true;
        }

        private boolean trySettingDataSourceManyWays(final MediaPlayer player,
                                                     final Uri pathUri) {
            if (pathUri == null) {
                return false;
            }
            String path = pathUri.toString();
            if (path.startsWith("content://media/external")) {
                if (!setDatasourceUsingDataPathFromMediaStoreContentURI(player, pathUri)) {
                    if (!setDataSourceUsingContentPathURI(player, pathUri)) {
                        return setDataSourceUsingAFileDescriptor(player, pathUri);
                    }
                }
            } else if (path.startsWith("content://com.android.providers.downloads.documents")) {
                if (!setDataSourceUsingFilePathFromComAndroidProvidersDownloadsDocumentsPath(player, pathUri)) {
                    return setDataSourceUsingAFileDescriptor(player, pathUri);
                }
            }
            return setDataSourceUsingAFileDescriptor(player, pathUri);
        }

        private boolean setDataSourceUsingFilePathFromComAndroidProvidersDownloadsDocumentsPath(final MediaPlayer player,
                                                                                                final Uri pathUri) {
            String dirtyPath = pathUri.toString();
            String cleanPath = MusicUtils.getFilePathFromComAndroidProvidersDownloadsDocumentsPath(MusicPlaybackService.INSTANCE, dirtyPath);
            Uri cleanUri = Uri.parse(cleanPath);
            try {
                player.reset();
                player.setDataSource(MusicPlaybackService.INSTANCE, cleanUri);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        private boolean setDataSourceUsingContentPathURI(
                final MediaPlayer player,
                final Uri pathUri) {
            try {
                player.reset();
                LOG.info("setDataSourceUsingContentPathURI: pathUri = " + pathUri);
                player.setDataSource(MusicPlaybackService.INSTANCE, pathUri);
                return true;
            } catch (Throwable e2) {
                LOG.error("setDataSourceUsingContentPathURI: " + e2.getMessage());
                LOG.error("setDataSourceUsingContentPathURI: failed with pathUri = " + pathUri);
            }
            return false;
        }

        private boolean setDatasourceUsingDataPathFromMediaStoreContentURI(
                final MediaPlayer player,
                final Uri pathUri) {
            try {
                String dataPath = MusicUtils.getDataPathFromMediaStoreContentURI(MusicPlaybackService.INSTANCE, pathUri);
                if (dataPath != null) {
                    LOG.info("setDatasourceUsingDataPathFromMediaStoreContentURI: dataPath = " + dataPath);
                    Uri dataPathUri = UIUtils.getFileUri(MusicPlaybackService.INSTANCE, dataPath);
                    LOG.info("setDatasourceUsingDataPathFromMediaStoreContentURI: dataPathUri = " + dataPathUri);
                    player.setDataSource(MusicPlaybackService.INSTANCE, dataPathUri);
                }
            } catch (Throwable e3) {
                LOG.error("setDatasourceUsingDataPathFromMediaStoreContentURI: " + e3.getMessage(), e3);
                return false;
            }
            return true;
        }

        private boolean setDataSourceUsingAFileDescriptor(
                final MediaPlayer player,
                final Uri pathUri) {
            try {
                LOG.info("setDataSourceUsingAFileDescriptor: try getting file descriptor from pathUri = " + pathUri);
                ParcelFileDescriptor pfd = MusicPlaybackService.INSTANCE.getContentResolver().openFileDescriptor(pathUri, "r");
                FileDescriptor fd = pfd.getFileDescriptor();
                LOG.info("setDataSourceUsingAFileDescriptor: is file descriptor valid? " + fd.valid());
                player.reset();
                player.setDataSource(fd);
                LOG.info("setDataSourceUsingAFileDescriptor: success with file descriptor");
                return true;
            } catch (Throwable e) {
                LOG.error("setDataSourceUsingAFileDescriptor: failure with file descriptor -> " + e.getMessage());
                return false;
            }
        }

        private void initCurrentMediaPlayer() {
            if (mCurrentMediaPlayer != null) {
                releaseCurrentMediaPlayer();
            }
            mCurrentMediaPlayer = new MediaPlayer();
            initMediaPlayer(mCurrentMediaPlayer);
        }

        private boolean initNextMediaPlayer() {
            if (mNextMediaPlayer != null) {
                releaseNextMediaPlayer();
            }
            mNextMediaPlayer = new MediaPlayer();
            return initMediaPlayer(mNextMediaPlayer);
        }

        private boolean initMediaPlayer(@NonNull MediaPlayer mediaPlayer) {
            if (MusicPlaybackService.instanceReady()) {
                try {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mediaPlayer.setWakeMode(MusicPlaybackService.getInstance(), PowerManager.PARTIAL_WAKE_LOCK);
                    mediaPlayer.setAudioSessionId(getAudioSessionId());
                    return true;
                } catch (Throwable e) {
                    LOG.error("Media player Illegal State exception", e);
                }
            }
            return false;
        }

        void releaseMediaPlayer(MediaPlayer mediaPlayer) {
            if (mediaPlayer != null) {
                mediaPlayerAction(mediaPlayer, MediaPlayerAction.RELEASE);
            }
        }

        private void releaseCurrentMediaPlayer() {
            try {
                releaseMediaPlayer(mCurrentMediaPlayer);
                mCurrentMediaPlayer = null;
                LOG.info("mCurrentMediaPlayer released and nullified");
            } catch (Throwable t) {
                LOG.warn("releaseCurrentMediaPlayer() couldn't release mCurrentMediaPlayer", t);
            }
        }

        private void releaseNextMediaPlayer() {
            try {
                releaseMediaPlayer(mNextMediaPlayer);
                mNextMediaPlayer = null;
                LOG.info("mNextMediaPlayer released and nullified");
            } catch (Throwable t) {
                LOG.warn("releaseNextMediaPlayer() couldn't release mNextMediaPlayer", t);
            }
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        boolean isInitialized() {
            return mIsInitialized;
        }

        /**
         * Starts or resumes playback.
         */
        public void start() {
            if (mCurrentMediaPlayer != null) {
                try {
                    mediaPlayerAsyncAction(mCurrentMediaPlayer, MediaPlayerAction.START);
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * Resets the MediaPlayer to its uninitialized state.
         */
        public void stop() {
            if (mCurrentMediaPlayer != null) {
                try {
                    mediaPlayerAsyncAction(mCurrentMediaPlayer, MediaPlayerAction.RESET);
                    mediaPlayerAsyncAction(mNextMediaPlayer, MediaPlayerAction.RESET);
                    releaseCurrentMediaPlayer();
                    releaseNextMediaPlayer();
                    mIsInitialized = false;
                } catch (Throwable t) {
                    // recover from possible IllegalStateException caused by native _reset() method.
                }
            }
        }

        /**
         * Releases resources associated with this MediaPlayer object.
         */
        public void release() {
            if (mCurrentMediaPlayer != null) {
                try {
                    releaseCurrentMediaPlayer();
                    releaseNextMediaPlayer();
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * Pauses playback. Call start() to resume.
         */
        public void pause() {
            if (mCurrentMediaPlayer != null) {
                try {
                    mCurrentMediaPlayer.pause();
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * NOTE: This method can take a long time, do not use on the main thread
         * Gets the duration of the file.
         *
         * @return The duration in milliseconds
         */
        public long duration() {
            if (mCurrentMediaPlayer != null) {
                try {
                    return mCurrentMediaPlayer.getDuration();
                } catch (Throwable t) {
                    return -1;
                }
            }
            return -1;
        }

        /**
         * Gets the current playback position.
         *
         * @return The current position in milliseconds
         */
        public long position() {
            long result = 0;
            if (mCurrentMediaPlayer != null) {
                try {
                    result = mCurrentMediaPlayer.getCurrentPosition();
                } catch (Throwable ignored) {
                }
            }
            return result;
        }

        /**
         * Gets the current playback position.
         *
         * @param whereto The offset in milliseconds from the start to seek to
         * @return The offset in milliseconds from the start to seek to
         */
        long seek(final long whereto) {
            if (mCurrentMediaPlayer != null) {
                try {
                    mCurrentMediaPlayer.seekTo((int) whereto);
                } catch (Throwable ignored) {
                }
            }
            return whereto;
        }

        /**
         * Sets the volume on this player.
         *
         * @param vol Left and right volume scalar
         */
        public void setVolume(final float vol) {
            if (mCurrentMediaPlayer != null) {
                try {
                    mCurrentMediaPlayer.setVolume(vol, vol);
                } catch (Throwable t) {
                    // possible native IllegalStateException.
                }
            }
        }

        /**
         * Sets the audio session ID.
         *
         * @param sessionId The audio session ID
         */
        public void setAudioSessionId(final int sessionId) {
            if (mCurrentMediaPlayer != null) {
                try {
                    mCurrentMediaPlayer.setAudioSessionId(sessionId);
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * Returns the audio session ID.
         *
         * @return The current audio session ID.
         */
        int getAudioSessionId() {
            int result = 0;
            if (mCurrentMediaPlayer != null) {
                try {
                    result = mCurrentMediaPlayer.getAudioSessionId();
                } catch (Throwable ignored) {
                }
            }
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onError(final MediaPlayer mp, final int what, final int extra) {
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                try {
                    mIsInitialized = false;
                    releaseCurrentMediaPlayer();
                    initCurrentMediaPlayer();
                    MusicPlaybackService.mPlayerHandler.sendMessageDelayed(MusicPlaybackService.mPlayerHandler.obtainMessage(SERVER_DIED), 2000);
                } catch (Throwable ignored) {
                }
                return true;
            }
            return false;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public void onCompletion(final MediaPlayer mp) {
            if (MusicPlaybackService.getInstance() == null) {
                LOG.info("onCompletion() aborted, no MusicPlayBackService available");
                return;
            }
            if (mp != mCurrentMediaPlayer) {
                LOG.info("onCompletion() aborted, mp is not the current player");
                return;
            }
            if (mNextMediaPlayer == null) {
                LOG.info("onCompletion() aborted, no mNextMediaPlayer available to play the next song");
                return;
            }
            try {
                LOG.info("onCompletion() invoked");
                if (MusicPlaybackService.getInstance() != null &&
                        mp == mCurrentMediaPlayer &&
                        mNextMediaPlayer != null) {
                    releaseCurrentMediaPlayer();
                    mCurrentMediaPlayer = mNextMediaPlayer;
                    MusicPlaybackService.getInstance().mWakeLock.acquire(30000);
                    if (MusicPlaybackService.mPlayerHandler != null) {
                        MusicPlaybackService.mPlayerHandler.sendEmptyMessage(TRACK_ENDED);
                        MusicPlaybackService.mPlayerHandler.sendEmptyMessage(RELEASE_WAKELOCK);
                        LOG.info("onCompletion() finished successfully");
                    }
                }
            } catch (Throwable t) {
                LOG.error("onCompletion() error: " + t.getMessage(), t);
            }
        }
    }

    private static final class AudioOnPreparedListener implements MediaPlayer.OnPreparedListener {

        private final WeakReference<MusicPlaybackService> serviceRef;

        AudioOnPreparedListener(WeakReference<MusicPlaybackService> serviceRef) {
            this.serviceRef = serviceRef;
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            if (Ref.alive(serviceRef) && serviceRef.get().launchPlayerActivity) {
                serviceRef.get().launchPlayerActivity = false;
                Intent i = new Intent(serviceRef.get(), AudioPlayerActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                serviceRef.get().startActivity(i);
            }
        }

    }
}
