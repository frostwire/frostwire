/*
 * Copyright (C) 2012-2015 Andrew Neal, Angel Leon, Alden Torres Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RemoteControlClient;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.andrew.apollo.appwidgets.AppWidgetLarge;
import com.andrew.apollo.appwidgets.AppWidgetLargeAlternate;
import com.andrew.apollo.appwidgets.AppWidgetSmall;
import com.andrew.apollo.appwidgets.RecentWidgetProvider;
import com.andrew.apollo.cache.ImageCache;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.Lists;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.util.Ref;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;

/**
 * A backbround {@link Service} used to keep music playing between activities
 * and when the user moves Apollo into the background.
 */
@SuppressLint("NewApi")
public class MusicPlaybackService extends Service {
    private static final String TAG = "MusicPlaybackService";
    private static final boolean D = false;

    /**
     * Indicates that the music has paused or resumed
     */
    public static final String PLAYSTATE_CHANGED = "com.andrew.apollo.playstatechanged";

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
    public static final String APOLLO_PACKAGE_NAME = "com.andrew.apollo";
    public static final String MUSIC_PACKAGE_NAME = "com.android.music";

    /**
     * Called to indicate a general service command. Used in
     * {@link MediaButtonIntentReceiver}
     */
    public static final String SERVICECMD = "com.andrew.apollo.musicservicecommand";

    /**
     * Called to go toggle between pausing and playing the music
     */
    public static final String TOGGLEPAUSE_ACTION = "com.andrew.apollo.togglepause";

    /**
     * Called to go to pause the playback
     */
    public static final String PAUSE_ACTION = "com.andrew.apollo.pause";

    /**
     * Called to go to stop the playback
     */
    public static final String STOP_ACTION = "com.andrew.apollo.stop";

    /**
     * Called to go to the previous track
     */
    public static final String PREVIOUS_ACTION = "com.andrew.apollo.previous";

    /**
     * Called to go to the next track
     */
    public static final String NEXT_ACTION = "com.andrew.apollo.next";

    /**
     * Called to change the repeat mode
     */
    public static final String REPEAT_ACTION = "com.andrew.apollo.repeat";

    /**
     * Called to change the shuffle mode
     */
    public static final String SHUFFLE_ACTION = "com.andrew.apollo.shuffle";

    /**
     * Called to update the service about the foreground state of Apollo's activities
     */
    public static final String FOREGROUND_STATE_CHANGED = "com.andrew.apollo.fgstatechanged";

    public static final String NOW_IN_FOREGROUND = "nowinforeground";

    public static final String FROM_MEDIA_BUTTON = "frommediabutton";

    /**
     * Used to easily notify a list that it should refresh. i.e. A playlist
     * changes
     */
    public static final String REFRESH = "com.andrew.apollo.refresh";

    /**
     * Used by the alarm intent to shutdown the service after being idle
     */
    private static final String SHUTDOWN = "com.andrew.apollo.shutdown";

    public static final String CMDNAME = "command";

    public static final String CMDTOGGLEPAUSE = "togglepause";

    public static final String CMDSTOP = "stop";

    public static final String CMDPAUSE = "pause";

    public static final String CMDPLAY = "play";

    public static final String CMDPREVIOUS = "previous";

    public static final String CMDNEXT = "next";

    private static final int IDCOLIDX = 0;

    /**
     * Moves a list to the front of the queue
     */
    public static final int NOW = 1;

    /**
     * Moves a list to the next position in the queue
     */
    public static final int NEXT = 2;

    /**
     * Moves a list to the last position in the queue
     */
    public static final int LAST = 3;

    /**
     * Shuffles no songs, turns shuffling off
     */
    public static final int SHUFFLE_NONE = 0;

    /**
     * Shuffles all songs
     */
    public static final int SHUFFLE_NORMAL = 1;

    /**
     * Party shuffle
     */
    public static final int SHUFFLE_AUTO = 2;

    /**
     * Turns repeat off
     */
    public static final int REPEAT_NONE = 0;

    /**
     * Repeats the current track in a list
     */
    public static final int REPEAT_CURRENT = 1;

    /**
     * Repeats all the tracks in a list
     */
    public static final int REPEAT_ALL = 2;

    /**
     * Indicates when the track ends
     */
    private static final int TRACK_ENDED = 1;

    /**
     * Indicates that the current track was changed the next track
     */
    private static final int TRACK_WENT_TO_NEXT = 2;

    /**
     * Indicates when the release the wake lock
     */
    private static final int RELEASE_WAKELOCK = 3;

    /**
     * Indicates the player died
     */
    private static final int SERVER_DIED = 4;

    /**
     * Indicates some sort of focus change, maybe a phone call
     */
    private static final int FOCUSCHANGE = 5;

    /**
     * Indicates to fade the volume down
     */
    private static final int FADEDOWN = 6;

    /**
     * Indicates to fade the volume back up
     */
    private static final int FADEUP = 7;

    /**
     * Idle time before stopping the foreground notfication (1 minute)
     */
    private static final int IDLE_DELAY = 60000;

    /**
     * Song play time used as threshold for rewinding to the beginning of the
     * track instead of skipping to the previous track when getting the PREVIOUS
     * command
     */
    private static final long REWIND_INSTEAD_PREVIOUS_THRESHOLD = 3000;

    /**
     * The max size allowed for the track history
     */
    private static final int MAX_HISTORY_SIZE = 100;

    /**
     * The columns used to retrieve any info from the current track
     */
    private static final String[] PROJECTION = new String[] {
            "audio._id AS _id", MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };

    /**
     * The columns used to retrieve any info from the current album
     */
    private static final String[] ALBUM_PROJECTION = new String[] {
            MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.LAST_YEAR
    };

    /**
     * Keeps a mapping of the track history
     */
    private static final LinkedList<Integer> mHistory = Lists.newLinkedList();

    /**
     * Used to shuffle the tracks
     */
    private static final Shuffler mShuffler = new Shuffler();

    /**
     * Used to save the queue as reverse hexadecimal numbers, which we can
     * generate faster than normal decimal or hexadecimal numbers, which in
     * turn allows us to save the playlist more often without worrying too
     * much about performance
     */
    private static final char HEX_DIGITS[] = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Service stub
     */
    private final IBinder mBinder = new ServiceStub(this);

    /**
     * 4x1 widget
     */
    private final AppWidgetSmall mAppWidgetSmall = AppWidgetSmall.getInstance();

