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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.StaleDataException;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.AudioPlayerNotificationActivity;
import com.andrew.apollo.ui.activities.HomeActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.DataStoreManager;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.google.android.gms.common.internal.Asserts;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * A background {@link Service} used to keep music playing between activities and when the user
 * moves Apollo into the background.
 *
 * <p>The queue is ExoPlayer's playlist ({@link PlaybackQueue}). Threading: ExoPlayer and the queue
 * are only used on the player thread ({@code mPlayerHandler}). Methods that change them run at once
 * when called there, else they are posted to it; the queue's getters read a snapshot that thread
 * publishes, so any thread may call them.
 */
public class MusicPlaybackService extends MediaSessionService {
  private final Object cursorLock = new Object();
  private static final Object safePostLock = new Object();
  private static final ArrayDeque<Runnable> pendingSafePosts = new ArrayDeque<>();
  private static final int MAX_PENDING_SAFE_POSTS = 64;

  public static void safePost(Runnable runnable) {
    if (runnable == null) {
      return;
    }
    if (MusicPlaybackService.mPlayerHandler != null) {
      SystemUtils.exceptionSafePost(MusicPlaybackService.mPlayerHandler, runnable);
      return;
    }
    synchronized (safePostLock) {
      if (MusicPlaybackService.mPlayerHandler != null) {
        SystemUtils.exceptionSafePost(MusicPlaybackService.mPlayerHandler, runnable);
        return;
      }
      if (MusicPlaybackService.INSTANCE != null) {
        if (pendingSafePosts.size() >= MAX_PENDING_SAFE_POSTS) {
          pendingSafePosts.removeFirst();
          LOG.warn(
              "MusicPlaybackService.safePost dropped oldest queued task while waiting for mPlayerHandler");
        }
        pendingSafePosts.addLast(runnable);
        LOG.warn("MusicPlaybackService.safePost queued task until mPlayerHandler is ready");
        return;
      }
    }
    LOG.warn("MusicPlaybackService.safePost ignored because the service is not running yet");
  }

  private static void flushPendingSafePosts() {
    MusicPlayerHandler handler = MusicPlaybackService.mPlayerHandler;
    if (handler == null) {
      return;
    }
    synchronized (safePostLock) {
      while (!pendingSafePosts.isEmpty()) {
        SystemUtils.exceptionSafePost(handler, pendingSafePosts.removeFirst());
      }
    }
  }

  private static final Logger LOG = Logger.getLogger(MusicPlaybackService.class);
  private static final boolean D = BuildConfig.DEBUG;

  private static MusicPlaybackService INSTANCE = null;

  private static final CountDownLatch initLatch = new CountDownLatch(1);

  private static final LocalBinder binder = new LocalBinder();

  /** Indicates that the music has paused or resumed */
  public static final String PLAYSTATE_CHANGED = "com.andrew.apollo.playstatechanged";

  /** Indicates has been stopped */
  private static final String PLAYSTATE_STOPPED = "com.andrew.apollo.playstatestopped";

  /** Indicates that music playback position within a title was changed */
  public static final String POSITION_CHANGED = "com.android.apollo.positionchanged";

  /** Indicates the meta data has changed in some way, like a track change */
  public static final String META_CHANGED = "com.andrew.apollo.metachanged";

  /** Indicates the queue has been updated */
  public static final String QUEUE_CHANGED = "com.andrew.apollo.queuechanged";

  /** Indicates the repeat mode changed */
  public static final String REPEATMODE_CHANGED = "com.andrew.apollo.repeatmodechanged";

  /** Indicates the shuffle mode changed */
  public static final String SHUFFLEMODE_CHANGED = "com.andrew.apollo.shufflemodechanged";

  /** For backwards compatibility reasons, also provide sticky broadcasts under the music package */
  private static final String APOLLO_PACKAGE_NAME = "com.andrew.apollo";

  private static final String MUSIC_PACKAGE_NAME = "com.android.music";

  /** Called to indicate a general service command. */
  public static final String SERVICECMD = "com.andrew.apollo.musicservicecommand";

  /** Called to go toggle between pausing and playing the music */
  static final String TOGGLEPAUSE_ACTION = "com.andrew.apollo.togglepause";

  /** Called to go to pause the playback */
  private static final String PAUSE_ACTION = "com.andrew.apollo.pause";

  /** Called to go to stop the playback */
  static final String STOP_ACTION = "com.andrew.apollo.stop";

  /** Called to go to the previous track */
  public static final String PREVIOUS_ACTION = "com.andrew.apollo.previous";

  /** Called to go to the next track */
  static final String NEXT_ACTION = "com.andrew.apollo.next";

  /** Called to change the repeat mode */
  private static final String REPEAT_ACTION = "com.andrew.apollo.repeat";

  /** Called to change the shuffle mode */
  private static final String SHUFFLE_ACTION = "com.andrew.apollo.shuffle";

  /** Called to update the service about the foreground state of Apollo's activities */
  public static final String FOREGROUND_STATE_CHANGED = "com.andrew.apollo.fgstatechanged";

  public static final String NOW_IN_FOREGROUND = "nowinforeground";

  /** Used to easily notify a list that it should refresh. i.e. A playlist changes */
  public static final String REFRESH = "com.andrew.apollo.refresh";

  /** Used by the alarm intent to shutdown the service after being idle */
  public static final String SHUTDOWN_ACTION = "com.andrew.apollo.shutdown";

  /** Simple player stopped playing the sound (completed or was stopped) */
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

  private static final int SERVER_DIED = 4;
  private static final int FADE_DOWN = 6;
  private static final int FADE_UP = 7;

  /** "Previous" later than this into a song restarts it instead. */
  private static final long REWIND_INSTEAD_PREVIOUS_THRESHOLD = 3000;

  /** Songs in a row that may fail to play before playback stops instead of skipping on. */
  private static final int MAX_FAILED_SONGS_SKIPPED = 10;

  /** How long a caller off the main thread waits for the player thread to change the queue. */
  private static final long QUEUE_CHANGE_TIMEOUT_MS = 2000;

  private static final String AUDIO_ID_COLUMN_NAME =
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ? "_id" : "audio._id AS _id";

  private static final String[] PROJECTION =
      new String[] {
        AUDIO_ID_COLUMN_NAME,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.DURATION
      };

  // AUDIO_FOCUS_REQUEST removed — ExoPlayer in MultiPlayer handles audio focus internally.

  private static final String[] ALBUM_PROJECTION =
      new String[] {
        MediaStore.Audio.Albums.ALBUM,
        MediaStore.Audio.Albums.ARTIST,
        MediaStore.Audio.Albums.LAST_YEAR
      };

  private static final String[] SIMPLE_PROJECTION =
      new String[] {"_id", MediaStore.Audio.Media.DATA};

  private static final char[] HEX_DIGITS =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  // Both made on the player thread in initService, and only used there, except for the
  // thread-safe reads they document.
  private volatile MultiPlayer mPlayer;
  private volatile PlaybackQueue mQueue;
  // SimplePlayer wraps a lightweight ExoPlayer for single-file preview playback
  // (distinct from the queued MultiPlayer used for full music playback).
  private ExoPlayer mSimplePlayer;
  private String mSimplePlayerPlayingFile;

  // mWakeLock removed — ExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL) handles this internally.
  private Cursor mCursor;
  private Cursor mAlbumCursor;
  // File path for tracks opened directly from /storage (mCursor is null for these)
  private volatile String mCurrentFilePath;
  private volatile AudioManager mAudioManager;
  private DataStore<Preferences> serviceDataStore;
  private boolean mServiceInUse = false;
  private volatile boolean mIsSupposedToBePlaying = false;
  private boolean mQueueIsSaveable = true;
  private boolean mPausedByTransientLossOfFocus = false;
  private boolean musicPlaybackActivityInForeground = false;
  MediaSession mMediaSession;
  private int mCardId;
  private int mFailedSongsInARow = 0; // player thread
  private int mMediaMountedCount = 0;
  // The modes the user picked, readable at once by the button that changed them; ExoPlayer gets
  // them on the player thread, and a controller changing them there writes them back.
  private volatile boolean mShuffleEnabled = false;
  private volatile int mRepeatMode = REPEAT_ALL;
  private int mServiceStartId = -1;
  private static volatile MusicPlayerHandler mPlayerHandler;
  private BroadcastReceiver mUnmountReceiver = null;
  private ImageFetcher mImageFetcher;
  private RecentStore mRecentsCache;
  private FavoritesStore mFavoritesCache;
  private boolean mForegroundNotificationStarted;
  private RememberingMediaNotificationProvider mNotificationProvider;

  private static final HashMap<String, Long> notifyChangeIntervals = new HashMap<>();