    /**
     * 4x2 widget
     */
    private final AppWidgetLarge mAppWidgetLarge = AppWidgetLarge.getInstance();

    /**
     * 4x2 alternate widget
     */
    private final AppWidgetLargeAlternate mAppWidgetLargeAlternate = AppWidgetLargeAlternate
            .getInstance();

    /**
     * Recently listened widget
     */
    private final RecentWidgetProvider mRecentWidgetProvider = RecentWidgetProvider.getInstance();

    /**
     * The media player
     */
    private MultiPlayer mPlayer;

    /**
     * The path of the current file to play
     */
    private String mFileToPlay;

    /**
     * Keeps the service running when the screen is off
     */
    private WakeLock mWakeLock;

    /**
     * Alarm intent for removing the notification when nothing is playing
     * for some time
     */
    private AlarmManager mAlarmManager;
    private PendingIntent mShutdownIntent;
    private boolean mShutdownScheduled;

    /**
     * The cursor used to retrieve info on the current track and run the
     * necessary queries to play audio files
     */
    private Cursor mCursor;

    /**
     * The cursor used to retrieve info on the album the current track is
     * part of, if any.
     */
    private Cursor mAlbumCursor;

    /**
     * Monitors the audio state
     */
    private AudioManager mAudioManager;

    /**
     * Settings used to save and retrieve the queue and history
     */
    private SharedPreferences mPreferences;

    /**
     * Used to know when the service is active
     */
    private boolean mServiceInUse = false;

    /**
     * Used to know if something should be playing or not
     */
    private boolean mIsSupposedToBePlaying = false;

    /**
     * Used to indicate if the queue can be saved
     */
    private boolean mQueueIsSaveable = true;

    /**
     * Used to track what type of audio focus loss caused the playback to pause
     */
    private boolean mPausedByTransientLossOfFocus = false;

    /**
     * Used to track whether any of Apollo's activities is in the foreground
     */
    private boolean mAnyActivityInForeground = false;

    /**
     * Lock screen controls
     */
    private RemoteControlClient mRemoteControlClient;

    private ComponentName mMediaButtonReceiverComponent;

    // We use this to distinguish between different cards when saving/restoring
    // playlists
    private int mCardId;

    private int mPlayListLen = 0;

    private int mPlayPos = -1;

    private int mNextPlayPos = -1;

    private int mOpenFailedCounter = 0;

    private int mMediaMountedCount = 0;

    private int mShuffleMode = SHUFFLE_NONE;

    private int mRepeatMode = REPEAT_NONE;

    private int mServiceStartId = -1;

    private long[] mPlayList = null;

    private long[] mAutoShuffleList = null;

    private MusicPlayerHandler mPlayerHandler;

    private BroadcastReceiver mUnmountReceiver = null;

    /**
     * Image cache
     */
    private ImageFetcher mImageFetcher;

    /**
     * Used to build the notification
     */
    private NotificationHelper mNotificationHelper;

    /**
     * Recently listened database
     */
    private RecentStore mRecentsCache;

    /**
     * Favorites database
     */
    private FavoritesStore mFavoritesCache;

    private boolean launchPlayerActivity;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(final Intent intent) {
        if (D) Log.d(TAG, "Service bound, intent = " + intent);
        cancelShutdown();
        mServiceInUse = true;
        return mBinder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onUnbind(final Intent intent) {
        if (D) Log.d(TAG, "Service unbound");
        mServiceInUse = false;
        saveQueue(true);

        if (mIsSupposedToBePlaying || mPausedByTransientLossOfFocus) {
            // Something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop
            // the service now.
            return true;

            // If there is a playlist but playback is paused, then wait a while
            // before stopping the service, so that pause/resume isn't slow.
            // Also delay stopping the service if we're transitioning between
            // tracks.
        } else if (mPlayListLen > 0 || (mPlayerHandler != null && mPlayerHandler.hasMessages(TRACK_ENDED))) {
            scheduleDelayedShutdown();
            return true;
        }
        stopSelf(mServiceStartId);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRebind(final Intent intent) {
        cancelShutdown();
        mServiceInUse = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        if (D) Log.d(TAG, "Creating service");
        super.onCreate();

        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
            PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            initService();
        }
    }

    private void initService() {
        // Initialize the favorites and recents databases
        mRecentsCache = RecentStore.getInstance(this);
        mFavoritesCache = FavoritesStore.getInstance(this);

        // Initialize the notification helper
        mNotificationHelper = new NotificationHelper(this);

        // Initialize the image fetcher
        mImageFetcher = ImageFetcher.getInstance(this);
        // Initialize the image cache
        mImageFetcher.setImageCache(ImageCache.getInstance(this));

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work will not disrupt the UI.
        final HandlerThread thread = new HandlerThread("MusicPlayerHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Initialize the handler
        mPlayerHandler = new MusicPlayerHandler(this, thread.getLooper());

        // Initialize the audio manager and register any headset controls for
        // playback
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        try {
            mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);
        } catch (SecurityException e) {
            e.printStackTrace();
            // ignore
            // some times the phone does not grant the MODIFY_PHONE_STATE permission
            // this permission is for OMEs and we can't do anything about it
        }

        // Use the remote control APIs to set the playback state
        setUpRemoteControlClient();

        // Initialize the preferences
        mPreferences = getSharedPreferences("Service", 0);
        mCardId = getCardId();

        registerExternalStorageListener();

        // Initialize the media player
        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mPlayerHandler);

        // Initialize the intent filter and each action
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);
        // Attach the broadcast listener
        registerReceiver(mIntentReceiver, filter);

        // Initialize the wake lock
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        // Initialize the delayed shutdown intent
        final Intent shutdownIntent = new Intent(this, MusicPlaybackService.class);
        shutdownIntent.setAction(SHUTDOWN);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);

        // Listen for the idle state
        scheduleDelayedShutdown();

        // Bring the queue back
        reloadQueue();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Initializes the remote control client
     */
    private void setUpRemoteControlClient() {
        final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mMediaButtonReceiverComponent);
        mRemoteControlClient = new RemoteControlClient(
                PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT));
        mAudioManager.registerRemoteControlClient(mRemoteControlClient);

        // Flags for the media transport control that this client supports.
        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_STOP;

        if (ApolloUtils.hasJellyBeanMR2()) {
            // aldenml: Commented due to the nature of the needed refactor
            /*
            flags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;

            mRemoteControlClient.setOnGetPlaybackPositionListener(
                    new RemoteControlClient.OnGetPlaybackPositionListener() {
                @Override
                public long onGetPlaybackPosition() {
                    return position();
                }
            });
            mRemoteControlClient.setPlaybackPositionUpdateListener(
                    new RemoteControlClient.OnPlaybackPositionUpdateListener() {
                @Override
                public void onPlaybackPositionUpdate(long newPositionMs) {
                    seek(newPositionMs);
                }
            });
            */
        }

        mRemoteControlClient.setTransportControlFlags(flags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        if (D) Log.d(TAG, "Destroying service");
        super.onDestroy();
        // Remove any sound effects
        final Intent audioEffectsIntent = new Intent(
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);

        // remove any pending alarms
        if (mAlarmManager != null) {
            mAlarmManager.cancel(mShutdownIntent);
        }

        // Remove all pending messages before kill the player
        if (mPlayerHandler != null) {
            mPlayerHandler.removeCallbacksAndMessages(null);
        }

        // Release the player
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        // Remove the audio focus listener and lock screen controls
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
            mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);
        }

        // Remove any callbacks from the handler
        if (mPlayerHandler != null) {
            mPlayerHandler.removeCallbacksAndMessages(null);
            mPlayerHandler.getLooper().quit();
        }

        // Close the cursor
        closeCursor();

        // Unregister the mount listener
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

        // Release the wake lock
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (D) Log.d(TAG, "Got new intent " + intent + ", startId = " + startId);
        mServiceStartId = startId;

        if (intent != null) {
            final String action = intent.getAction();

            if (intent.hasExtra(NOW_IN_FOREGROUND)) {
                mAnyActivityInForeground = intent.getBooleanExtra(NOW_IN_FOREGROUND, false);
                updateNotification();
            }

            if (SHUTDOWN.equals(action)) {
                mShutdownScheduled = false;
                releaseServiceUiAndStop();
                return START_NOT_STICKY;
            }

            handleCommandIntent(intent);
        }

        // Make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        scheduleDelayedShutdown();

        if (intent != null && intent.getBooleanExtra(FROM_MEDIA_BUTTON, false)) {
            MediaButtonIntentReceiver.completeWakefulIntent(intent);
        }

        return START_STICKY;
    }

    private void releaseServiceUiAndStop() {
        if (mPlayerHandler == null ||
                isPlaying()
                || mPausedByTransientLossOfFocus
                || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

        if (D) Log.d(TAG, "Nothing is playing anymore, releasing notification");
        mNotificationHelper.killNotification();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        if (!mServiceInUse) {
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    }

    private void handleCommandIntent(Intent intent) {
        final String action = intent.getAction();
        final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

        if (D) Log.d(TAG, "handleCommandIntent: action = " + action + ", command = " + command);

        if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
            gotoNext(true);
        } else if (CMDPREVIOUS.equals(command) || PREVIOUS_ACTION.equals(action)) {
            if (position() < REWIND_INSTEAD_PREVIOUS_THRESHOLD) {
                prev();
            } else {
                seek(0);
                play();
            }
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
            releaseServiceUiAndStop();
        } else if (REPEAT_ACTION.equals(action)) {
            cycleRepeat();
        } else if (SHUFFLE_ACTION.equals(action)) {
            cycleShuffle();
        }
    }

    /**
     * Updates the notification, considering the current play and activity state
     */
    private void updateNotification() {
        if (mNotificationHelper == null) {
            return;
        }

        if (!mAnyActivityInForeground && isPlaying()) {
            mNotificationHelper.buildNotification(getAlbumName(), getArtistName(),
                    getTrackName(), getAlbumId(), getAlbumArt(), isPlaying());
        } else if (mAnyActivityInForeground) {
            mNotificationHelper.killNotification();
        }
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
                cursor = null;
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
     *
     * @param storagePath The path to mount point for the removed media
     */
    public void closeExternalStorageFiles(final String storagePath) {
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The
     * intent will call closeExternalStorageFiles() if the external media is
     * going to be ejected, so applications can clean up any files they have
     * open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    final String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = getCardId();
                        reloadQueue();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, filter);
        }
    }

    private void scheduleDelayedShutdown() {
        if (D) Log.v(TAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
        if (mAlarmManager != null) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
            mShutdownScheduled = true;
        } else {
            mShutdownScheduled = false;
        }
    }

    private void cancelShutdown() {
        if (D) Log.d(TAG, "Cancelling delayed shutdown, scheduled = " + mShutdownScheduled);
        if (mShutdownScheduled) {
            mAlarmManager.cancel(mShutdownIntent);
            mShutdownScheduled = false;
        }
    }

    /**
     * Stops playback
     *
     * @param goToIdle True to go to the idle state, false otherwise
     */
    private void stop(final boolean goToIdle) {
        if (D) Log.d(TAG, "Stopping playback, goToIdle = " + goToIdle);
        if (mPlayer != null && mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        closeCursor();
        if (goToIdle) {
            scheduleDelayedShutdown();
            mIsSupposedToBePlaying = false;
        } else {
            stopForeground(false);
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
        synchronized (this) {
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

            if (gotonext) {
                if (mPlayListLen == 0) {
                    stop(true);
                    mPlayPos = -1;
                    closeCursor();
                } else {
                    if (mShuffleMode != SHUFFLE_NONE) {
                        mPlayPos = getNextPosition(true);
                    } else if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    final boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
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

        final int tailsize = mPlayListLen - position;
        for (int i = tailsize; i > 0; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen];
        }

        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        mPlayListLen += addlen;
        if (mPlayListLen == 0) {
            closeCursor();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * @param trackId The track ID
     */
    private void updateCursor(final long trackId) {
        updateCursor("_id=" + trackId, null);
    }

    private void updateCursor(final String selection, final String[] selectionArgs) {
        synchronized (this) {
            closeCursor();
            mCursor = openCursorAndGoToFirst(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    PROJECTION, selection, selectionArgs);
        }
        updateAlbumCursor();
    }

    private void updateCursor(final Uri uri) {
        synchronized (this) {
            closeCursor();
            mCursor = openCursorAndGoToFirst(uri, PROJECTION, null, null);
        }
        updateAlbumCursor();
    }

    private void updateAlbumCursor() {
        long albumId = getAlbumId();
        if (albumId >= 0) {
            mAlbumCursor = openCursorAndGoToFirst(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    ALBUM_PROJECTION, "_id=" + albumId, null);
        } else {
            mAlbumCursor = null;
        }
    }

    private Cursor openCursorAndGoToFirst(Uri uri, String[] projection,
                                          String selection, String[] selectionArgs) {
        Cursor c;

        try {
            c = getContentResolver().query(uri, projection, selection, selectionArgs, null);
        } catch (Throwable t) {
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
            mCursor.close();
            mCursor = null;
        }
        if (mAlbumCursor != null) {
            mAlbumCursor.close();
            mAlbumCursor = null;
        }
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     */
    private void openCurrentAndNext() {
        openCurrentAndMaybeNext(true);
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     *
     * @param openNext True to prepare the next track for playback, false
     *                 otherwise.
     */
    private void openCurrentAndMaybeNext(final boolean openNext) {
        synchronized (this) {
            closeCursor();

            if (mPlayListLen == 0 || mPlayList == null) {
                return;
            }
            stop(false);

            mPlayPos = Math.min(mPlayPos, mPlayList.length - 1);
            updateCursor(mPlayList[mPlayPos]);

            while (true) {
                if (mCursor != null && !mCursor.isClosed()
                        && openFile(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
                        + mCursor.getLong(IDCOLIDX))) {
                    break;
                }
                // if we get here then opening the file failed. We can close the
                // cursor now, because
                // we're either going to create a new one next, or stop trying
                closeCursor();
                if (mOpenFailedCounter++ < 10 && mPlayListLen > 1) {
                    final int pos = getNextPosition(false);
                    if (pos < 0) {
                        scheduleDelayedShutdown();
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
                    }
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
                    updateCursor(mPlayList[mPlayPos]);
                } else {
                    mOpenFailedCounter = 0;
                    Log.w(TAG, "Failed to open file for playback");
                    scheduleDelayedShutdown();
                    if (mIsSupposedToBePlaying) {
                        mIsSupposedToBePlaying = false;
                        notifyChange(PLAYSTATE_CHANGED);
                    }
                    return;
                }
            }
            if (openNext) {
                setNextTrack();
            }
        }
    }

    /**
     * @param force True to force the player onto the track next, false
     *              otherwise.
     * @return The next position to play.
     */
    private int getNextPosition(final boolean force) {
        if (!force && mRepeatMode == REPEAT_CURRENT) {
            if (mPlayPos < 0) {
                return 0;
            }
            return mPlayPos;
        } else if (mShuffleMode == SHUFFLE_NORMAL) {
            if (mPlayPos >= 0) {
                mHistory.add(mPlayPos);
            }
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            final int numTracks = mPlayListLen;
            final int[] tracks = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                tracks[i] = i;
            }

            final int numHistory = mHistory.size();
            int numUnplayed = numTracks;
            for (int i = 0; i < numHistory; i++) {
                final int idx = mHistory.get(i).intValue();
                if (idx < numTracks && tracks[idx] >= 0) {
                    numUnplayed--;
                    tracks[idx] = -1;
                }
            }
            if (numUnplayed <= 0) {
                if (mRepeatMode == REPEAT_ALL || force) {
                    numUnplayed = numTracks;
                    for (int i = 0; i < numTracks; i++) {
                        tracks[i] = i;
                    }
                } else {
                    return -1;
                }
            }
            int skip = 0;
            if (mShuffleMode == SHUFFLE_NORMAL || mShuffleMode == SHUFFLE_AUTO) {
                skip = mShuffler.nextInt(numUnplayed);
            }
            int cnt = -1;
            if (numTracks > 0) {
                while (true) {
                    while (cnt < tracks.length && tracks[++cnt] < 0) {
                    }
                    skip--;
                    if (skip < 0) {
                        break;
                    }
                }
            }
            return cnt;
        } else if (mShuffleMode == SHUFFLE_AUTO) {
            doAutoShuffleUpdate();
            return mPlayPos + 1;
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
        mNextPlayPos = getNextPosition(false);
        if (D) Log.d(TAG, "setNextTrack: next play position = " + mNextPlayPos);

        if (mPlayer != null) {
            if (mNextPlayPos >= 0 && mPlayList != null) {
                final long id = mPlayList[mNextPlayPos];
                mPlayer.setNextDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
            } else {
                mPlayer.setNextDataSource(null);
            }
        } else {
            Log.w(TAG, "setNextTrack() -> no mPlayer instance available.");
        }
    }

    /**
     * Creates a shuffled playlist used for party mode
     */
    private boolean makeAutoShuffleList() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            MediaStore.Audio.Media._ID
                    }, MediaStore.Audio.Media.IS_MUSIC + "=1", null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return false;
            }
            final int len = cursor.getCount();
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (final RuntimeException e) {
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return false;
    }

    /**
     * Creates the party shuffle playlist
     */
    private void doAutoShuffleUpdate() {
        boolean notify = false;
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        final int toAdd = 7 - (mPlayListLen - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < toAdd; i++) {
            int lookback = mHistory.size();
            int idx = -1;
            while (true) {
                idx = mShuffler.nextInt(mAutoShuffleList.length);
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            mHistory.add(idx);
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            ensurePlayListCapacity(mPlayListLen + 1);
            mPlayList[mPlayListLen++] = mAutoShuffleList[idx];
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**/
    private boolean wasRecentlyUsed(final int idx, int lookbacksize) {
        if (lookbacksize == 0) {
            return false;
        }
        final int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            lookbacksize = histsize;
        }
        final int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            final long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
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
            final long[] newlist = new long[size * 2];
            final int len = mPlayList != null ? mPlayList.length : mPlayListLen;
            for (int i = 0; i < len; i++) {
                newlist[i] = mPlayList[i];
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }

    /**
     * Notify the change-receivers that something has changed.
     */
    private void notifyChange(final String what) {
        if (D) Log.d(TAG, "notifyChange: what = " + what);

        // Update the lockscreen controls
        updateRemoteControlClient(what);

        if (what.equals(POSITION_CHANGED)) {
            return;
        }

        final Intent intent = new Intent(what);
        intent.putExtra("id", getAudioId());
        intent.putExtra("artist", getArtistName());
        intent.putExtra("album", getAlbumName());
        intent.putExtra("track", getTrackName());
        intent.putExtra("playing", isPlaying());
        intent.putExtra("isfavorite", isFavorite());
        sendStickyBroadcast(intent);

        final Intent musicIntent = new Intent(intent);
        musicIntent.setAction(what.replace(APOLLO_PACKAGE_NAME, MUSIC_PACKAGE_NAME));
        sendStickyBroadcast(musicIntent);

        if (what.equals(META_CHANGED)) {
            // Increase the play count for favorite songs.
            if (mFavoritesCache.getSongId(getAudioId()) != null) {
                mFavoritesCache.addSongId(getAudioId(), getTrackName(), getAlbumName(),
                        getArtistName());
            }
            // Add the track to the recently played list.
            mRecentsCache.addAlbumId(getAlbumId(), getAlbumName(), getArtistName(),
                    MusicUtils.getSongCountForAlbum(this, getAlbumId()),
                    MusicUtils.getReleaseDateForAlbum(this, getAlbumId()));
        } else if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
            if (isPlaying()) {
                setNextTrack();
            }
        } else {
            saveQueue(false);
        }

        if (what.equals(PLAYSTATE_CHANGED)) {
            mNotificationHelper.updatePlayState(isPlaying());
        }

        // Update the app-widgets
        mAppWidgetSmall.notifyChange(this, what);
        mAppWidgetLarge.notifyChange(this, what);
        mAppWidgetLargeAlternate.notifyChange(this, what);
        mRecentWidgetProvider.notifyChange(this, what);
    }

    /**
     * Updates the lockscreen controls.
     *
     * @param what The broadcast
     */
    private void updateRemoteControlClient(final String what) {
        int playState = mIsSupposedToBePlaying
                ? RemoteControlClient.PLAYSTATE_PLAYING
                : RemoteControlClient.PLAYSTATE_PAUSED;

        if (ApolloUtils.hasJellyBeanMR2()
                && (what.equals(PLAYSTATE_CHANGED) || what.equals(POSITION_CHANGED))) {
            //mRemoteControlClient.setPlaybackState(playState, position(), 1.0f);
            mRemoteControlClient.setPlaybackState(playState);
        } else if (what.equals(PLAYSTATE_CHANGED)) {
            mRemoteControlClient.setPlaybackState(playState);
        } else if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
            Bitmap albumArt = getAlbumArt();
            if (albumArt != null) {
                // RemoteControlClient wants to recycle the bitmaps thrown at it, so we need
                // to make sure not to hand out our cache copy
                Bitmap.Config config = albumArt.getConfig();
                if (config == null) {
                    config = Bitmap.Config.ARGB_8888;
                }
                albumArt = albumArt.copy(config, false);
            }
            mRemoteControlClient
                    .editMetadata(true)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName())
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
                            getAlbumArtistName())
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, getAlbumName())
                    .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName())
                    .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration())
                    .putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, albumArt)
                    .apply();

            if (ApolloUtils.hasJellyBeanMR2()) {
                //mRemoteControlClient.setPlaybackState(playState, position(), 1.0f);
                mRemoteControlClient.setPlaybackState(playState);
            }
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
                if (n < 0) {
                    continue;
                } else if (n == 0) {
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
            if (mShuffleMode != SHUFFLE_NONE) {
                len = mHistory.size();
                q.setLength(0);
                for (int i = 0; i < len; i++) {
                    int n = mHistory.get(i);
                    if (n == 0) {
                        q.append("0;");
                    } else {
                        while (n != 0) {
                            final int digit = n & 0xf;
                            n >>>= 4;
                            q.append(HEX_DIGITS[digit]);
                        }
                        q.append(";");
                    }
                }
                editor.putString("history", q.toString());
            }
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
        editor.putInt("shufflemode", mShuffleMode);
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
                updateCursor(mPlayList[mPlayPos]);
            }
            synchronized (this) {
                closeCursor();
                mOpenFailedCounter = 20;
                openCurrentAndNext();
            }
            if (mPlayer == null || !mPlayer.isInitialized()) {
                mPlayListLen = 0;
                return;
            }

            final long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

            if (D) {
                Log.d(TAG, "restored queue, currently at position "
                        + position() + "/" + duration()
                        + " (requested " + seekpos + ")");
            }

            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode != SHUFFLE_NONE) {
                q = mPreferences.getString("history", "");
                qlen = q != null ? q.length() : 0;
                if (qlen > 1) {
                    plen = 0;
                    n = 0;
                    shift = 0;
                    mHistory.clear();
                    for (int i = 0; i < qlen; i++) {
                        final char c = q.charAt(i);
                        if (c == ';') {
                            if (n >= mPlayListLen) {
                                mHistory.clear();
                                break;
                            }
                            mHistory.add(n);
                            n = 0;
                            shift = 0;
                        } else {
                            if (c >= '0' && c <= '9') {
                                n += c - '0' << shift;
                            } else if (c >= 'a' && c <= 'f') {
                                n += 10 + c - 'a' << shift;
                            } else {
                                mHistory.clear();
                                break;
                            }
                            shift += 4;
                        }
                    }
                }
            }
            if (shufmode == SHUFFLE_AUTO) {
                if (!makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
        }
    }

    /**
     * Opens a file and prepares it for playback
     *
     * @param path The path of the file to open
     */
    public boolean openFile(final String path) {
        if (D) Log.d(TAG, "openFile: path = " + path);
        synchronized (this) {
            if (path == null) {
                return false;
            }

            // If mCursor is null, try to associate path with a database cursor
            if (mCursor == null) {
                Uri uri = Uri.parse(path);
                long id = -1;
                try {
                    id = Long.valueOf(uri.getLastPathSegment());
                } catch (NumberFormatException ex) {
                    // Ignore
                }

                if (id != -1 && path.startsWith(MediaStore.Audio.Media.
                        EXTERNAL_CONTENT_URI.toString())) {
                    updateCursor(uri);

                } else if (id != -1 && path.startsWith(MediaStore.Files.getContentUri(
                        "external").toString())) {
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
                } catch (final UnsupportedOperationException ex) {
                }
            }
            mFileToPlay = path;
            if (mPlayer != null) { // machine state issues in general with original Apollo code
                mPlayer.setDataSource(mFileToPlay);
                if (mPlayer.isInitialized()) {
                    mOpenFailedCounter = 0;
                    return true;
                }
            }
            stop(true);
            return false;
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

        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /**
     * Indicates if the media storeage device has been mounted or not
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
    public int getShuffleMode() {
        return mShuffleMode;
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
        synchronized (this) {
            if (mCursor == null || mCursor.isClosed()) {
                return null;
            }
            try {
                return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
            } catch (Throwable e) {
                e.printStackTrace();
                return "---";
            }
        }
    }

    /**
     * Returns the artist name
     *
     * @return The current song artist name
     */
    public String getArtistName() {
        try {
            synchronized (this) {
                if (mCursor == null || mCursor.isClosed()) {
                    return null;
                }
                return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
            }
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
    public String getAlbumArtistName() {
        synchronized (this) {
            if (mAlbumCursor == null || mAlbumCursor.isClosed()) {
                return null;
            }
            return mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(AlbumColumns.ARTIST));
        }
    }

    /**
     * Returns the album ID
     *
     * @return The current song album ID
     */
    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null || mCursor.isClosed()) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM_ID));
        }
    }

    /**
     * Returns the artist ID
     *
     * @return The current song artist ID
     */
    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null || mCursor.isClosed()) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST_ID));
        }
    }

    /**
     * Returns the current audio ID
     *
     * @return The current track ID
     */
    public long getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer != null && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
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
     * Returns the current position in time of the currenttrack
     *
     * @return The current playback position in miliseconds
     */
    public long position() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Returns the full duration of the current track
     *
     * @return The duration of the current track in miliseconds
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
        synchronized (this) {
            final int len = mPlayListLen;
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
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
        return mCursor == null || mCursor.isClosed();
    }

    /**
     * True if the current track is a "favorite", false otherwise
     */
    public boolean isFavorite() {
        if (mFavoritesCache != null) {
            synchronized (this) {
                final Long id = mFavoritesCache.getSongId(getAudioId());
                return id != null ? true : false;
            }
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
        launchPlayerActivity = true;
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
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
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mShuffler.nextInt(mPlayListLen);
            }
            mHistory.clear();
            openCurrentAndNext();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Resumes or starts playback.
     */
    public void play() {
        int status = mAudioManager.requestAudioFocus(mAudioFocusListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (D) Log.d(TAG, "Starting playback: audio focus request status = " + status);

        if (status != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }

        try {
            mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                    MediaButtonIntentReceiver.class.getName()));
        } catch (SecurityException e) {
            e.printStackTrace();
            // see explanation in initService
        }

        if (mPlayer != null && mPlayer.isInitialized()) {
            setNextTrack();

            final long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000
                    && mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }

            mPlayer.start();

            if (mPlayerHandler != null) {
                mPlayerHandler.removeMessages(FADEDOWN);
                mPlayerHandler.sendEmptyMessage(FADEUP);
            }

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

            cancelShutdown();
            updateNotification();
        } else if (mPlayListLen <= 0) {
            setShuffleMode(SHUFFLE_AUTO);
        }
    }

    /**
     * Temporarily pauses playback.
     */
    public void pause() {
        if (D) Log.d(TAG, "Pausing playback");
        synchronized (this) {
            if (mPlayerHandler != null) {
                mPlayerHandler.removeMessages(FADEUP);
            }
            if (mIsSupposedToBePlaying && mPlayer != null) {
                mPlayer.pause();
                scheduleDelayedShutdown();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    /**
     * Changes from the current track to the next track
     */
    public void gotoNext(final boolean force) {
        if (D) Log.d(TAG, "Going to next track");
        synchronized (this) {
            if (mPlayListLen <= 0) {
                if (D) Log.d(TAG, "No play queue");
                scheduleDelayedShutdown();
                return;
            }
            final int pos = getNextPosition(force);
            if (pos < 0) {
                scheduleDelayedShutdown();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
                return;
            }
            mPlayPos = pos;
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Changes from the current track to the previous played track
     */
    public void prev() {
        if (D) Log.d(TAG, "Going to previous track");
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_NORMAL) {
                // Go to previously-played track and remove it from the history
                final int histsize = mHistory.size();
                if (histsize == 0) {
                    return;
                }
                final Integer pos = mHistory.remove(histsize - 1);
                mPlayPos = pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            }
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
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
     * @param index1 The position the item is currently at
     * @param index2 The position the item is being moved to
     */
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            if (index1 < index2) {
                final long tmp = mPlayList[index1];
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i + 1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                    mPlayPos--;
                }
            } else if (index2 < index1) {
                final long tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i - 1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Sets the repeat mode
     *
     * @param repeatmode The repeat mode to use
     */
    public void setRepeatMode(final int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            saveQueue(false);
            notifyChange(REPEATMODE_CHANGED);
        }
    }

    /**
     * Sets the shuffle mode
     *
     * @param shufflemode The shuffle mode to use
     */
    public void setShuffleMode(final int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    mPlayListLen = 0;
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                } else {
                    mShuffleMode = SHUFFLE_NONE;
                }
            }
            saveQueue(false);
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }

    /**
     * Sets the position of a track in the queue
     *
     * @param index The position to place the track
     */
    public void setQueuePosition(final int index) {
        synchronized (this) {
            stop(false);
            mPlayPos = index;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
            }
        }
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
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                play();
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
            if (mShuffleMode != SHUFFLE_NONE) {
                setShuffleMode(SHUFFLE_NONE);
            }
        } else {
            setRepeatMode(REPEAT_NONE);
        }
    }

    /**
     * Cycles through the different shuffle modes
     */
    private void cycleShuffle() {
        if (mShuffleMode == SHUFFLE_NONE) {
            setShuffleMode(SHUFFLE_NORMAL);
            if (mRepeatMode == REPEAT_CURRENT) {
                setRepeatMode(REPEAT_ALL);
            }
        } else if (mShuffleMode == SHUFFLE_NORMAL || mShuffleMode == SHUFFLE_AUTO) {
            setShuffleMode(SHUFFLE_NONE);
        }
    }

    /**
     * @return The album art for the current album.
     */
    public Bitmap getAlbumArt() {
        try {
            // Return the cached artwork
            final Bitmap bitmap = mImageFetcher.getArtwork(getAlbumName(),
                    getAlbumId(), getArtistName());
            return bitmap;
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

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String command = intent.getStringExtra(CMDNAME);

            if (AppWidgetSmall.CMDAPPWIDGETUPDATE.equals(command)) {
                final int[] small = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetSmall.performUpdate(MusicPlaybackService.this, small);
            } else if (AppWidgetLarge.CMDAPPWIDGETUPDATE.equals(command)) {
                final int[] large = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLarge.performUpdate(MusicPlaybackService.this, large);
            } else if (AppWidgetLargeAlternate.CMDAPPWIDGETUPDATE.equals(command)) {
                final int[] largeAlt = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLargeAlternate.performUpdate(MusicPlaybackService.this, largeAlt);
            } else if (RecentWidgetProvider.CMDAPPWIDGETUPDATE.equals(command)) {
                final int[] recent = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mRecentWidgetProvider.performUpdate(MusicPlaybackService.this, recent);
            } else {
                handleCommandIntent(intent);
            }
        }
    };

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onAudioFocusChange(final int focusChange) {
            if (mPlayerHandler != null) {
                mPlayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
            }
        }
    };

    private static final class MusicPlayerHandler extends Handler {
        private final WeakReference<MusicPlaybackService> mService;
        private float mCurrentVolume = 1.0f;

        /**
         * Constructor of <code>MusicPlayerHandler</code>
         *
         * @param service The service to use.
         * @param looper  The thread to run on.
         */
        public MusicPlayerHandler(final MusicPlaybackService service, final Looper looper) {
            super(looper);
            mService = new WeakReference<MusicPlaybackService>(service);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            final MusicPlaybackService service = mService.get();
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        sendEmptyMessageDelayed(FADEUP, 10);
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
                        service.seek(0);
                        service.play();
                    } else {
                        service.gotoNext(false);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    service.mWakeLock.release();
                    break;
                case FOCUSCHANGE:
                    if (D) Log.d(TAG, "Received audio focus change event " + msg.arg1);
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
                            removeMessages(FADEUP);
                            sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying()
                                    && service.mPausedByTransientLossOfFocus) {
                                service.mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                service.mPlayer.setVolume(mCurrentVolume);
                                service.play();
                            } else {
                                removeMessages(FADEDOWN);
                                sendEmptyMessage(FADEUP);
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

    private static final class Shuffler {

        private final LinkedList<Integer> mHistoryOfNumbers = new LinkedList<Integer>();

        private final TreeSet<Integer> mPreviousNumbers = new TreeSet<Integer>();

        private final Random mRandom = new Random();

        private int mPrevious;

        /**
         * Constructor of <code>Shuffler</code>
         */
        public Shuffler() {
            super();
        }

        /**
         * @param interval The length the queue
         * @return The position of the next track to play
         */
        public int nextInt(final int interval) {
            if (interval <= 0) {
                return 0;
            }
            int next;
            do {
                next = mRandom.nextInt(interval);
            } while (next == mPrevious && interval > 1
                    && !mPreviousNumbers.contains(Integer.valueOf(next)));
            mPrevious = next;
            mHistoryOfNumbers.add(mPrevious);
            mPreviousNumbers.add(mPrevious);
            cleanUpHistory();
            return next;
        }

        /**
         * Removes old tracks and cleans up the history preparing for new tracks
         * to be added to the mapping
         */
        private void cleanUpHistory() {
            if (!mHistoryOfNumbers.isEmpty() && mHistoryOfNumbers.size() >= MAX_HISTORY_SIZE) {
                for (int i = 0; i < Math.max(1, MAX_HISTORY_SIZE / 2); i++) {
                    mPreviousNumbers.remove(mHistoryOfNumbers.removeFirst());
                }
            }
        }
    }

    ;

    private static final class MultiPlayer implements MediaPlayer.OnErrorListener,
            MediaPlayer.OnCompletionListener {

        private final WeakReference<MusicPlaybackService> mService;

        private MediaPlayer mCurrentMediaPlayer = new MediaPlayer();

        private MediaPlayer mNextMediaPlayer;

        private Handler mHandler;

        private boolean mIsInitialized = false;

        /**
         * Constructor of <code>MultiPlayer</code>
         */
        public MultiPlayer(final MusicPlaybackService service) {
            mService = new WeakReference<MusicPlaybackService>(service);
            mCurrentMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
        }

        /**
         * @param path The path of the file, or the http/rtsp URL of the stream
         *             you want to play
         */
        public void setDataSource(final String path) {
            mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, path);
            if (mIsInitialized) {
                setNextDataSource(null);
            }
        }

        /**
         * @param player The {@link MediaPlayer} to use
         * @param path   The path of the file, or the http/rtsp URL of the stream
         *               you want to play
         * @return True if the <code>player</code> has been prepared and is
         * ready to play, false otherwise
         */
        private boolean setDataSourceImpl(final MediaPlayer player, final String path) {
            try {
                player.reset();
                if (Ref.alive(mService) && mService.get().launchPlayerActivity) {
                    player.setOnPreparedListener(new AudioOnPreparedListener(mService));
                }
                if (path.startsWith("content://")) {
                    player.setDataSource(mService.get(), Uri.parse(path));
                } else {
                    player.setDataSource(path);
                }
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.prepare();
            } catch (final IOException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (final IllegalArgumentException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (Throwable e) {
                // TODO: notify the user why the file couldn't be opened due to an unknown error
                return false;
            }
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get().getPackageName());
            mService.get().sendBroadcast(intent);
            return true;
        }

        /**
         * Set the MediaPlayer to start when this MediaPlayer finishes playback.
         *
         * @param path The path of the file, or the http/rtsp URL of the stream
         *             you want to play
         */
        public void setNextDataSource(final String path) {
            try {
                mCurrentMediaPlayer.setNextMediaPlayer(null);
            } catch (IllegalArgumentException e) {
                Log.i(TAG, "Next media player is current one, continuing");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Media player not initialized!");
                return;
            } catch (Throwable e) {
                Log.e(TAG, "Media player fatal error", e);
                return;
            }

            if (mNextMediaPlayer != null) {
                try {
                    mNextMediaPlayer.release();
                } catch (Throwable e) {
                    Log.w(TAG, "Something wrong releasing mNextMediaPlayer", e);
                } finally {
                    mNextMediaPlayer = null;
                }
            }
            if (path == null) {
                return;
            }
            mNextMediaPlayer = new MediaPlayer();
            mNextMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);

            try {
                mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            } catch (Throwable e) {
                Log.e(TAG, "Media player Illegal State exception", e);
            }

            if (setDataSourceImpl(mNextMediaPlayer, path)) {
                try {
                    mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
                } catch (Throwable e) {
                    Log.e(TAG, "Media player fatal error", e);
                    return;
                }
            } else {
                if (mNextMediaPlayer != null) {
                    try {
                        mNextMediaPlayer.release();
                    } catch (Throwable e) {
                        Log.w(TAG, "Something wrong releasing mNextMediaPlayer", e);
                    } finally {
                        mNextMediaPlayer = null;
                    }
                }
            }
        }

        /**
         * Sets the handler
         *
         * @param handler The handler to use
         */
        public void setHandler(final Handler handler) {
            mHandler = handler;
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        public boolean isInitialized() {
            return mIsInitialized;
        }

        /**
         * Starts or resumes playback.
         */
        public void start() {
            try {
                mCurrentMediaPlayer.start();
            } catch (Throwable t) {

            }
        }

        /**
         * Resets the MediaPlayer to its uninitialized state.
         */
        public void stop() {
            try {
                mCurrentMediaPlayer.reset();
                mIsInitialized = false;
            } catch (Throwable t) {
                // recover from possible IllegalStateException caused by native _reset() method.
            }
        }

        /**
         * Releases resources associated with this MediaPlayer object.
         */
        public void release() {
            stop();
            try {
                mCurrentMediaPlayer.release();
            } catch (Throwable t) {

            }
        }

        /**
         * Pauses playback. Call start() to resume.
         */
        public void pause() {
            try {
                mCurrentMediaPlayer.pause();
            } catch (Throwable t) {

            }
        }

        /**
         * Gets the duration of the file.
         *
         * @return The duration in milliseconds
         */
        public long duration() {
            try {
                return mCurrentMediaPlayer.getDuration();
            } catch (Throwable t) {
                return -1;
            }
        }

        /**
         * Gets the current playback position.
         *
         * @return The current position in milliseconds
         */
        public long position() {
            long result = 0;
            try {
                result = mCurrentMediaPlayer.getCurrentPosition();
            } catch (Throwable t) {
            }
            return result;
        }

        /**
         * Gets the current playback position.
         *
         * @param whereto The offset in milliseconds from the start to seek to
         * @return The offset in milliseconds from the start to seek to
         */
        public long seek(final long whereto) {
            try {
                mCurrentMediaPlayer.seekTo((int) whereto);
            } catch (Throwable t) {

            }
            return whereto;
        }

        /**
         * Sets the volume on this player.
         *
         * @param vol Left and right volume scalar
         */
        public void setVolume(final float vol) {
            try {
                mCurrentMediaPlayer.setVolume(vol, vol);
            } catch (Throwable t) {
                // possible native IllegalStateException.
            }
        }

        /**
         * Sets the audio session ID.
         *
         * @param sessionId The audio session ID
         */
        public void setAudioSessionId(final int sessionId) {
            try {
                mCurrentMediaPlayer.setAudioSessionId(sessionId);
            } catch (Throwable t) {
            }
        }

        /**
         * Returns the audio session ID.
         *
         * @return The current audio session ID.
         */
        public int getAudioSessionId() {
            int result = 0;
            try {
                result = mCurrentMediaPlayer.getAudioSessionId();
            } catch (Throwable t) {
            }
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onError(final MediaPlayer mp, final int what, final int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    try {
                        mIsInitialized = false;
                        mCurrentMediaPlayer.release();
                        mCurrentMediaPlayer = new MediaPlayer();
                        mCurrentMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
                    } catch (Throwable t) {
                    }
                    return true;
                default:
                    break;
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCompletion(final MediaPlayer mp) {
            try {
                if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
                    mCurrentMediaPlayer.release();
                    mCurrentMediaPlayer = mNextMediaPlayer;
                    mNextMediaPlayer = null;
                    mHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
                } else {
                    mService.get().mWakeLock.acquire(30000);
                    mHandler.sendEmptyMessage(TRACK_ENDED);
                    mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
                }
            } catch (Throwable t) {

            }
        }
    }

    private static final class ServiceStub extends IApolloService.Stub {

        private final WeakReference<MusicPlaybackService> mService;
        private final static long[] EMPTY_LONG_ARRAY = new long[0];

        private ServiceStub(final MusicPlaybackService service) {
            mService = new WeakReference<MusicPlaybackService>(service);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void openFile(final String path) throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().openFile(path);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void open(final long[] list, final int position) throws RemoteException {
            if (list != null && Ref.alive(mService)) {
                mService.get().open(list, position);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void stop() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().stop();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void pause() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().pause();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void play() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().play();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void prev() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().prev();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void next() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().gotoNext(true);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void enqueue(final long[] list, final int action) throws RemoteException {
            if (list != null && Ref.alive(mService)) {
                mService.get().enqueue(list, action);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setQueuePosition(final int index) throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().setQueuePosition(index);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setShuffleMode(final int shufflemode) throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().setShuffleMode(shufflemode);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setRepeatMode(final int repeatmode) throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().setRepeatMode(repeatmode);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void moveQueueItem(final int from, final int to) throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().moveQueueItem(from, to);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void toggleFavorite() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().toggleFavorite();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void refresh() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().refresh();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isFavorite() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().isFavorite();
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isPlaying() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().isPlaying();
            }
            return false;
        }

        @Override
        public boolean isStopped() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().isStopped();
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long[] getQueue() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getQueue();
            }
            return EMPTY_LONG_ARRAY;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long duration() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().duration();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long position() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().position();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long seek(final long position) throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().seek(position);
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getAudioId() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getAudioId();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getArtistId() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getArtistId();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getAlbumId() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getAlbumId();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getArtistName() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getArtistName();
            }
            return "";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getTrackName() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getTrackName();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAlbumName() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getAlbumName();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPath() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getPath();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getQueuePosition() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getQueuePosition();
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getShuffleMode() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getShuffleMode();
            }
            return 0; // SHUFFLE_NONE
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getRepeatMode() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getRepeatMode();
            }
            return REPEAT_NONE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int removeTracks(final int first, final int last) throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().removeTracks(first, last);
            }
            return 0; //0 tracks deleted.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int removeTrack(final long id) throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().removeTrack(id);
            }
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getMediaMountedCount() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getMediaMountedCount();
            }
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getAudioSessionId() throws RemoteException {
            if (Ref.alive(mService)) {
                return mService.get().getAudioSessionId();
            }
            return -1;
        }

        @Override
        public void shutdown() throws RemoteException {
            if (Ref.alive(mService)) {
                mService.get().shutdown();
            }
        }
    }

    private static final class AudioOnPreparedListener implements MediaPlayer.OnPreparedListener {

        private WeakReference<MusicPlaybackService> serviceRef;

        public AudioOnPreparedListener(WeakReference<MusicPlaybackService> serviceRef) {
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

    public void shutdown() {
        mPlayPos = -1;
        stopForeground(true);
        stopSelf();
    }
}