  private final BroadcastReceiver mIntentReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
          // We handle command intents via onStartCommand
          // The old code tried to call onCreate and initService here, but onCreate is a lifecycle
          // method of Service, no need to call it again.
          LOG.info("mIntentReceiver received intent, delegating to handleCommandIntent()");
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            handleCommandIntent(intent);
          }
        }
      };

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

  // mediaButtonLock removed — no longer manually registering media button receiver.

  /**
   * The player thread, made once: ExoPlayer is bound to it for good, so a second onCreate()
   * (HomeActivity's onCreateSafe() after a permission grant) must not replace it under the player.
   */
  private static MusicPlayerHandler setupMPlayerHandler() {
    if (mPlayerHandler != null && mPlayerHandler.getLooper().getThread().isAlive()) {
      return mPlayerHandler;
    }

    final HandlerThread handlerThread =
        new HandlerThread(
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
   * If we are on the UI thread, we go to a background service and wait for the MusicPlaybackService
   * instance latch to let us know we have an instance ready then calls onCreate.
   */
  public static void onCreateSafe() {
    if (SystemUtils.isUIThread()) {
      SystemUtils.postToHandler(
          SystemUtils.HandlerThreadName.MISC,
          () -> {
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
    mServiceInUse = true;
    IBinder mediaBinder = super.onBind(intent);
    return mediaBinder != null ? mediaBinder : binder;
  }

  @Nullable
  @Override
  public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
    if (mMediaSession == null) {
      setUpMediaSession();
    }
    return mMediaSession;
  }

  @Override
  public void onRebind(final Intent intent) {
    INSTANCE = this;
    initServiceLatch.countDown();
    mServiceInUse = true;
  }

  @Override
  public void onCreate() {
    INSTANCE = this;
    if (D) LOG.info("onCreate: Creating service");
    super.onCreate();
    // Audio focus is handled internally by ExoPlayer in MultiPlayer.
    String permission =
        SystemUtils.hasAndroid13OrNewer()
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;
    boolean mediaReadPermissionsGranted =
        runStrict(
            () ->
                PackageManager.PERMISSION_GRANTED
                    == ContextCompat.checkSelfPermission(this, permission));

    boolean postNotificationsPermissionGranted = true;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      postNotificationsPermissionGranted =
          runStrict(
              () ->
                  PackageManager.PERMISSION_GRANTED
                      == ContextCompat.checkSelfPermission(
                          this, Manifest.permission.POST_NOTIFICATIONS));
    }

    if (!postNotificationsPermissionGranted && HomeActivity.instance() != null) {
      // See HomeActivity::onCreate() for the permission request
      // See HomeActivity::onRequestPermissionsResult() for the service initialization, which calls
      // this again when the permission is granted
      LOG.info(
          "MusicPlaybackService::onCreate() requesting post notifications permission from HomeActivity.instance().requestForPostNotificationPermission()");
      HomeActivity.instance().requestForPostNotificationsPermission();
    }

    if (mediaReadPermissionsGranted) {
      createNotificationChannel();
      mNotificationProvider =
          new RememberingMediaNotificationProvider(new ApolloMediaNotificationProvider(this));
      setMediaNotificationProvider(mNotificationProvider);

      mPlayerHandler = setupMPlayerHandler();
      SystemUtils.exceptionSafePost(mPlayerHandler, this::initService);
    } else {
      LOG.warn(
          "onCreate: service couldn't be initialized correctly, " + permission + " not granted");
    }
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    LOG.info("onStartCommand: Got new intent " + intent + ", startId = " + startId, true);
    mServiceStartId = startId;

    promoteForStart();

    if (intent != null) {
      final String action = intent.getAction();

      if (intent.hasExtra(NOW_IN_FOREGROUND)) {
        musicPlaybackActivityInForeground = intent.getBooleanExtra(NOW_IN_FOREGROUND, false);
        updateNotification();
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

  /**
   * A start through {@code ContextCompat.startForegroundService()} (MusicUtils, Media3's media
   * button handling) must be followed by {@code startForeground()} before the system's deadline,
   * whatever the app's state is by now and whether the service was promoted before: the system
   * decided whether that start was allowed when it was made, and kills the app with
   * ForegroundServiceDidNotStartInTimeException if the promotion never comes
   * (frostwire/frostwire#1371). Promoting a service that is already in the foreground only updates
   * its notification, so Media3's current notification is reused when there is one.
   */
  private void promoteForStart() {
    try {
      createNotificationChannel();
      MediaNotification current =
          mNotificationProvider != null && mMediaSession != null
              ? mNotificationProvider.getCachedNotification(mMediaSession)
              : null;
      if (current != null) {
        startForeground(current.notificationId, current.notification);
      } else {
        Notification tempNotification =
            new Notification.Builder(this, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("FrostWire")
                .setContentText("Loading playback controls...")
                .setSmallIcon(R.drawable.frostwire_notification_flat)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .build();
        startForeground(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, tempNotification);
      }
      mForegroundNotificationStarted = true;
      LOG.info(
          "onStartCommand: Called startForeground() with "
              + (current != null ? "the media" : "a temporary")
              + " notification");
    } catch (Throwable e) {
      // ForegroundServiceStartNotAllowedException: a plain startService() from the background,
      // which has no startForeground() deadline.
      LOG.error("onStartCommand: Failed to call startForeground()", e);
    }
  }

  /**
   * Override onTaskRemoved to prevent ExoPlayer from being accessed on the wrong thread.
   * MediaSessionService.onTaskRemoved() calls isAnySessionPlaying() which accesses ExoPlayer, but
   * this can be called on any thread (e.g., EngineThreadPool). We post to mPlayerHandler to ensure
   * ExoPlayer is only accessed on its dedicated thread.
   */
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    if (mPlayerHandler != null) {
      mPlayerHandler.post(
          () -> {
            try {
              MusicPlaybackService.super.onTaskRemoved(rootIntent);
            } catch (Throwable e) {
              LOG.error("MusicPlaybackService.onTaskRemoved: Error in super call", e);
            }
          });
    } else {
      // Service not fully initialized yet, just call super directly
      try {
        super.onTaskRemoved(rootIntent);
      } catch (Throwable e) {
        LOG.error("MusicPlaybackService.onTaskRemoved: Error calling super", e);
      }
    }
  }

  /**
   * Override onUpdateNotification to keep Media3 away from foreground promotion while the app is
   * backgrounded on Android 12+. We let Media3 manage visible-app promotion and normal foreground
   * teardown, but background updates are posted from the cached notification instead of letting
   * Media3 call ContextCompat.startForegroundService() again.
   */
  @Override
  public void onUpdateNotification(MediaSession session, boolean startInForegroundRequired) {
    MusicForegroundNotificationPolicy.Action action =
        MusicForegroundNotificationPolicy.resolve(
            Build.VERSION.SDK_INT, startInForegroundRequired, isNotificationPromotionAllowed());

    if (action == MusicForegroundNotificationPolicy.Action.UPDATE_VIA_NOTIFICATION_MANAGER) {
      if (updateNotificationFromCache(session)) {
        LOG.info(
            "MusicPlaybackService.onUpdateNotification: updated notification through NotificationManager to avoid Media3 foreground self-start");
        return;
      }
      if (isMedia3StartedInForeground()) {
        LOG.warn(
            "MusicPlaybackService.onUpdateNotification: cached notification unavailable; keeping existing foreground notification unchanged");
        return;
      }
      LOG.warn(
          "MusicPlaybackService.onUpdateNotification: cached notification unavailable while app is backgrounded; falling back to Media3 background update");
      try {
        super.onUpdateNotification(session, false);
        mForegroundNotificationStarted = false;
      } catch (Exception e) {
        LOG.error(
            "MusicPlaybackService.onUpdateNotification: Error updating notification without foreground",
            e);
      }
      return;
    }

    boolean startInForeground =
        action == MusicForegroundNotificationPolicy.Action.PROMOTE_WITH_MEDIA3;
    if (action == MusicForegroundNotificationPolicy.Action.UPDATE_WITH_MEDIA3) {
      LOG.info(
          "MusicPlaybackService.onUpdateNotification: updating notification via Media3 without foreground promotion");
    }
    try {
      super.onUpdateNotification(session, startInForeground);
      mForegroundNotificationStarted = startInForeground;
    } catch (Exception e) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          && e instanceof android.app.ForegroundServiceStartNotAllowedException) {
        LOG.warn(
            "MusicPlaybackService.onUpdateNotification: Cannot start foreground from background on Android 12+");
        if (updateNotificationFromCache(session)) {
          mForegroundNotificationStarted = isMedia3StartedInForeground();
          return;
        }
        try {
          super.onUpdateNotification(session, false);
          mForegroundNotificationStarted = false;
        } catch (Exception retryError) {
          LOG.error(
              "MusicPlaybackService.onUpdateNotification: Error updating notification after foreground start was denied",
              retryError);
        }
      } else {
        LOG.error("MusicPlaybackService.onUpdateNotification: Error updating notification", e);
      }
    }
  }

  private boolean isNotificationPromotionAllowed() {
    return isAppProcessInForeground() || musicPlaybackActivityInForeground;
  }

  private boolean isAppProcessInForeground() {
    try {
      return SystemUtils.isAppInForeground(this);
    } catch (Throwable t) {
      LOG.warn("MusicPlaybackService.isAppProcessInForeground failed: " + t.getMessage());
    }
    return false;
  }

  private boolean updateNotificationFromCache(MediaSession session) {
    if (mNotificationProvider == null) {
      return false;
    }

    MediaNotification cachedNotification = mNotificationProvider.getCachedNotification(session);
    if (cachedNotification == null) {
      return false;
    }

    try {
      createNotificationChannel();
      NotificationManager notificationManager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (notificationManager == null) {
        return false;
      }
      notificationManager.notify(
          cachedNotification.notificationId, cachedNotification.notification);
      return true;
    } catch (Throwable t) {
      LOG.error(
          "MusicPlaybackService.updateNotificationFromCache: failed to post cached notification",
          t);
      return false;
    }
  }

  private boolean isMedia3StartedInForeground() {
    if (mForegroundNotificationStarted) {
      return true;
    }
    try {
      return isPlaybackOngoing();
    } catch (Throwable ignored) {
    }
    return false;
  }

  /**
   * Handles a command intent (media button, notification action, foreground-state change, shutdown)
   * on the player thread, after {@link #initService()}: the player thread runs its tasks in order,
   * so a command posted after initService runs once the service is ready. It used to move to the
   * shared MISC thread and wait there up to 4 s for initialization, holding up everything else on
   * MISC (frostwire/frostwire#1376).
   */
  public void handleCommandIntent(Intent intent) {
    Asserts.checkNotNull(intent);
    final MusicPlayerHandler handler = mPlayerHandler;
    if (handler == null) {
      LOG.warn(
          "handleCommandIntent: the service isn't initialized (no permission?), dropping "
              + intent);
      return;
    }
    if (!serviceInitialized.get()) {
      SystemUtils.exceptionSafePost(handler, this::initService);
    }
    SystemUtils.exceptionSafePost(handler, () -> handleCommand(intent));
  }

  /** {@link #handleCommandIntent(Intent)}'s work; on the player thread, after initService. */
  private void handleCommand(Intent intent) {
    final String action = intent.getAction();
    final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

    if (action == null) {
      LOG.info("MusicPlaybackService::handleCommandIntent: nothing to be done here, exiting.");
      return;
    }

    LOG.info(
        "MusicPlaybackService::handleCommandIntent: action = " + action + ", command = " + command,
        true);

    if (SHUTDOWN_ACTION.equals(action)) {
      boolean exiting = intent.hasExtra("force");
      // On the player thread, so stopping the player runs at once instead of being posted.
      runOnPlayerThread(() -> releaseServiceUiAndStop(exiting));
      return;
    }

    if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
      gotoNext();
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
      // On the player thread, so no player event can mark it playing again before the check.
      runOnPlayerThread(
          () -> {
            pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            releaseServiceUiAndStop(false);
          });
    } else if (REPEAT_ACTION.equals(action)) {
      cycleRepeat();
    }
  }

  @Override
  public void onDestroy() {
    LOG.info("onDestroy() destroying MusicPlaybackService");
    super.onDestroy();
    mForegroundNotificationStarted = false;
    if (mNotificationProvider != null) {
      mNotificationProvider.clear();
      mNotificationProvider = null;
    }

    try {
      final Intent audioEffectsIntent =
          new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
      audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
      audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
      sendBroadcast(audioEffectsIntent);
    } catch (Throwable ignored) {
    }

    if (mPlayerHandler != null) {
      mPlayerHandler.removeCallbacksAndMessages(null);
    }

    mQueue = null;
    if (mPlayer != null) {
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
      } catch (Throwable ignored) {
      }
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
    synchronized (safePostLock) {
      pendingSafePosts.clear();
    }
    INSTANCE = null;
    mServiceInUse = false;
  }

  public void updateNotification() {
    updateMediaSessionState(PLAYSTATE_CHANGED);
    updateMediaSessionState(META_CHANGED);
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel =
          nm.getNotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID);
      if (channel == null) {
        channel =
            new NotificationChannel(
                Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID,
                "FrostWire Player",
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
      }
    }
  }

  /** Stops the player, not the foreground service; the queue stays. */
  private void stopPlayer() {
    LOG.info("stopPlayer()");
    mIsSupposedToBePlaying = false;
    final MultiPlayer player = mPlayer;
    if (player != null) {
      player.stop();
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
        mSimplePlayer.setMediaItem(MediaItem.fromUri(Uri.parse(path)));
        mSimplePlayer.prepare();
        mSimplePlayer.play();
        mSimplePlayerPlayingFile = path;
        mSimplePlayer.addListener(
            new Player.Listener() {
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
      } catch (Throwable ignored) {
      }
      notifySimpleStopped(mSimplePlayerPlayingFile);
      mSimplePlayer = null;
      mSimplePlayerPlayingFile = null;
    }
  }

  // prepareAudioFocusRequest() removed — ExoPlayer in MultiPlayer handles audio focus internally.

  private void setUpMediaSession() {
    if (mMediaSession != null) {
      return;
    }
    final MultiPlayer player = mPlayer;
    if (player == null) {
      LOG.warn("setUpMediaSession() called before ExoPlayer is ready — deferring");
      return;
    }
    try {
      mMediaSession =
          new MediaSession.Builder(this, player.mExoPlayer)
              .setId("FrostWireApollo")
              .setSessionActivity(AudioPlayerNotificationActivity.createPendingIntent(this))
              .build();
      addSession(mMediaSession);
      LOG.info("setUpMediaSession() MediaSession created and added to service");
    } catch (Throwable t) {
      LOG.error("setUpMediaSession() error: " + t.getMessage(), t);
    }
  }

  private void releaseServiceUiAndStop(boolean force) {
    if (mPlayerHandler == null || (!force && isPlaying()) || mPausedByTransientLossOfFocus) {
      LOG.info("releaseServiceUiAndStop(force=" + force + ") aborted: isPlaying()=" + isPlaying());
      return;
    }

    if (force && isPlaying()) {
      LOG.info("releaseServiceUiAndStop(force=true) : isPlaying()=" + isPlaying());
      stopPlayer();
    }
    if (D) LOG.info("Nothing is playing anymore, releasing notification");
    updateMediaSessionState(PLAYSTATE_STOPPED);
    if (!mServiceInUse || force) {
      unregisterReceivers();
      saveQueue(true);
      stopSelf(mServiceStartId);
      stop(false);
    }
    if (force && serviceDataStore != null) {
      DataStoreManager.putInt(serviceDataStore, "curpos", -1);
    }
    // Only the player's own messages: removing every callback also dropped posted queue changes
    // and the change notifications ChangeCoalescer waits for, which then never went out again.
    final Handler handler = mPlayerHandler;
    if (handler != null) {
      handler.removeMessages(FADE_UP);
      handler.removeMessages(FADE_DOWN);
      handler.removeMessages(SERVER_DIED);
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

  private void initService() {
    if (mPlayerHandler == null) {
      throw new RuntimeException("check your logic, can't init service without mPlayerHandler.");
    }
    if (serviceInitialized.get()) {
      // onCreate() and handleCommandIntent() can both post this; one player is enough.
      return;
    }
    LOG.info("initService() invoked", true);

    mFavoritesCache = FavoritesStore.getInstance(this);
    mRecentsCache = RecentStore.getInstance(this);

    mImageFetcher = ImageFetcher.getInstance(this);

    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    serviceDataStore = DataStoreManager.getServiceDataStore();
    mCardId = getCardId();

    registerExternalStorageListener();

    attachPlayer(MultiPlayer.buildExoPlayer(this, mPlayerHandler.getLooper()));
    setUpMediaSession();

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

    // WakeLock initialization removed — ExoPlayer handles it via WAKE_MODE_LOCAL.

    restoreQueue();
    notifyChange(QUEUE_CHANGED);
    notifyChange(META_CHANGED);

    updateNotification();
    serviceInitialized.set(true);
    initServiceLatch.countDown();
    flushPendingSafePosts();
  }

  /**
   * On the player thread at start: the saved queue, then the repeat and shuffle preferences. In
   * this order: setting a mode saves the queue position, which before the reload is none, and every
   * restart forgot where the user was.
   */
  private void restoreQueue() {
    reloadQueue();
    MusicPlaybackService.initRepeatModeAndShuffleTask(this);
  }

  /**
   * @return A card ID used to save and restore playlists, i.e., the queue.
   */
  private int getCardId() {
    int mCardId = -1;
    try {
      final ContentResolver resolver = getContentResolver();
      Cursor cursor =
          resolver.query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
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

  /** Called when we receive a ACTION_MEDIA_EJECT notification. */
  private void closeExternalStorageFiles() {
    stop(true);
    MusicUtils.requestMusicPlaybackServiceShutdown(this);
  }

  /**
   * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
   * closeExternalStorageFiles() if the external media is going to be ejected, so applications can
   * clean up any files they have open.
   */
  private void registerExternalStorageListener() {
    if (mUnmountReceiver == null) {
      mUnmountReceiver =
          new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
              final String action = intent.getAction();
              if (action == null) {
                return;
              }
              // The queue is ExoPlayer's, so it is only touched on the player thread.
              if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                runOnPlayerThread(
                    () -> {
                      saveQueue(true);
                      mQueueIsSaveable = false;
                      closeExternalStorageFiles();
                    });
              } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                runOnPlayerThread(
                    () -> {
                      mMediaMountedCount++;
                      mCardId = getCardId();
                      if (queueSnapshot().size() == 0) {
                        reloadQueue();
                      }
                      mQueueIsSaveable = true;
                      notifyChange(QUEUE_CHANGED);
                      notifyChange(META_CHANGED);
                    });
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
        LOG.error(
            "registerExternalStorageListener() could not register ACTION_MEDIA_EJECT|MOUNTED intent filter",
            notABiggie);
      }
    }
  }

  /** Stops playback; the queue stays, and playing again resumes it. */
  private void stop(final boolean removeNotification) {
    if (D) LOG.info("Stopping playback, removeNotification = " + removeNotification);
    stopPlayer();
  }

  /** True on the player thread, the only thread that uses ExoPlayer and changes the queue. */
  private static boolean onPlayerThread() {
    final Handler handler = mPlayerHandler;
    return handler != null && handler.getLooper().isCurrentThread();
  }

  /** Runs {@code task} on the player thread: now when called there, else after what's posted. */
  static void runOnPlayerThread(Runnable task) {
    if (onPlayerThread()) {
      task.run();
    } else {
      safePost(task);
    }
  }

  /**
   * Runs a queue change that returns a count on the player thread. A caller off the main thread
   * waits for it, at most {@link #QUEUE_CHANGE_TIMEOUT_MS}, so what it reads next shows the change
   * (NowPlayingCursor reads the queue right after removing songs). The main thread must not wait:
   * it gets {@code estimate}, worked out from the queue's snapshot before the change.
   */
  private int changeQueue(IntSupplier change, IntSupplier estimate) {
    if (onPlayerThread()) {
      return change.getAsInt();
    }
    final int expected = estimate.getAsInt();
    final FutureTask<Integer> task = new FutureTask<>(change::getAsInt);
    safePost(task);
    if (SystemUtils.isUIThread()) {
      return expected;
    }
    try {
      return task.get(QUEUE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      LOG.warn("changeQueue() did not finish on the player thread: " + e);
    }
    return expected;
  }

  /** What the queue holds now; any thread. */
  private PlaybackQueue.Snapshot queueSnapshot() {
    final PlaybackQueue queue = mQueue;
    return queue != null ? queue.snapshot() : PlaybackQueue.Snapshot.EMPTY;
  }

  /**
   * Makes {@code exoPlayer} the service's player and its playlist the queue. On the player thread,
   * which must be {@code exoPlayer}'s application thread.
   */
  void attachPlayer(ExoPlayer exoPlayer) {
    mPlayer = new MultiPlayer(exoPlayer);
    final PlaybackQueue queue =
        new PlaybackQueue(
            exoPlayer,
            new PlaybackQueue.Listener() {
              @Override
              public void onQueueChanged() {
                notifyChange(QUEUE_CHANGED);
              }

              @Override
              public void onSongChanged(long audioId) {
                songChanged(audioId);
              }
            });
    // Every queued song carries its title, so controllers can show the whole queue (#1452).
    queue.setMetadataLookup(ids -> songMetadata(this, ids));
    mQueue = queue;
  }

  /** Songs per MediaStore query when looking up queued songs' metadata. */
  private static final int METADATA_BATCH = 500;

  /**
   * Title, artist, album and album art of the MediaStore songs {@code ids}, for the queue's media
   * items. One query per {@value #METADATA_BATCH} songs; songs MediaStore doesn't know are left
   * out. Off the main thread.
   */
  static Map<Long, MediaMetadata> songMetadata(final Context context, final long[] ids) {
    final Map<Long, MediaMetadata> found = new HashMap<>();
    for (int from = 0; from < ids.length; from += METADATA_BATCH) {
      final StringBuilder in = new StringBuilder();
      for (int i = from; i < Math.min(ids.length, from + METADATA_BATCH); i++) {
        if (in.length() > 0) {
          in.append(',');
        }
        in.append(ids[i]);
      }
      try (Cursor cursor =
          context
              .getContentResolver()
              .query(
                  MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                  new String[] {
                    BaseColumns._ID,
                    AudioColumns.TITLE,
                    AudioColumns.ARTIST,
                    AudioColumns.ALBUM,
                    AudioColumns.ALBUM_ID
                  },
                  BaseColumns._ID + " IN (" + in + ")",
                  null,
                  null)) {
        while (cursor != null && cursor.moveToNext()) {
          final MediaMetadata.Builder metadata =
              new MediaMetadata.Builder()
                  .setTitle(cursor.getString(1))
                  .setArtist(cursor.getString(2))
                  .setAlbumTitle(cursor.getString(3));
          final long albumId = cursor.getLong(4);
          if (albumId > 0) {
            metadata.setArtworkUri(
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId));
          }
          found.put(cursor.getLong(0), metadata.build());
        }
      } catch (Throwable t) {
        LOG.warn("songMetadata: MediaStore query failed: " + t.getMessage());
      }
    }
    return found;
  }

  /** On the player thread: another song is current, so its metadata, Recent and notification. */
  private void songChanged(long audioId) {
    if (audioId >= 0) {
      mCurrentFilePath = null;
      updateCursor(audioId);
    } else {
      closeCursor();
      if (queueSnapshot().size() == 0) {
        mCurrentFilePath = null;
      }
    }
    notifyChange(META_CHANGED);
    updateNotification();
  }

  /**
   * On the player thread: removes songs from the queue, and stops playback when none are left. A
   * stopped player with nothing queued takes Media3's notification and the foreground state away.
   */
  private int removeFromQueue(ToIntFunction<PlaybackQueue> removal) {
    final PlaybackQueue queue = mQueue;
    if (queue == null) {
      return 0;
    }
    final int removed = removal.applyAsInt(queue);
    if (removed > 0 && queue.snapshot().size() == 0) {
      stop(true);
    }
    return removed;
  }

  /**
   * Removes every instance of the tracks in {@code ids} from the queue at once. When the playing
   * track is among them, playback moves straight to the first track that stays, and listeners get
   * one queue change. Waits for the player thread unless called on the main thread; see {@link
   * #changeQueue}.
   *
   * @return how many queue entries were removed
   */
  public int removeTracks(final long[] ids) {
    return changeQueue(
        () -> removeFromQueue(queue -> queue.remove(ids)), () -> queueSnapshot().count(ids));
  }

  /**
   * Removes every song but the playing one from the queue; the playing song keeps playing. Posted
   * to the player thread.
   */
  public void clearQueue() {
    runOnPlayerThread(() -> removeFromQueue(PlaybackQueue::removeAllButCurrent));
  }

  /**
   * @param trackId The track ID
   */
  private void updateCursor(final long trackId) {
    updateCursor("_id=" + trackId, null);
  }

  private void updateCursor(final String selection, final String[] selectionArgs) {
    closeCursor();
    mCursor =
        openCursorAndGoToFirst(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, PROJECTION, selection, selectionArgs);
    updateAlbumCursor();
  }

  private void updateCursor(final Uri uri) {
    closeCursor();

    Cursor cursor = openCursorAndGoToFirst(uri, PROJECTION, null, null);

    synchronized (cursorLock) {
      mCursor = cursor;
    }
    updateAlbumCursor();
  }

  private void updateAlbumCursor() {
    long albumId = getAlbumId();
    if (albumId >= 0) {
      mAlbumCursor =
          openCursorAndGoToFirst(
              MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
              ALBUM_PROJECTION,
              "_id=" + albumId,
              null);
    } else {
      mAlbumCursor = null;
    }
  }

  private Cursor openCursorAndGoToFirst(
      Uri uri, String[] projection, String selection, String[] selectionArgs) {
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

  /** Notify the change-receivers that something has changed. */
  private void notifyChange(final String change) {
    LOG.info("notifyChange(" + change + ") trying...");
    Runnable notifyChangeTaskRunnable = () -> MusicPlaybackService.notifyChangeTask(this, change);
    long interval = 250;
    try {
      interval = notifyChangeIntervals.get(change);
    } catch (Throwable t) {
      LOG.error(
          "notifyChange() change="
              + change
              + " interval not defined in notifyChangeIntervals, defaulting to 250ms",
          t);
    }
    // Coalesce instead of dropping: a throttle that dropped repeats left the screen on the
    // previous song and skipped saving the queue (GitHub #1453).
    notifyChangeCoalescer.submit(change, interval, notifyChangeTaskRunnable);
  }

  /** Runs change notifications on the player thread; repeats within an interval collapse. */
  private final ChangeCoalescer notifyChangeCoalescer =
      new ChangeCoalescer(
          (task, delay) -> {
            final Handler handler = mPlayerHandler;
            if (handler == null) {
              return false;
            }
            if (delay <= 0) {
              SystemUtils.exceptionSafePost(handler, task);
              return true;
            }
            return handler.postDelayed(() -> SystemUtils.exceptionSafePost(handler, task), delay);
          },
          SystemClock::uptimeMillis);

  private static void notifyChangeTask(MusicPlaybackService musicPlaybackService, String change) {
    LOG.info("notifyChangeTask(" + change + ")!");
    // Update the lock screen controls
    musicPlaybackService.updateMediaSessionState(change);
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
    boolean favorite = musicPlaybackService.isFavorite();
    intent.putExtra("id", audioId);
    intent.putExtra("artist", artistName);
    intent.putExtra("album", albumName);
    intent.putExtra("track", trackName);
    intent.putExtra("playing", isPlaying);
    intent.putExtra("isfavorite", favorite);
    musicPlaybackService.sendBroadcast(intent);
    final Intent musicIntent = new Intent(intent);
    musicIntent.setAction(change.replace(APOLLO_PACKAGE_NAME, MUSIC_PACKAGE_NAME));
    musicPlaybackService.sendBroadcast(musicIntent);
    if (META_CHANGED.equals(change)) {
      if (audioId < 0) {
        return; // the queue emptied, or a file MediaStore doesn't know
      }
      // Increase the play count for favorite songs.
      if (musicPlaybackService.mFavoritesCache != null
          && musicPlaybackService.mFavoritesCache.getSongId(audioId) != null) {
        musicPlaybackService.mFavoritesCache.addSongId(audioId, trackName, albumName, artistName);
      }
      // Add the track to the recently played list.
      recentsStoreAddSongIdTask(musicPlaybackService);
    } else if (QUEUE_CHANGED.equals(change)) {
      musicPlaybackService.saveQueue(true);
    } else {
      musicPlaybackService.saveQueue(false);
    }
  }

  /**
   * The duration Recent records for the track that just opened: MediaStore's, else the player's.
   * Recent is written on META_CHANGED, when the player still holds the previous track's duration
   * (an explicit open) or -1 (an automatic advance), and RecentStore drops songs without one, so
   * auto-advanced songs never reached Recent (GitHub #1451).
   */
  static long recentDuration(long mediaStoreDuration, long playerDuration) {
    return mediaStoreDuration > 0 ? mediaStoreDuration : playerDuration;
  }

  /** The current track's duration in milliseconds from MediaStore, or 0 if unknown. */
  long getTrackMediaStoreDuration() {
    if (mCursor == null || mCursor.isClosed()) {
      return 0;
    }
    try {
      int column = mCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
      return column >= 0 && !mCursor.isNull(column) ? mCursor.getLong(column) : 0;
    } catch (Throwable e) {
      return 0;
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
    long duration =
        recentDuration(
            musicPlaybackService.getTrackMediaStoreDuration(), musicPlaybackService.duration());
    if (musicPlaybackService.mRecentsCache != null) {
      musicPlaybackService.mRecentsCache.addSongId(
          songId,
          songName,
          artistName,
          albumName,
          duration,
          musicPlaybackService.getString(R.string.unknown));
    }
  }

  /** Notify the change-receivers that simple player has stopped */
  private void notifySimpleStopped(final String path) {
    if (D) LOG.info("notifySimplePlayerStopped");

    final Intent intent = new Intent(SIMPLE_PLAYSTATE_STOPPED);
    intent.putExtra("path", path);
    sendBroadcast(intent);
  }

  /**
   * Updates the MediaSession playback state and metadata.
   *
   * @param what The broadcast event that triggered the update (e.g. PLAYSTATE_CHANGED,
   *     META_CHANGED)
   */
  void updateMediaSessionState(final String what) {
    if (what == null) {
      LOG.info("updateMediaSessionState() skipped — what is null");
      return;
    }
    LOG.info("updateMediaSessionState(what=" + what + ")");

    switch (what) {
      case PLAYSTATE_CHANGED:
      case POSITION_CHANGED:
      case PLAYSTATE_STOPPED:
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
   * Pushes current track metadata into the MediaSession via a refreshed MediaItem. Media3's
   * MediaSession reads metadata directly from ExoPlayer's current MediaItem — the system lock
   * screen and Bluetooth devices receive it automatically.
   */
  private void updateMediaSessionMetadata() {
    final MultiPlayer player = mPlayer;
    if (mMediaSession == null || player == null) return;
    try {
      final MediaItem current = player.mExoPlayer.getCurrentMediaItem();
      String path = getPath();
      if (current == null || path == null) return;
      long albumId = getAlbumId();
      androidx.media3.common.MediaMetadata.Builder metaBuilder =
          new androidx.media3.common.MediaMetadata.Builder()
              .setTitle(getTrackName())
              .setArtist(getArtistName())
              .setAlbumTitle(getAlbumName())
              .setAlbumArtist(getAlbumArtistName());
      Uri artworkUri = null;
      if (albumId > 0) {
        artworkUri =
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId);
      }
      if (artworkUri == null && path != null) {
        File artFile = new File(path.substring(0, path.lastIndexOf('.')) + ".art");
        if (artFile.exists()) {
          artworkUri = Uri.fromFile(artFile);
        }
      }
      if (artworkUri != null) {
        metaBuilder.setArtworkUri(artworkUri);
      }
      final androidx.media3.common.MediaMetadata metadata = metaBuilder.build();
      if (metadata.equals(current.mediaMetadata)) {
        return; // a replace re-sends the whole queue to every controller
      }
      // Same URI and media id: ExoPlayer keeps playing it, and it stays the same queue entry.
      player.mExoPlayer.replaceMediaItem(
          player.mExoPlayer.getCurrentMediaItemIndex(),
          current.buildUpon().setMediaMetadata(metadata).build());
      LOG.info("updateMediaSessionMetadata() pushed metadata for: " + getTrackName());
    } catch (Throwable t) {
      LOG.error("updateMediaSessionMetadata() error: " + t.getMessage(), t);
    }
  }

  /**
   * Saves the queue, from its snapshot, in the format Apollo always used: "queue" holds the audio
   * ids, "curpos" the current song's position in it.
   *
   * @param full True to save the songs too, not only the position and modes
   */
  private void saveQueue(final boolean full) {
    if (!mQueueIsSaveable || serviceDataStore == null) {
      return;
    }
    final PlaybackQueue.Snapshot queue = queueSnapshot();
    if (full) {
      DataStoreManager.putString(serviceDataStore, "queue", encodeQueue(queue.ids()));
      DataStoreManager.putInt(serviceDataStore, "cardid", mCardId);
    }
    DataStoreManager.putInt(serviceDataStore, "curpos", queue.index());
    final MultiPlayer player = mPlayer;
    if (player != null) {
      DataStoreManager.putLong(serviceDataStore, "seekpos", player.position());
    }
    DataStoreManager.putInt(serviceDataStore, "repeatmode", mRepeatMode);
    DataStoreManager.putBoolean(serviceDataStore, "shufflemode", mShuffleEnabled);
  }

  /**
   * Each id as hexadecimal digits, lowest digit first, then ';'. A queue with a file MediaStore
   * doesn't know (id -1) can't be restored, so it saves as no queue.
   */
  static String encodeQueue(long[] ids) {
    final StringBuilder q = new StringBuilder();
    for (long n : ids) {
      if (n < 0) {
        return "";
      }
      if (n == 0) {
        q.append('0');
      }
      while (n != 0) {
        q.append(HEX_DIGITS[(int) (n & 0xf)]);
        n >>>= 4;
      }
      q.append(';');
    }
    return q.toString();
  }

  /**
   * The ids {@link #encodeQueue} wrote; none if anything else is in there, or a negative id:
   * earlier versions saved a file MediaStore doesn't know as -1, which can't be played again.
   */
  static long[] decodeQueue(@Nullable String q) {
    if (q == null || q.length() < 2) {
      return new long[0];
    }
    final long[] ids = new long[q.length() / 2];
    int count = 0;
    long n = 0;
    int shift = 0;
    for (int i = 0; i < q.length(); i++) {
      final char c = q.charAt(i);
      if (c == ';') {
        if (n < 0) {
          return new long[0];
        }
        ids[count++] = n;
        n = 0;
        shift = 0;
      } else if (c >= '0' && c <= '9') {
        n += (long) (c - '0') << shift;
        shift += 4;
      } else if (c >= 'a' && c <= 'f') {
        n += (long) (10 + c - 'a') << shift;
        shift += 4;
      } else {
        return new long[0];
      }
    }
    return Arrays.copyOf(ids, count);
  }

  /**
   * Reloads the queue as the user left it the last time they stopped using Apollo, paused at the
   * song and position they were at. On the player thread.
   */
  private void reloadQueue() {
    final PlaybackQueue queue = mQueue;
    if (serviceDataStore == null || queue == null) {
      return;
    }
    String q = null;
    int id = mCardId;
    if (DataStoreManager.containsKey(serviceDataStore, "cardid")) {
      id = DataStoreManager.getInt(serviceDataStore, "cardid", ~mCardId);
    }
    if (id == mCardId) {
      q = DataStoreManager.getString(serviceDataStore, "queue", "");
    }
    final long[] ids = decodeQueue(q);
    final int pos = DataStoreManager.getInt(serviceDataStore, "curpos", 0);
    if (pos < 0 || pos >= ids.length) {
      return;
    }
    final long seekpos = DataStoreManager.getLong(serviceDataStore, "seekpos", 0);
    // The repeat and shuffle modes come from the preferences right after (initService).
    queue.open(ids, pos, Math.max(seekpos, 0));
    if (D) LOG.info("restored queue of " + ids.length + " at " + pos + ", " + seekpos + " ms");

    if (mCursor == null) {
      // MediaStore may not be ready this early after boot; ask again for the song's details.
      mPlayerHandler.postDelayed(
          () -> {
            final long audioId = getAudioId();
            if (mCursor == null && audioId >= 0) {
              updateCursor(audioId);
              notifyChange(META_CHANGED);
            }
          },
          500);
    }
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
   * Plays a file another app or a download handed over: as its MediaStore song when it has one,
   * else as a queue of only that file. On the player thread.
   */
  private void openTransientFile(String path) {
    final PlaybackQueue queue = mQueue;
    if (queue == null) {
      return;
    }
    // content://com.frostwire.android.fileprovider/external/Download/FrostWire/...audiofile.ext
    if (path.startsWith("content://com.frostwire.android.fileprovider/external")) {
      path =
          path.replace(
              "content://com.frostwire.android.fileprovider/external", "/storage/emulated/0");
    }
    final long id = audioIdForPath(path);
    if (id != -1) {
      open(new long[] {id}, 0);
      play();
      return;
    }
    if (path.startsWith("/storage")) {
      path = UrlUtils.decode(path);
      LOG.info("MusicPlaybackService.openTransientFile decoded path -> " + path);
    }
    mCurrentFilePath = path;
    queue.openUri(MultiPlayer.resolveUri(path));
    notifyChange(META_CHANGED);
    play();
  }

  /** The MediaStore audio id of a file handed over by path or content URI, or -1. */
  private long audioIdForPath(String path) {
    long id = -1;
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
      String prefix = path.substring(0, path.indexOf("/storage"));
      LOG.info("MusicPlaybackService::audioIdForPath prefix=" + prefix, true);
      String fixedPath = path.replace(prefix, "");
      try {
        fixedPath = URLDecoder.decode(fixedPath, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        LOG.error("MusicPlaybackService::audioIdForPath decoding error " + e.getMessage(), e, true);
      }
      LOG.info("MusicPlaybackService::audioIdForPath - updated path -> " + fixedPath);
      id = getFileIdFromPath(fixedPath, Uri.parse("content://media/external/downloads"), false);
      if (id == -1) {
        id = getFileIdFromPath(fixedPath, Uri.parse("content://media/internal/downloads"), false);
      }
      LOG.info("MusicPlaybackService::audioIdForPath got an ID? id = " + id);
    }

    // Any other path: a file MediaStore may know by its path.
    if (id == -1 && !path.startsWith("/storage") && !path.startsWith("content://")) {
      try {
        id = getFileIdFromPath(path, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
      } catch (Throwable t) {
        LOG.error("MusicPlaybackService::audioIdForPath MediaStore lookup failed", t);
      }
    }
    return id;
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
   * Removes all instances of the track with the given ID from the queue, as {@link
   * #removeTracks(long[])}.
   *
   * @param id The id to be removed
   * @return how many instances of the track were removed
   */
  public int removeTrack(final long id) {
    return removeTracks(new long[] {id});
  }

  /**
   * Removes the queue entries {@code first..last}, clamped to the queue. If the playing song is
   * among them, playback moves to the first song after them. Waits for the player thread unless
   * called on the main thread; see {@link #changeQueue}.
   *
   * @param first The first file to be removed
   * @param last The last file to be removed
   * @return the number of tracks deleted
   */
  public int removeTracks(final int first, final int last) {
    return changeQueue(
        () -> removeFromQueue(queue -> queue.removeRange(first, last)),
        () -> queueSnapshot().countRange(first, last));
  }

  /**
   * Returns the position in the queue
   *
   * @return the current position in the queue, or -1 when it is empty; from its snapshot, so any
   *     thread
   */
  public int getQueuePosition() {
    return queueSnapshot().index();
  }

  /**
   * Returns the path to current song
   *
   * @return The path to the current song
   */
  @SuppressWarnings("deprecation")
  public String getPath() {
    synchronized (this) {
      if (mCursor != null && !mCursor.isClosed()) {
        return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.DATA));
      }
      // For tracks opened directly from /storage (e.g. fresh YouTube downloads
      // not yet indexed by MediaStore), return the stored file path
      return mCurrentFilePath;
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
    // synchronized (cursorLock) {
    if (mCursor == null || mCursor.isClosed()) {
      return null;
    }
    try {
      return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
    } catch (Throwable e) {
      e.printStackTrace();
      return "---";
    }
    // }
  }

  /**
   * Returns the artist name
   *
   * @return The current song artist name
   */
  public String getArtistName() {
    try {
      // synchronized (cursorLock) {
      if (mCursor == null || mCursor.isClosed()) {
        return null;
      }
      return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
      // }
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
      // synchronized (cursorLock) {
      if (mAlbumCursor == null || mAlbumCursor.isClosed()) {
        return null;
      }
      return mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(AlbumColumns.ARTIST));
      // }
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
    // synchronized (cursorLock) {
    if (mCursor == null || mCursor.isClosed()) {
      return -1;
    }
    return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST_ID));
    // }
  }

  /**
   * Returns the current audio ID
   *
   * @return The current track ID, or -1; from the queue's snapshot, so any thread
   */
  public long getAudioId() {
    return queueSnapshot().audioId();
  }

  /**
   * Returns the current audio for simple player ID
   *
   * @return The current simple player track ID
   */
  public long getCurrentSimplePlayerAudioId() {
    // synchronized (cursorLock) {
    long id = -1;
    if (mSimplePlayerPlayingFile != null) {
      id = getFileIdFromPath(mSimplePlayerPlayingFile, MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
      if (id == -1) {
        id =
            getFileIdFromPath(
                mSimplePlayerPlayingFile, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
      }
    }
    return id;
    // }
  }

  /**
   * Uses exact data path match when querying the media store db. See getFileIdFromPath to use LIKE
   * %<my path>% by passing exact=false parameter.
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
    Cursor cursor =
        getContentResolver().query(uri, SIMPLE_PROJECTION, selectionClause, selectionArgs, null);
    if (cursor != null && cursor.getCount() > 0) {
      cursor.moveToNext();
      long id = cursor.getLong(IDCOLIDX);
      cursor.close();
      return id;
    }
    return -1;
  }

  @SuppressWarnings("unused")
  /**
   * Use this to print all the _id and _data of the files in Downloads as stored by the Media Store
   * Useful for debugging
   */
  private void debugPrintAllFilesInDownloads() {
    ContentResolver contentResolver = getContentResolver();
    Uri downloadsExternalUri = Uri.parse("content://media/external/downloads");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      downloadsExternalUri = MediaStore.Downloads.getContentUri("external");
    }
    Cursor query =
        contentResolver.query(
            downloadsExternalUri,
            SIMPLE_PROJECTION,
            "_data LIKE ?",
            new String[] {"%/Download/%"},
            null,
            null);
    if (query != null && query.moveToFirst()) {
      do {
        LOG.info(
            "MusicPlaybackService::debugPrintAllFilesInDownloads _id: "
                + query.getLong(0)
                + " _data:"
                + query.getString(1));
      } while (query.moveToNext());
      query.close();
    }
  }

  /**
   * Seeks the current track to a specific time
   *
   * @param position The time to seek to
   * @return The time to play the track at, or -1 when nothing is queued
   */
  public long seek(long position) {
    final MultiPlayer player = mPlayer;
    if (player == null || queueSnapshot().size() == 0) {
      return -1;
    }
    final long duration = player.duration();
    final long target = Math.max(0, duration > 0 ? Math.min(position, duration) : position);
    player.seek(target);
    notifyChange(POSITION_CHANGED);
    return target;
  }

  /**
   * Returns the current position in time of the current track
   *
   * @return The current playback position in milliseconds
   */
  public long position() {
    final MultiPlayer player = mPlayer;
    return player != null && queueSnapshot().size() > 0 ? player.position() : -1;
  }

  /**
   * Returns the full duration of the current track
   *
   * @return The duration of the current track in milliseconds, or -1 while unknown
   */
  public long duration() {
    final MultiPlayer player = mPlayer;
    return player != null && queueSnapshot().size() > 0 ? player.duration() : -1;
  }

  /**
   * Returns the queue
   *
   * @return The queue as a long[]; from its snapshot, so any thread
   */
  public long[] getQueue() {
    return queueSnapshot().ids();
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
   * @return True if nothing is queued, or the player is stopped or failed
   */
  public boolean isStopped() {
    final MultiPlayer player = mPlayer;
    return player == null || queueSnapshot().size() == 0 || player.isIdle();
  }

  /** True if the current track is a "favorite", false otherwise */
  public boolean isFavorite() {
    if (mFavoritesCache != null) {
      final Long id = mFavoritesCache.getSongId(getAudioId());
      return id != null;
    }
    return false;
  }

  /**
   * Makes {@code list} the queue and plays it from {@code position}, or from the first song in
   * playback order when {@code position} is -1. Posted to the player thread.
   *
   * @param list The list of tracks to open
   * @param position The position to start playback at
   */
  public void open(final long[] list, final int position) {
    LOG.info("MusicPlaybackService.open at position=" + position, true);
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue == null) {
            return;
          }
          final long oldId = queue.snapshot().audioId();
          queue.open(list, position, 0);
          launchPlayerActivity();
          if (queue.snapshot().audioId() != oldId) {
            play();
          }
        });
  }

  private void launchPlayerActivity() {
    try {
      Intent i = new Intent(this, AudioPlayerActivity.class);
      i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    } catch (Throwable t) {
      LOG.error("launchPlayerActivity() failed: " + t.getMessage(), t);
    }
  }

  /**
   * Stops playback and requests service shutdown along with the removal of the player notification
   */
  public void stop() {
    stop(true);
  }

  /**
   * Starts playback, from the first song again when the queue had finished. Posted to the player
   * thread.
   */
  public void play() {
    stopSimplePlayer();
    runOnPlayerThread(
        () -> {
          final MultiPlayer player = mPlayer;
          final PlaybackQueue queue = mQueue;
          if (player == null || queue == null || queue.snapshot().size() == 0) {
            return;
          }
          queue.restartIfEnded();
          player.start();
          final Handler handler = mPlayerHandler;
          if (handler != null) {
            handler.removeMessages(FADE_DOWN);
            handler.sendEmptyMessage(FADE_UP);
          }
          mIsSupposedToBePlaying = true;
          notifyChange(PLAYSTATE_CHANGED);
          updateNotification();
        });
  }

  /**
   * Plays a file another app or a download handed over. Posted to the player thread.
   *
   * @return false without a path
   */
  public boolean playTransientFile(String path) {
    if (path == null) {
      return false;
    }
    runOnPlayerThread(() -> openTransientFile(path));
    return true;
  }

  /** Temporarily pauses playback. */
  public void pause() {
    if (D) LOG.info("Pausing playback");
    // At once: a stop that follows reads it (releaseServiceUiAndStop).
    mIsSupposedToBePlaying = false;
    runOnPlayerThread(
        () -> {
          final Handler handler = mPlayerHandler;
          if (handler != null) {
            handler.removeMessages(FADE_UP);
          }
          final MultiPlayer player = mPlayer;
          if (player != null) {
            player.pause();
          }
          notifyChange(PLAYSTATE_CHANGED);
        });
  }

  /**
   * Goes to the next song exactly as a Bluetooth headset's or the lock screen's "next" does: in
   * shuffle order when shuffling, wrapping with repeat-all, nothing at the end without repeat.
   * Doesn't start or pause playback. Posted to the player thread.
   */
  public void gotoNext() {
    if (D) LOG.info("Going to next track");
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.next();
          }
        });
  }

  /**
   * Goes to the previous song exactly as a controller's "previous" does; after the first {@link
   * #REWIND_INSTEAD_PREVIOUS_THRESHOLD} ms of a song it restarts the song instead. Doesn't start or
   * pause playback. Posted to the player thread.
   */
  public void gotoPrev() {
    if (D) LOG.info("Going to previous track");
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.previous();
          }
        });
  }

  /** Toggles the current song as a favorite. */
  public void toggleFavorite() {
    if (mFavoritesCache != null) {
      synchronized (this) {
        mFavoritesCache.toggleSong(getAudioId(), getTrackName(), getAlbumName(), getArtistName());
      }
    }
  }

  /**
   * Moves an item in the queue from one position to another; the playing song keeps playing. Posted
   * to the player thread.
   *
   * @param from The position the item is currently at
   * @param to The position the item is being moved to
   */
  public void moveQueueItem(final int from, final int to) {
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.move(from, to);
          }
        });
  }

  /**
   * Sets the repeat mode. {@link #getRepeatMode()} returns it at once; ExoPlayer gets it on the
   * player thread.
   *
   * @param repeatMode The repeat mode to use
   */
  public void setRepeatMode(final int repeatMode) {
    mRepeatMode = repeatMode;
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.setRepeatMode(repeatMode);
          }
          saveQueue(false);
        });
    notifyChange(REPEATMODE_CHANGED);
    SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastRepeatStateAsync(repeatMode));
  }

  private static void saveLastRepeatStateAsync(int repeatMode) {
    ConfigurationManager CM = ConfigurationManager.instance();
    CM.setInt(Constants.PREF_KEY_GUI_PLAYER_REPEAT_MODE, repeatMode);
  }

  /**
   * Sets the shuffle mode; the playing song keeps playing. {@link #isShuffleEnabled()} returns it
   * at once; ExoPlayer gets it on the player thread.
   *
   * @param on The shuffle mode to use
   */
  public void enableShuffle(boolean on) {
    mShuffleEnabled = on;
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.setShuffle(on);
          }
        });
    SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastShuffleStateAsync(on));
    notifyChange(SHUFFLEMODE_CHANGED);
  }

  private static void saveLastShuffleStateAsync(boolean shuffleEnabled) {
    ConfigurationManager CM = ConfigurationManager.instance();
    CM.setBoolean(Constants.PREF_KEY_GUI_PLAYER_SHUFFLE_ENABLED, shuffleEnabled);
  }

  /**
   * On the player thread: a controller (Bluetooth, Android Auto, Wear) changed shuffle or repeat on
   * ExoPlayer; the app shows and keeps what it chose.
   */
  private void modesChangedByPlayer(boolean shuffle, int repeatMode) {
    if (shuffle != mShuffleEnabled) {
      mShuffleEnabled = shuffle;
      SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastShuffleStateAsync(shuffle));
      notifyChange(SHUFFLEMODE_CHANGED);
    }
    if (repeatMode != mRepeatMode) {
      mRepeatMode = repeatMode;
      SystemUtils.exceptionSafePost(mPlayerHandler, () -> saveLastRepeatStateAsync(repeatMode));
      notifyChange(REPEATMODE_CHANGED);
    }
  }

  /**
   * Plays the song at {@code index} in the queue from its start. Posted to the player thread.
   *
   * @param index The position to place the track
   */
  public void setQueuePosition(final int index) {
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue != null) {
            queue.seekTo(index);
            play();
          }
        });
  }

  /**
   * Queues a new list for playback: {@link #NEXT} after the playing song, {@link #LAST} at the end,
   * {@link #NOW} at the end and playing the first of them now. Added to an empty queue, the first
   * song loads paused. Posted to the player thread.
   *
   * @param list The list to queue
   * @param action The action to take
   */
  public void enqueue(final long[] list, final int action) {
    runOnPlayerThread(
        () -> {
          final PlaybackQueue queue = mQueue;
          if (queue == null) {
            return;
          }
          final int first = queue.add(list, action == NEXT);
          if (action == NOW && first >= 0) {
            queue.seekTo(first);
            play();
          }
        });
  }

  /** Cycles through the different repeat modes */
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
   * On the player thread, after ExoPlayer failed to play a song: playing, it skips to the next
   * song, paused, it loads the song again; after {@link #MAX_FAILED_SONGS_SKIPPED} failures in a
   * row (or every song once) it stops.
   */
  private void recoverFromPlayerError() {
    final MultiPlayer player = mPlayer;
    final PlaybackQueue queue = mQueue;
    if (player == null
        || queue == null
        || queue.snapshot().size() == 0
        || player.mExoPlayer.getPlayerError() == null) {
      return; // nothing left to recover from: another song was opened since the error
    }
    if (++mFailedSongsInARow > Math.min(MAX_FAILED_SONGS_SKIPPED, queue.snapshot().size())) {
      LOG.warn("Failed to open file for playback");
      mFailedSongsInARow = 0;
      stopPlayer();
      notifyChange(PLAYSTATE_CHANGED);
      return;
    }
    if (isPlaying()) {
      queue.next();
    }
    player.prepare();
  }

  /**
   * @return The album art for the current album.
   */
  public Bitmap getAlbumArt() {
    try {
      // Return the cached artwork
      return mImageFetcher.getArtwork(getAlbumName(), getAlbumId(), getArtistName());
    } catch (Throwable e) {
      e.printStackTrace();
      // due to the lifecycle of android components,
      // mImageFetcher could be null at the moment of call
      // updateRemoveControlClient.
    }
    return null;
  }

  /** Called when one of the lists should refresh or requery. */
  public void refresh() {
    notifyChange(REFRESH);
  }

  // MediaPlayerAction, mediaPlayerAction, and mediaPlayerAsyncAction removed —
  // ExoPlayer in MultiPlayer manages its own lifecycle.

  public static final class MusicPlayerHandler extends Handler {
    private static final Logger LOG = Logger.getLogger(MusicPlayerHandler.class);
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

      final MultiPlayer player = service.mPlayer;
      switch (msg.what) {
        case FADE_DOWN:
          mCurrentVolume -= .05f;
          if (mCurrentVolume > .2f) {
            sendEmptyMessageDelayed(FADE_DOWN, 10);
          } else {
            mCurrentVolume = .2f;
          }
          if (player != null) {
            player.setVolume(mCurrentVolume);
          }
          break;
        case FADE_UP:
          mCurrentVolume += .01f;
          if (mCurrentVolume < 1.0f) {
            sendEmptyMessageDelayed(FADE_UP, 10);
          } else {
            mCurrentVolume = 1.0f;
          }
          if (player != null) {
            player.setVolume(mCurrentVolume);
          }
          break;
        case SERVER_DIED:
          service.recoverFromPlayerError();
          break;
        default:
          break;
      }
    }
  }

  /**
   * The ExoPlayer that plays the queue, and what other threads may read of it. ExoPlayer handles
   * gapless playback, audio focus, wake mode and audio becoming noisy; its playlist is the queue,
   * which {@link PlaybackQueue} keeps.
   *
   * <p>Threading: ExoPlayer is built on mPlayerHandler's thread and only used there. The methods
   * that use it run at once when called on that thread, else they are posted to it. Position,
   * duration, playing and idle are volatile values its listener and a position poller keep, so any
   * thread may read them.
   */
  static final class MultiPlayer {
    private static final Logger LOG = Logger.getLogger(MultiPlayer.class);
    final ExoPlayer mExoPlayer; // player thread only

    volatile long mCachedPosition = 0;
    volatile long mCachedDuration = -1;
    volatile boolean mCachedIsPlaying = false;
    private volatile boolean mIdle = true;
    private volatile int mAudioSessionId;

    // Polls getCurrentPosition() on the player thread every 500 ms while playing.
    private final Runnable mPositionPoller =
        new Runnable() {
          @Override
          public void run() {
            if (mCachedIsPlaying) {
              mCachedPosition = mExoPlayer.getCurrentPosition();
              postDelayed(this, 500);
            }
          }
        };

    /** Builds the queue's ExoPlayer, bound to {@code looper}'s thread. */
    static ExoPlayer buildExoPlayer(Context context, Looper looper) {
      final ExoPlayer exoPlayer =
          new ExoPlayer.Builder(context)
              .setHandleAudioBecomingNoisy(true)
              .setWakeMode(C.WAKE_MODE_LOCAL)
              .setLooper(looper)
              .setMaxSeekToPreviousPositionMs(REWIND_INSTEAD_PREVIOUS_THRESHOLD)
              .build();
      exoPlayer.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
              .build(),
          true /* handleAudioFocus — ExoPlayer manages focus internally */);
      return exoPlayer;
    }

    /** On {@code exoPlayer}'s application thread. */
    MultiPlayer(ExoPlayer exoPlayer) {
      mExoPlayer = exoPlayer;
      mAudioSessionId = exoPlayer.getAudioSessionId();
      exoPlayer.addListener(
          new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
              LOG.info("MultiPlayer: onPlaybackStateChanged state=" + state);
              mIdle = state == Player.STATE_IDLE;
              final MusicPlaybackService service = MusicPlaybackService.getInstance();
              if (state == Player.STATE_ENDED) {
                LOG.info("MultiPlayer: onPlaybackStateChanged STATE_ENDED — the queue finished");
                mCachedIsPlaying = false;
                removeCallbacks(mPositionPoller);
              } else if (state == Player.STATE_READY) {
                mCachedDuration = knownDuration(mExoPlayer.getDuration());
                removeCallbacks(mPositionPoller);
                post(mPositionPoller);
                if (service != null) {
                  service.mFailedSongsInARow = 0;
                  service.updateNotification();
                }
              }
              syncPlaying(service);
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
              syncPlaying(MusicPlaybackService.getInstance());
            }

            @Override
            public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
              // The current item's duration becomes known once its source is prepared.
              if (mCachedDuration <= 0) {
                mCachedDuration = knownDuration(mExoPlayer.getDuration());
              }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
              LOG.info(
                  "MultiPlayer: onMediaItemTransition reason="
                      + reason
                      + " index="
                      + mExoPlayer.getCurrentMediaItemIndex()
                      + " itemCount="
                      + mExoPlayer.getMediaItemCount());
              mCachedPosition = mExoPlayer.getCurrentPosition();
              // A gapless transition stays READY, so onPlaybackStateChanged won't set it.
              mCachedDuration = knownDuration(mExoPlayer.getDuration());
            }

            @Override
            public void onPositionDiscontinuity(
                @NonNull Player.PositionInfo oldPosition,
                @NonNull Player.PositionInfo newPosition,
                int reason) {
              // Also a seek from the lock screen or a headset while paused, when nothing polls.
              mCachedPosition = mExoPlayer.getCurrentPosition();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
              LOG.info("MultiPlayer: onIsPlayingChanged isPlaying=" + isPlaying);
              mCachedIsPlaying = isPlaying;
              if (isPlaying) {
                removeCallbacks(mPositionPoller);
                post(mPositionPoller);
              }
              MusicPlaybackService svc = MusicPlaybackService.getInstance();
              if (svc != null) {
                svc.notifyChange(PLAYSTATE_CHANGED);
                if (isPlaying) {
                  svc.updateNotification();
                }
              }
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
              modesChanged();
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
              modesChanged();
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
              mAudioSessionId = audioSessionId;
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
              LOG.error("MultiPlayer: onPlayerError: " + error.getMessage(), error);
              mCachedIsPlaying = false;
              final Handler handler = MusicPlaybackService.mPlayerHandler;
              if (handler != null) {
                handler.removeCallbacks(mPositionPoller);
                handler.sendMessageDelayed(handler.obtainMessage(SERVER_DIED), 2000);
              }
            }
          });
    }

    /**
     * The service plays while ExoPlayer is to play and hasn't finished the queue, also when a
     * Bluetooth headset or the lock screen started or paused it.
     */
    private void syncPlaying(@Nullable MusicPlaybackService service) {
      final boolean playing =
          mExoPlayer.getPlayWhenReady() && mExoPlayer.getPlaybackState() != Player.STATE_ENDED;
      if (service != null && service.mIsSupposedToBePlaying != playing) {
        service.mIsSupposedToBePlaying = playing;
        service.notifyChange(PLAYSTATE_CHANGED);
      }
    }

    private void modesChanged() {
      final MusicPlaybackService service = MusicPlaybackService.getInstance();
      if (service != null) {
        service.modesChangedByPlayer(
            mExoPlayer.getShuffleModeEnabled(),
            PlaybackQueue.toApolloRepeatMode(mExoPlayer.getRepeatMode()));
      }
    }

    private static void post(Runnable task) {
      final Handler handler = MusicPlaybackService.mPlayerHandler;
      if (handler != null) {
        handler.post(task);
      }
    }

    private static void postDelayed(Runnable task, long delayMillis) {
      final Handler handler = MusicPlaybackService.mPlayerHandler;
      if (handler != null) {
        handler.postDelayed(task, delayMillis);
      }
    }

    private static void removeCallbacks(Runnable task) {
      final Handler handler = MusicPlaybackService.mPlayerHandler;
      if (handler != null) {
        handler.removeCallbacks(task);
      }
    }

    /**
     * Resolve a path string to a Uri suitable for ExoPlayer. Handles content://, http/https, and
     * bare file paths.
     */
    static Uri resolveUri(String path) {
      return Uri.parse(MusicUriUtils.resolveUriString(path));
    }

    /** True while stopped, failed, or not prepared yet: no song is loaded. */
    boolean isIdle() {
      return mIdle;
    }

    /** Starts or resumes playback; prepares the player first when it was stopped. */
    void start() {
      runOnPlayerThread(
          () -> {
            if (mExoPlayer.getPlaybackState() == Player.STATE_IDLE) {
              mExoPlayer.prepare();
            }
            mExoPlayer.play();
          });
    }

    /** Loads the current song again, after it failed; keeps playing or paused. */
    void prepare() {
      runOnPlayerThread(mExoPlayer::prepare);
    }

    /** Stops playback and lets go of the song's resources; the queue stays. */
    void stop() {
      mCachedIsPlaying = false;
      removeCallbacks(mPositionPoller);
      runOnPlayerThread(
          () -> {
            mExoPlayer.pause();
            mExoPlayer.stop();
          });
    }

    /** Releases all ExoPlayer resources. */
    void release() {
      mCachedIsPlaying = false;
      removeCallbacks(mPositionPoller);
      final Handler handler = MusicPlaybackService.mPlayerHandler;
      if (handler == null || !handler.post(mExoPlayer::release)) {
        LOG.warn("MultiPlayer::release() the player thread is gone; ExoPlayer not released");
      }
    }

    /** Pauses playback. */
    void pause() {
      runOnPlayerThread(mExoPlayer::pause);
    }

    /** ExoPlayer's duration in milliseconds, or -1 while it isn't known. */
    static long knownDuration(long exoDuration) {
      return exoDuration == C.TIME_UNSET || exoDuration < 0 ? -1 : exoDuration;
    }

    /**
     * @return The duration of the current track in milliseconds, or -1.
     */
    long duration() {
      return mCachedDuration;
    }

    /**
     * @return The current playback position in milliseconds.
     */
    long position() {
      return mCachedPosition;
    }

    /** Seeks within the current song; {@link #position()} returns {@code whereto} at once. */
    void seek(final long whereto) {
      mCachedPosition = whereto;
      runOnPlayerThread(() -> mExoPlayer.seekTo(whereto));
    }

    /** Sets the playback volume (0.0–1.0). */
    void setVolume(final float vol) {
      runOnPlayerThread(() -> mExoPlayer.setVolume(vol));
    }

    /**
     * @return True if the player is currently playing. Uses cached value for thread safety.
     */
    boolean isPlaying() {
      return mCachedIsPlaying;
    }

    /** Returns the audio session ID for equalizer / AudioEffect integration; any thread. */
    int getAudioSessionId() {
      return mAudioSessionId;
    }
  }
}
