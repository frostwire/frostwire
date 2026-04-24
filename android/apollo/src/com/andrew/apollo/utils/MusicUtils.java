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

package com.andrew.apollo.utils;

import static android.provider.MediaStore.Audio.AudioColumns.ALBUM_ID;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.ArtistColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Audio.PlaylistsColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.view.Menu;
import android.view.SubMenu;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.loader.content.CursorLoader;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.loaders.FavoritesLoader;
import com.andrew.apollo.loaders.LastAddedLoader;
import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.loaders.SongLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.FavoritesStore.FavoriteColumns;
import com.andrew.apollo.provider.RecentStore;
import com.devspark.appmsg.AppMsg;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * A collection of helpers directly related to music or Apollo's service.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressWarnings("deprecation")
public final class MusicUtils {

    private static final Logger LOG = Logger.getLogger(MusicUtils.class);
    private static final int DELETE_TRACKS_REQUEST_CODE = 0x4155;
    private static final Object pendingTrackDeleteLock = new Object();

    private static java.lang.ref.WeakReference<MusicPlaybackService> musicPlaybackServiceRef = null;
    private static PendingTrackDelete pendingTrackDelete;

    /** Returns the live service instance, or null if it has been GC'd or disconnected. */
    private static MusicPlaybackService getService() {
        MusicPlaybackService service = musicPlaybackServiceRef != null ? musicPlaybackServiceRef.get() : null;
        if (service == null) {
            service = MusicPlaybackService.getInstance();
            if (service != null) {
                musicPlaybackServiceRef = new java.lang.ref.WeakReference<>(service);
            }
        }
        return service;
    }

    private static int sForegroundActivities = 0;

    private static final long[] sEmptyList;

    private static ServiceConnectionListener serviceConnectionListener;

    private static ContentValues[] mContentValuesCache = null;

    private static final Object startMusicPlaybackServiceLock = new Object();

    private static final Object getStartMusicPlaybackServiceLock = new Object();

    private static final class PendingTrackDelete {
        private final Context context;
        private final long[] trackIds;
        private final boolean showNotification;
        private final Runnable onDeleted;

        private PendingTrackDelete(Context context, long[] trackIds, boolean showNotification, Runnable onDeleted) {
            Context appContext = context.getApplicationContext();
            this.context = appContext != null ? appContext : context;
            this.trackIds = trackIds;
            this.showNotification = showNotification;
            this.onDeleted = onDeleted;
        }
    }

    static {
        sEmptyList = new long[0];
    }

    /* This class is never initiated */
    public MusicUtils() {
    }

    public static Intent buildStartMusicPlaybackServiceIntent(final Context context) {
        Intent musicPlaybackServiceIntent = new Intent(context, MusicPlaybackService.class);
        musicPlaybackServiceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        musicPlaybackServiceIntent.setAction(MusicPlaybackService.SERVICECMD);
        return musicPlaybackServiceIntent;
    }

    public static void startMusicPlaybackService(final Context context, final Intent intent, Runnable onServiceBoundCallback) {
        // MusicPlaybackService has to be a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check if conditions are appropriate for starting the service
            if (!SystemUtils.isAppInForeground(context)) {
                LOG.warn("MusicUtils::startMusicPlaybackService() - App is not in foreground, delaying start.");
                return; // Delay or prevent the start if app is not in foreground
            }
        }
        try {
            LOG.info("MusicUtils::startMusicPlaybackService() startForegroundService(MusicPlaybackService)", true);
            // should end with a android.app.Service#startForeground(int, android.app.Notification) call
            // otherwise if in 5 seconds it's not invoked, the system will crash the app
            ContextCompat.startForegroundService(context, intent);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
        if (onServiceBoundCallback == null) {
            LOG.info("MusicUtils::startMusicPlaybackService() onServiceBoundCallback is null, returning early");
            return;
        }
        try {
            synchronized (startMusicPlaybackServiceLock) {
                if (serviceConnectionListener == null || !serviceConnectionListener.isBound() && !context.getApplicationContext().bindService(intent, serviceConnectionListener, 0)) {
                    // No existing listener or it's no longer bound — create a fresh one
                    serviceConnectionListener = new ServiceConnectionListener(onServiceBoundCallback);
                    LOG.info("MusicUtils::startMusicPlaybackService() let's bind the ServiceConnectionListener...");
                    context.getApplicationContext().bindService(intent, serviceConnectionListener, Context.BIND_AUTO_CREATE);
                    LOG.info("MusicUtils::startMusicPlaybackService() ServiceConnectionListener bound");
                } else {
                    // Service is already being connected — just add our callback to the existing listener
                    // so it fires when the service is ready without overwriting others
                    LOG.info("MusicUtils::startMusicPlaybackService() appending callback to existing ServiceConnectionListener");
                    serviceConnectionListener.addCallback(onServiceBoundCallback);
                }
            }
        } catch (Throwable t) {
            LOG.error("MusicUtils::startMusicPlaybackService() error, ServiceConnectionListener not bound " + t.getMessage(), t);
        }
    }

    public static void executeWithMusicPlaybackService(Context context, Runnable runnable) {
        if (!MusicPlaybackService.instanceReady() || MusicPlaybackService.getMusicPlayerHandler() == null) {
            MusicUtils.startMusicPlaybackService(context, MusicUtils.buildStartMusicPlaybackServiceIntent(context), runnable);
        } else {
            MusicPlaybackService.safePost(runnable);
        }
    }

    /**
     * Used to build and show a notification when Apollo is sent into the
     * background
     *
     * @param context The {@link Context} to use.
     */

    public static void notifyForegroundStateChanged(final Context context, boolean inForeground) {
        int old = sForegroundActivities;
        if (inForeground) {
            sForegroundActivities++;
        } else {
            sForegroundActivities--;
        }
        LOG.info("notifyForegroundStateChanged, inForeground=" + inForeground + " oldForegroundActivities=" + old + ", sForegroundActivities=" + sForegroundActivities, true);
        if (old == 0 || sForegroundActivities == 0) {
            boolean startedServiceNow = false;
            LOG.info("notifyForegroundStateChanged trying to start the MusicPlaybackService");
            final Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.FOREGROUND_STATE_CHANGED);
            intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, sForegroundActivities > 0);
            try {
                if (MusicUtils.isMusicPlaybackServiceRunning()) {
                    // no need to be calling start service to make it do what we want if it's already there
                    LOG.info("notifyForegroundStateChanged() -> telling existing MusicPlaybackService to handle our intent (intent=" + intent + ")", true);
                    MusicUtils.getMusicPlaybackService().handleCommandIntent(intent);
                }
            } catch (Throwable t) {
                LOG.error("notifyForegroundStateChanged() failed:" + t.getMessage(), t);
            }
        }
    }

    public static MusicPlaybackService getMusicPlaybackService() {
        synchronized (getStartMusicPlaybackServiceLock) {
            return getService();
        }
    }

    public static boolean isMusicPlaybackServiceRunning() {
        return getMusicPlaybackService() != null;
    }


    public static void requestMusicPlaybackServiceShutdown(Context context) {
        if (context == null) {
            LOG.warn("requestMusicPlaybackServiceShutdown() aborted. context is null.");
            return;
        }
        if (!MusicUtils.isMusicPlaybackServiceRunning()) {
            LOG.info("requestMusicPlaybackServiceShutdown() aborted. MusicPlaybackService has already shutdown.");
            return;
        }
        try {
            final Intent shutdownIntent = new Intent(context, MusicPlaybackService.class);
            shutdownIntent.setAction(MusicPlaybackService.SHUTDOWN_ACTION);
            shutdownIntent.putExtra("force", true);
            LOG.info("MusicUtils.requestMusicPlaybackServiceShutdown() -> sending shut down intent now");
            LOG.info("MusicUtils.requestMusicPlaybackServiceShutdown() -> " + shutdownIntent);
            MusicUtils.getMusicPlaybackService().handleCommandIntent(shutdownIntent);
// Commenting this, it disconnects, but when we launch the service again it doesn't re-connect
//            if (serviceConnectionListener != null) {
//                serviceConnectionListener.onServiceDisconnected(null);
//            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isPaused() {
        return !MusicUtils.isPlaying() && !MusicUtils.isStopped();
    }

    public static void fixPlaylistsOwnership() {
        Context context = MainApplication.context();
        if (context == null) {
            return;
        }

        Uri playlistUri = MusicUtils.getPlaylistContentUri();

        List<String> projectionList = new ArrayList<>();
        projectionList.add(MediaStore.Audio.Playlists._ID);
        projectionList.add(MediaStore.Audio.Playlists.NAME);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projectionList.add("owner_package_name");
        }


        String[] projection = projectionList.toArray(new String[0]);

        try (Cursor cursor = context.getContentResolver().query(playlistUri, projection, null, null, Playlists.DEFAULT_SORT_ORDER)) {

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long playlistId = cursor.getLong(cursor.getColumnIndex(Playlists._ID));
                    String playlistName = cursor.getString(cursor.getColumnIndex(Playlists.NAME));
                    String ownerPackage = null;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        ownerPackage = cursor.getString(cursor.getColumnIndex("owner_package_name"));
                    }

                    // Check if the playlist is owner less
                    if (ownerPackage == null || !ownerPackage.equals(context.getPackageName())) {
                        LOG.info("MusicUtils.fixPlaylistsOwnership: Fixing ownerless playlist: " + playlistName);

                        // Recreate the playlist
                        long newPlaylistId = createPlaylist(context, playlistName, true);
                        if (newPlaylistId != -1) {
                            LOG.info("MusicUtils.fixPlaylistsOwnership: Recreated playlist: " + playlistName + " with new ID: " + newPlaylistId);

                            // Migrate songs from the old playlist to the new one
                            migratePlaylistSongs(context, playlistId, newPlaylistId);

                            // Try to Delete the old ownerless playlist, might not be able to since we don't own it
                            try {
                                deletePlaylist(context, playlistId);
                            } catch (Throwable t) {
                                LOG.warn("MusicUtils.fixPlaylistsOwnership: Failed to delete ownerless playlist: " + playlistName);
                            }
                        } else {
                            LOG.error("MusicUtils.fixPlaylistsOwnership: Failed to recreate ownerless playlist: " + playlistName);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("Error fixing playlists ownership", t);
        }
    }

    /**
     * Migrates songs from one playlist to another.
     */
    private static void migratePlaylistSongs(Context context, long oldPlaylistId, long newPlaylistId) {
        Uri oldPlaylistMembersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", oldPlaylistId);
        Uri newPlaylistMembersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", newPlaylistId);

        Cursor cursor = null;

        try {
            cursor = context.getContentResolver().query(oldPlaylistMembersUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                List<ContentValues> valuesList = new ArrayList<>();

                do {
                    long audioId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID));
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, valuesList.size());
                    valuesList.add(values);
                } while (cursor.moveToNext());

                ContentValues[] valuesArray = valuesList.toArray(new ContentValues[0]);
                int inserted = context.getContentResolver().bulkInsert(newPlaylistMembersUri, valuesArray);

                LOG.info("Migrated " + inserted + " songs from old playlist ID: " + oldPlaylistId + " to new playlist ID: " + newPlaylistId);
            }
        } catch (Throwable t) {
            LOG.error("Error migrating playlist songs", t);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Deletes a playlist from the MediaStore.
     */
    public static int deletePlaylist(@NonNull Context context, long playlistId) {
        if (context.getContentResolver() == null) {
            return 0;
        }
        Uri playlistUri = MusicUtils.getPlaylistContentUri();
        return context.getContentResolver().delete(playlistUri, Playlists._ID + " = ?", new String[]{String.valueOf(playlistId)});
    }


    private static class ServiceConnectionListener implements ServiceConnection {
        private static final Logger LOG = Logger.getLogger(ServiceConnectionListener.class);
        // Use a list so multiple callers (e.g. playEphemeralPlaylistTask + AudioPlayerActivity)
        // can each register a callback without overwriting each other.
        private final java.util.List<Runnable> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicBoolean bound = new AtomicBoolean(false);

        public ServiceConnectionListener(Runnable callback) {
            if (callback != null) {
                callbacks.add(callback);
            }
        }

        public void addCallback(Runnable callback) {
            if (callback != null) {
                callbacks.add(callback);
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlaybackService instance = MusicPlaybackService.getInstance();
            musicPlaybackServiceRef = instance != null ? new java.lang.ref.WeakReference<>(instance) : null;
            if (instance == null) {
                LOG.error("MusicUtils::ServiceConnectionListener.onServiceConnected aborted, getService() is null");
                return;
            }
            try {
                LOG.info("MusicUtils::ServiceConnectionListener::onServiceConnected(componentName=" + name + ") -> MusicPlaybackService::updateNotification()!", true);
                instance.updateNotification();
            } catch (Throwable e) {
                LOG.error("MusicUtils::ServiceConnectionListener::onServiceConnected(componentName=" + name + ") " + e.getMessage(), e, true);
            }

            for (Runnable cb : callbacks) {
                try {
                    LOG.info("MusicUtils::ServiceConnectionListener::onServiceConnected() posting callback...");
                    MusicPlaybackService.safePost(cb);
                } catch (Throwable t) {
                    LOG.warn("MusicUtils::ServiceConnectionListener::onServiceConnected() callback threw: " + t.getMessage());
                }
            }
            try {
                LOG.info("MusicUtils::ServiceConnectionListener::onServiceConnected() posting notification resync after callbacks");
                MusicPlaybackService.safePost(instance::updateNotification);
            } catch (Throwable t) {
                LOG.warn("MusicUtils::ServiceConnectionListener::onServiceConnected() notification resync threw: " + t.getMessage());
            }
            callbacks.clear();
            bound.set(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LOG.info("onServiceDisconnected() invoked!");
            musicPlaybackServiceRef = null;
            callbacks.clear();
            bound.set(false);
        }

        public boolean isBound() {
            return bound.get();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            LOG.warn("onNullBinding(componentName=" + name + ")");
            callbacks.clear();
        }
    }

    /**
     * Used to make number of labels for the number of artists, albums, songs,
     * genres, and playlists.
     *
     * @param context   The {@link Context} to use.
     * @param pluralInt The ID of the plural string to use.
     * @param number    The number of artists, albums, songs, genres, or playlists.
     * @return A {@link String} used as a label for the number of artists,
     * albums, songs, genres, and playlists.
     */
    public static String makeLabel(final Context context, final int pluralInt, final int number) {
        return context.getResources().getQuantityString(pluralInt, number, number);
    }

    /**
     * * Used to create a formatted time string for the duration of tracks.
     *
     * @param context The {@link Context} to use.
     * @param secs    The track in seconds.
     * @return Duration of a track that's properly formatted.
     */
    public static String makeTimeString(final Context context, long secs) {
        long hours, mins;

        hours = secs / 3600;
        secs -= hours * 3600;
        mins = secs / 60;
        secs -= mins * 60;

        final String durationFormat = context.getResources().getString(hours == 0 ? R.string.durationformatshort : R.string.durationformatlong);
        return String.format(durationFormat, hours, mins, secs);
    }

    /**
     * Changes to the next track
     */

    public static void next() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.stopPlayer();
                svc.gotoNext(true);
            }
        } catch (final Throwable ignored) {
        }
    }


    public static void previous() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.gotoPrev();
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Starts Playback, Pauses, or Resumes.
     */

    public static void playPauseOrResume() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                // PAUSED
                if (!svc.isPlaying() && !svc.isStopped()) {
                    svc.resume();
                }
                // STOPPED or UNSTARTED
                else if (svc.isStopped()) {
                    svc.play();
                }
                // PLAYING
                else if (svc.isPlaying()) {
                    svc.pause();
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Gets back to playing whatever it was playing before.
     */

    public static void play() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                svc.play();
            } catch (Throwable ignored) {
            }
        }
    }


    public static void pause() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                svc.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Cycles through the repeat options.
     */

    public static void cycleRepeat() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                switch (svc.getRepeatMode()) {
                    case MusicPlaybackService.REPEAT_NONE:
                        svc.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        break;
                    case MusicPlaybackService.REPEAT_ALL:
                        svc.setRepeatMode(MusicPlaybackService.REPEAT_CURRENT);
                        break;
                    default:
                        svc.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
                        break;
                }
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Cycles through the shuffle options.
     */

    public static void cycleShuffle() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                svc.enableShuffle(!isShuffleEnabled());
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * @return True if we're playing music, false otherwise.
     */

    public static boolean isPlaying() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.isPlaying();
            } catch (final Throwable ignored) {
            }
        }
        return false;
    }


    public static boolean isStopped() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.isStopped();
            } catch (final Throwable ignored) {
            }
        }
        return true;
    }

    /**
     * @return The current shuffle mode.
     */

    public static boolean isShuffleEnabled() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.isShuffleEnabled();
            } catch (final Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * @return The current repeat mode.
     */

    public static int getRepeatMode() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getRepeatMode();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current track name.
     */

    public static String getTrackName() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getTrackName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current artist name.
     */

    public static String getArtistName() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getArtistName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album name.
     */

    public static String getAlbumName() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getAlbumName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album Id.
     */

    public static long getCurrentAlbumId() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getAlbumId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id.
     */

    public static long getCurrentAudioId() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getAudioId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id played by Simple Player.
     */

    public static long getCurrentSimplePlayerAudioId() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getCurrentSimplePlayerAudioId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current artist Id.
     */
    public static long getCurrentArtistId() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getArtistId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The audio session Id.
     */

    static int getAudioSessionId() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.getAudioSessionId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The queue.
     */

    public static long[] getQueue() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                return svc.getQueue();
            }
        } catch (final Throwable ignored) {
        }
        return sEmptyList;
    }

    /**
     * @param id The ID of the track to remove.
     * @return removes track from a playlist or the queue.
     */

    public static int removeTrack(final long id) {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                return svc.removeTrack(id);
            }
        } catch (final Throwable ignored) {
        }
        return 0;
    }

    /**
     * @return The position of the current track in the queue.
     */

    private static int getQueuePosition() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                return svc.getQueuePosition();
            }
        } catch (final Throwable ignored) {
        }
        return 0;
    }

    /**
     * @param cursor The {@link Cursor} used to perform our query.
     * @return The song list for a MIME type.
     */
    public static long[] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        final int len = cursor.getCount();
        final long[] list = new long[len];
        cursor.moveToFirst();
        int columnIndex;
        try {
            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (final IllegalArgumentException notaplaylist) {
            columnIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(columnIndex);
            cursor.moveToNext();
        }
        cursor.close();
        return list;
    }

    public static Song getSong(Context context, final long songId) {
        String mSelection = BaseColumns._ID + "=?" + " AND " + AudioColumns.IS_MUSIC + "=1" + " AND " + AudioColumns.TITLE + " != ''";//$NON-NLS-2$
        final Cursor cursor;
        cursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, new String[]{
                /* 0 */
                BaseColumns._ID,
                /* 1 */
                AudioColumns.TITLE,
                /* 2 */
                AudioColumns.ARTIST,
                /* 3 */
                AudioColumns.ALBUM,
                /* 4 */
                AudioColumns.DURATION}, mSelection, new String[]{String.valueOf(songId)}, PreferenceUtils.getInstance().getSongSortOrder());

        if (cursor != null && cursor.getCount() == 1) {
            cursor.moveToFirst();
            Song result = new Song(songId, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4));
            cursor.close();
            return result;
        } else {
            return null;
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id      The ID of the artist.
     * @return The song list for an artist.
     */
    public static long[] getSongListForArtist(final Context context, final long id) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getSongListForArtist(context, id));
            return sEmptyList;
        }
        if (context == null) {
            return sEmptyList;
        }
        try {
            final String[] projection = new String[]{BaseColumns._ID};
            final String selection = AudioColumns.ARTIST_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC + "=1";
            Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, AudioColumns.ALBUM_KEY + "," + AudioColumns.TRACK);
            if (cursor != null) {
                final long[] mList = getSongListForCursor(cursor);
                cursor.close();
                if (mList == null || mList.length == 0) {
                    return sEmptyList;
                }
                return mList;
            }
        } catch (Throwable t) {
            return sEmptyList;
        }
        return sEmptyList;
    }

    public static long getAlbumIdForSong(final Context context, final long songId) {
        long albumId = -1;
        final String[] projection = new String[]{AudioColumns.ALBUM_ID};
        //DEBUG (to get all fields, select *)
        //String[] projection = null;
        final String selection = AudioColumns._ID + "=" + songId + " AND " + AudioColumns.IS_MUSIC + "=1";
        Cursor cursor;
        try {
            cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null, null);
        } catch (android.database.sqlite.SQLiteException t) {
            cursor = null;
        }
        if (cursor != null) {
            cursor.moveToFirst();
            try {
                // DEBUG: Show me all the fields and values of this table, do we not have an album ID?
//                String[] columnNames = cursor.getColumnNames();
//                int i=0;
//                for (String column : columnNames) {
//                    switch (cursor.getType(i)) {
//                        case Cursor.FIELD_TYPE_STRING:
//                            LOG.info("getAlbumIdForSong: " + column + " = " + cursor.getString(i));
//                            break;
//                        case Cursor.FIELD_TYPE_INTEGER:
//                            LOG.info("getAlbumIdForSong: " + column + " = " + cursor.getLong(i));
//                            break;
//                    }
//                    i++;
//                }
//              albumId = cursor.getLong(cursor.getColumnIndexOrThrow(ALBUM_ID));
                // END OF DEBUG CODE
                albumId = cursor.getLong(0);
            } catch (CursorIndexOutOfBoundsException oob) {
                return -1;
            } finally {
                if (!cursor.isClosed()) {
                    cursor.close();
                }
            }

        }
        return albumId;
    }

    public static String getAlbumName(final Context context, final long id) {
        String albumName = null;
        final String[] projection = new String[]{AudioColumns.ALBUM};
        final String selection = ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC + "=1";
        Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            albumName = cursor.getString(0);
            cursor.close();
        }
        return albumName;
    }

    /**
     * Extracts the path in the DATA column of the media store entry
     * convert a path like -> content://media/external_primary/audio/media/117
     * to content://com.frostwire.android.fileprovider/external_files/emulated/0/Android/data/com.frostwire.android/files/FrostWire/TorrentsData/looklikeyou-soundcloud.mp3
     */
    public static String getDataPathFromMediaStoreContentURI(Context context, Uri contentUri) {
        if (!contentUri.getPath().startsWith("content://media/")) {
            return null;
        }
        if (Looper.myLooper() == null) {
            // The cursor loader can only be created in a Looper Thread.
            Looper.prepare();
        }

        String fileIdString = DocumentsContract.getDocumentId(contentUri).split(":")[1];
        String selection = "_id = ?";
        String[] selectionArgs = {fileIdString};
        String[] projection = {MediaStore.Downloads.DATA};
        Uri uri = Uri.parse("content://media/external_primary/audio/media/");
        CursorLoader loader = new CursorLoader(context, uri, projection, selection, selectionArgs, null);
        Cursor cursor = loader.loadInBackground();
        int column_index;
        if (cursor != null) {
            column_index = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
            cursor.moveToFirst();
            String result = cursor.getString(column_index);
            cursor.close();

            return result;
        }
        return null;
    }

    // content://com.android.providers.downloads.documents/document/raw -> /storage/emulated/...
    public static String getFilePathFromComAndroidProvidersDownloadsDocumentsPath(Context context, String path) {
        if (path != null && (path.startsWith("/storage") || path.startsWith("/root"))) {
            return path;
        }
        if (path != null && path.startsWith("content://media/external_primary")) {
            return MusicUtils.getDataPathFromMediaStoreContentURI(context, Uri.parse(path));
        }
        String cleanFilePath = DocumentsContract.getDocumentId(Uri.parse(path)).split(":")[1];
        return cleanFilePath;
    }

    public static long getFileIdFromComAndroidProvidersDownloadsDocumentsPath(Context context, String path) {
        // remove the raw: from the result with the split "raw:/storage/emulated/... -> /storage/emulated
        LOG.info("MusicUtils.getFileIdFromComAndroidProvidersDownloadsDocumentsPath(path=" + path);
        String selection = MediaColumns.DATA + " = ?";
        String[] selectionArgs = {getFilePathFromComAndroidProvidersDownloadsDocumentsPath(context, path)};
        String[] projection = {MediaColumns._ID};
        Uri uri = Uri.parse("content://media/external_primary/audio/media/");

        CursorLoader loader = new CursorLoader(context, uri, projection, selection, selectionArgs, null);
        Cursor cursor = loader.loadInBackground();
        int column_index;
        if (cursor != null) {
            column_index = cursor.getColumnIndexOrThrow(MediaColumns._ID);
            cursor.moveToFirst();
            long result = cursor.getLong(column_index);
            cursor.close();

            return result;
        }
        return -1;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id      The ID of the album.
     * @return The song list for an album.
     */
    public static long[] getSongListForAlbum(final Context context, final long id) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getSongListForAlbum(context, id));
            return sEmptyList;
        }
        if (context == null) {
            return sEmptyList;
        }
        try {
            final String[] projection = new String[]{BaseColumns._ID};
            final String selection = ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC + "=1";
            Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, AudioColumns.TRACK + ", " + MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            if (cursor != null) {
                final long[] mList = getSongListForCursor(cursor);
                cursor.close();
                return mList;
            }
        } catch (Throwable t) {
            return sEmptyList;
        }
        return sEmptyList;
    }

    /**
     * Plays songs by an artist.
     *
     * @param context  The {@link Context} to use.
     * @param artistId The artist Id.
     * @param position Specify where to start.
     */

    public static void playArtist(final Context context, final long artistId, int position) {
        final long[] artistList = getSongListForArtist(context, artistId);
        if (artistList != null) {
            playFDs(artistList, position, MusicUtils.isShuffleEnabled());
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id      The ID of the genre.
     * @return The song list for an genre.
     */
    public static long[] getSongListForGenre(final Context context, final long id) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getSongListForGenre(context, id));
            return sEmptyList;
        }
        if (context == null) {
            return sEmptyList;
        }
        try {
            final String[] projection = new String[]{BaseColumns._ID};
            final Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
            String selection = AudioColumns.IS_MUSIC + "=1" + " AND " + MediaColumns.TITLE + "!=''";
            Cursor cursor = context.getContentResolver().query(uri, projection, selection, null, null);
            if (cursor != null) {
                final long[] mList = getSongListForCursor(cursor);
                cursor.close();
                return mList;
            }
        } catch (Throwable t) {
            return sEmptyList;
        }
        return sEmptyList;
    }


    public static boolean playFile(final File file) {
        return playFileFromUri(Uri.fromFile(file));
    }

    public static void playFileFromUserItemClick(final Context context, final File file) {
        if (context == null || file == null) {
            return;
        }
        executeWithMusicPlaybackService(context, () -> {
            long fileId = getMediaStoreAudioIdForFile(context, file);
            boolean started;
            if (fileId != -1) {
                playFDs(new long[]{fileId}, 0, false);
                started = true;
            } else {
                started = playFile(file);
            }
            if (started && context instanceof Activity) {
                Activity activity = (Activity) context;
                SystemUtils.postToUIThread(() -> NavUtils.openAudioPlayer(activity));
            }
        });
    }

    public static long getMediaStoreAudioIdForFile(final Context context, final File file) {
        if (context == null || file == null) {
            return -1;
        }
        String path = file.getAbsolutePath();
        long id = queryAudioIdByPath(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, path);
        if (id != -1) {
            return id;
        }
        id = queryAudioIdByPath(context, Uri.parse("content://media/external_primary/audio/media/"), path);
        if (id != -1) {
            return id;
        }
        return queryAudioIdByPath(context, MediaStore.Audio.Media.INTERNAL_CONTENT_URI, path);
    }

    private static long queryAudioIdByPath(final Context context, final Uri uri, final String path) {
        String selection = MediaColumns.DATA + " = ?";
        String[] selectionArgs = {path};
        String[] projection = {MediaColumns._ID};
        CursorLoader loader = new CursorLoader(context, uri, projection, selection, selectionArgs, null);
        Cursor cursor = loader.loadInBackground();
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID));
                }
            } finally {
                cursor.close();
            }
        }
        return -1;
    }

    /**
     * @param uri The source of the file
     */

    public static boolean playFileFromUri(final Uri uri) {
        if (uri == null || getService() == null) {
            return false;
        }

        // If this is a file:// URI, just use the path directly instead
        // of going through the open-from-file descriptor code path.
        String filename;
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            filename = uri.getPath();
        } else {
            filename = uri.toString();
        }

        // Check if we're on the music player handler thread to avoid ANR
        if (MusicPlaybackService.getMusicPlayerHandler() != null && 
            MusicPlaybackService.getMusicPlayerHandler().getLooperThread() != Thread.currentThread()) {
            // Post to background thread to avoid ANR
            final String finalFilename = filename;
            MusicPlaybackService.safePost(() -> playFileFromUriOnHandlerThread(finalFilename));
            return true; // Return true optimistically since we've posted the work
        }

        return playFileFromUriOnHandlerThread(filename);
    }

    /**
     * Performs the actual file opening on the music player handler thread
     * @param filename The filename to open
     * @return true if successful, false otherwise
     */
    private static boolean playFileFromUriOnHandlerThread(String filename) {
        try {
            MusicPlaybackService svc = getService();
            if (svc == null) { return false; }
            return svc.playTransientFile(filename);
        } catch (final Throwable t) {
            LOG.error(t.getMessage(), t);
            return false;
        }
    }

    /**
     * @param list         The list of songs to play.
     * @param position     Specify where to start.
     * @param forceShuffle True to force a shuffle, false otherwise.
     */

    public static void playFDs(final long[] list, int position, final boolean forceShuffle) {
        if (list == null) {
            LOG.info("playFDs() aborted, song list null");
            return;
        }
        if (list.length == 0) {
            LOG.info("playFDs() aborted, empty song list");
            return;
        }
        if (getService() == null) {
            LOG.info("playFDs() aborted, getService() is null");
            return;
        }
        
        // Check if we're on the music player handler thread to avoid ANR
        if (MusicPlaybackService.getMusicPlayerHandler() != null && 
            MusicPlaybackService.getMusicPlayerHandler().getLooperThread() != Thread.currentThread()) {
            // Post to background thread to avoid ANR
            final int finalPosition = position;
            MusicPlaybackService.safePost(() -> playFDsOnHandlerThread(list, finalPosition, forceShuffle));
            return;
        }

        playFDsOnHandlerThread(list, position, forceShuffle);
    }

    /**
     * Performs the actual playback operations on the music player handler thread
     */
    private static void playFDsOnHandlerThread(final long[] list, int position, final boolean forceShuffle) {
        try {
            MusicPlaybackService svc = getService();
            if (svc == null) { return; }
            svc.enableShuffle(forceShuffle);
            final long currentId = svc.getAudioId();
            final int currentQueuePosition = getQueuePosition();
            if (continuedPlayingCurrentQueue(list, position, currentId, currentQueuePosition)) {
                return;
            }
            if (position < 0) {
                position = 0;
            }
            svc.open(list, position);
            svc.play();
        } catch (NullPointerException e) {
            // we are getting this error because getService() is
            // a global static mutable variable, we can't do anything
            // until a full refactor in player
            LOG.error("playAll() Review code logic", e);
        } catch (final Throwable t) {
            LOG.error("playAll() " + t.getMessage(), t);
        }
    }


    private static boolean continuedPlayingCurrentQueue(long[] list, int position, long currentId, int currentQueuePosition) {
        if (position != -1 && currentQueuePosition == position && currentId == list[position]) {
            final long[] playlist = getQueue();
            if (Arrays.equals(list, playlist)) {
                try {
                    MusicPlaybackService svc2 = getService();
                    if (svc2 != null) { svc2.play(); }
                } catch (Throwable t) {
                    t.printStackTrace();
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @param list The list to enqueue.
     */

    public static void playNext(final long[] list) {
        if (getService() == null || list == null) {
            return;
        }
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) { svc.enqueue(list, MusicPlaybackService.NEXT); }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     */
    public static void shuffleAll(final Context context) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> shuffleAll(context));
            return;
        }
        // TODO: Check for PHONE_STATE Permissions here.
        Cursor cursor = new SongLoader(context).makeCursor(context);
        final long[] mTrackList = getSongListForCursor(cursor);
        final int position = 0;
        if (mTrackList.length == 0 || getService() == null) {
            return;
        }
        try {
            MusicPlaybackService svc = getService();
            if (svc == null) { return; }
            svc.enableShuffle(true);
            final long mCurrentId = svc.getAudioId();
            final int mCurrentQueuePosition = getQueuePosition();

            if (continuedPlayingCurrentQueue(mTrackList, position, mCurrentId, mCurrentQueuePosition)) {
                return;
            }
            svc.open(mTrackList, -1);
            svc.play();
            cursor.close();
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Returns The ID for a playlist.
     *
     * @param context The {@link Context} to use.
     * @param name    The name of the playlist.
     * @return The ID for a playlist.
     */
    public static long getIdForPlaylist(final Context context, final String name) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getIdForPlaylist(context, name));
            return -1;
        }
        if (context == null) {
            return -1;
        }
        Uri playlistContentUri = MusicUtils.getPlaylistContentUri();
        Cursor cursor = context.getContentResolver().query(playlistContentUri, new String[]{BaseColumns._ID}, PlaylistsColumns.NAME + "=?", new String[]{name}, PlaylistsColumns.NAME);
        return getFirstId(cursor, -1);
    }

    /**
     * Returns the Id for an artist.
     *
     * @param context The {@link Context} to use.
     * @param name    The name of the artist.
     * @return The ID for an artist.
     */
    public static long getIdForArtist(final Context context, final String name) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getIdForArtist(context, name));
            return -1;
        }
        if (context == null || name == null) {
            return -1;
        }
        try {
            Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, new String[]{BaseColumns._ID}, ArtistColumns.ARTIST + "=?", new String[]{name}, ArtistColumns.ARTIST);
            return getFirstId(cursor, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Returns the ID for an album.
     *
     * @param context    The {@link Context} to use.
     * @param albumName  The name of the album.
     * @param artistName The name of the artist
     * @return The ID for an album.
     */
    public static long getIdForAlbum(final Context context, final String albumName, final String artistName) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getIdForAlbum(context, albumName, artistName));
            return -1;
        }
        Cursor cursor;
        try {
            cursor = context.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[]{BaseColumns._ID}, AlbumColumns.ALBUM + "=? AND " + AlbumColumns.ARTIST + "=?", new String[]{albumName, artistName}, AlbumColumns.ALBUM);
        } catch (Throwable t) {
            return -1;
        }

        int id = -1;
        id = getFirstId(cursor, id);
        return id;
    }

    private static int getFirstId(Cursor cursor, int id) {
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        }
        return id;
    }

    /**
     * Plays songs from an album.
     *
     * @param context  The {@link Context} to use.
     * @param albumId  The album Id.
     * @param position Specify where to start.
     */
    public static void playAlbum(final Context context, final long albumId, int position) {
        final long[] albumList = getSongListForAlbum(context, albumId);
        if (albumList != null) {
            playFDs(albumList, position, MusicUtils.isShuffleEnabled());
        }
    }

    private static void makeInsertItems(final long[] ids, final int offset) {
        final int base = 1000;
        int len = 1000;
        if (offset + len > ids.length) {
            len = ids.length - offset;
        }

        if (mContentValuesCache == null || mContentValuesCache.length != len) {
            mContentValuesCache = new ContentValues[len];
        }
        for (int i = 0; i < len; i++) {
            if (mContentValuesCache[i] == null) {
                mContentValuesCache[i] = new ContentValues();
            }
            mContentValuesCache[i].put(Playlists.Members.PLAY_ORDER, base + offset + i);
            mContentValuesCache[i].put(Playlists.Members.AUDIO_ID, ids[offset + i]);
        }
    }

    /**
     * Resolves the appropriate playlist content URI based on the Android version.
     *
     * @return the appropriate content URI for accessing playlists
     */
    public static Uri getPlaylistContentUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            return MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        }
    }

    public static List<Playlist> getPlaylists(final Context context) {
        final List<Playlist> result = new ArrayList<>();

        try {
            final Cursor cursor = PlaylistLoader.makePlaylistCursor(context);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        result.add(new Playlist(cursor.getLong(0), cursor.getString(1)));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        } catch (Throwable e) {
            LOG.error("Could not fetch playlists", e);
        }

        return result;
    }

    /**
     * @param context                 The {@link Context} to use.
     * @param name                    The name of the new playlist.
     * @param fixingOwnerlessPlaylist If true, will create a new playlist if the given name exists already (because the existing one has no owner)
     * @return A new playlist ID.
     */
    public static long createPlaylist(final Context context, final String name, boolean fixingOwnerlessPlaylist) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> createPlaylist(context, name, fixingOwnerlessPlaylist));
            return 0;
        }
        long result = -1;
        if (context != null && name != null && !name.isEmpty()) {
            final ContentResolver resolver = context.getContentResolver();
            List<String> projectionList = new ArrayList<>();
            projectionList.add(PlaylistsColumns.NAME);

            Uri playlistUri = MusicUtils.getPlaylistContentUri();
            ;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                projectionList.add("owner_package_name");
            }
            final String[] projection = projectionList.toArray(new String[0]);
            final String selection = PlaylistsColumns.NAME + " = ? OR " + PlaylistsColumns.NAME + " = ?";
            Cursor cursor = resolver.query(
                    playlistUri,
                    projection,
                    selection,
                    new String[]{name, name + " "},
                    null
            );

            String ownerPackageName = null;
            boolean alreadyFixed = false;

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            ownerPackageName = cursor.getString(cursor.getColumnIndex("owner_package_name"));
                        } catch (Throwable t) {
                            LOG.error("createPlaylist() cursor.getString() failed: " + t.getMessage(), t, true);
                        }
                    }
                    if (fixingOwnerlessPlaylist && ownerPackageName != null && ownerPackageName.equals(context.getPackageName())) {
                        alreadyFixed = true;
                        break;
                    }
                } while (cursor.moveToNext());
            }

            if (cursor != null) {
                cursor.close();
            }

            if (!alreadyFixed) {
                final ContentValues values = new ContentValues();
                values.put(PlaylistsColumns.NAME, name + (fixingOwnerlessPlaylist ? " " : ""));
                values.put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000);
                values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000);

                final Uri newPlaylistUri = resolver.insert(playlistUri, values);

                if (newPlaylistUri != null && newPlaylistUri.getLastPathSegment() != null) {
                    result = Long.parseLong(newPlaylistUri.getLastPathSegment());
                }
            } else {
                LOG.info("createPlaylist() Playlist with name '" + name + "' already fixed, skipping.");
            }
        }
        return result;
    }


    public static long createPlaylist(final Context context, final String name) {
        return createPlaylist(context, name, false);
    }

    /**
     * @param context    The {@link Context} to use.
     * @param playlistId The playlist ID.
     */
    public static void clearPlaylist(final Context context, final int playlistId) {
        if (context != null) {
            final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
            context.getContentResolver().delete(uri, null, null);
        }
    }

    /**
     * @param context    The {@link Context} to use.
     * @param ids        The id of the song(s) to add.
     * @param playlistid The id of the playlist being added to.
     */
    public static void addToPlaylist(final Context context, final long[] ids, final long playlistid) {
        if (context == null) {
            LOG.warn("context was null, not adding anything to playlist.");
            return;
        }

        if (ids == null) {
            LOG.warn("song ids given null, not adding anything to playlist.");
            return;
        }

        if (ids == sEmptyList) {
            LOG.warn("song ids was empty, not adding anything to playlist.");
            return;
        }

        if (MusicPlaybackService.getMusicPlayerHandler() != null && MusicPlaybackService.getMusicPlayerHandler().getLooperThread() != Thread.currentThread()) {
            MusicPlaybackService.safePost(() -> addToPlaylist(context, ids, playlistid));
            return;
        }

        long[] currentQueue = getQueue();
        long[] playlist = getSongListForPlaylist(context, playlistid);
        boolean updateQueue = isPlaylistInQueue(playlist, currentQueue);
        final int size = ids.length;
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[]{MediaStore.Audio.Playlists.Members._ID};

        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, playlistid);

        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
        } catch (Throwable t) {
            LOG.error("addToPlaylist() resolver.query() failed: " + t.getMessage(), t, true);
        }

        if (cursor != null) {
            cursor.moveToFirst();
            final int base = cursor.getCount();
            cursor.close();
            int numinserted = 0;
            //TODO: Check this portion of code, seems is doing extra work.
            for (int offSet = 0; offSet < size; offSet += 1000) {
                makeInsertItems(ids, offSet);
                try {
                    numinserted += resolver.bulkInsert(uri, mContentValuesCache);
                } catch (Throwable ignored) {
                    LOG.error("MusicUtils.addToPlaylist() resolver.bulkInsert() failed", ignored, true);
                }
            }
            if (updateQueue) {
                addToQueue(context, ids);
            }
            final String message = context.getResources().getQuantityString(R.plurals.NNNtrackstoplaylist, numinserted, numinserted);

            SystemUtils.postToUIThread(() -> {
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
                refresh();
            });
        } else {
            LOG.warn("Unable to complete addToPlaylist, review the logic");
        }
    }

    private static boolean isPlaylistInQueue(long[] currentQueue, long[] playlist) {
        if (playlist.length == 0 || currentQueue.length == 0 || playlist.length > currentQueue.length) {
            return false;
        }
        for (long p : playlist) {
            boolean foundP = false;
            for (long q : currentQueue) {
                if (p == q) {
                    foundP = true;
                    break;
                }
            }
            if (!foundP) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a single track from a given playlist
     *
     * @param context    The {@link Context} to use.
     * @param songId         The songId of the song to remove.
     * @param playlistId The songId of the playlist being removed from.
     */
    public static void removeFromPlaylist(final Context context, final long songId, final long playlistId) {
        if (context == null) {
            return;
        }
        
        // Avoid ANR by ensuring ContentResolver operations run on background thread
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> 
                removeFromPlaylist(context, songId, playlistId));
            return;
        }
        
        Uri baseUri = getPlaylistContentUri();
        Uri uri = Uri.withAppendedPath(baseUri, playlistId + "/members");

        final ContentResolver resolver = context.getContentResolver();
        try {
            int num_deleted = resolver.delete(uri, Playlists.Members.AUDIO_ID + " = ? ", new String[]{Long.toString(songId)});
            LOG.info("MusicUtils.removeFromPlaylist: Removed " + num_deleted + " songs from playlist " + playlistId);
        } catch (Throwable ignored) {
            // could not acquire provider for uri
            LOG.error("MusicUtils.removeFromPlaylist() resolver.delete() failed", ignored, true);
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param list    The list to enqueue.
     */
    public static void addToQueue(final Context context, final long[] list) {
        if (context == null || getService() == null || list == null) {
            return;
        }
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) { svc.enqueue(list, MusicPlaybackService.LAST); }
            final String message = makeLabel(context, R.plurals.NNNtrackstoqueue, list.length);
            AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
        } catch (final Throwable ignored) {
        }
    }

    /**
     * @param context  The {@link Context} to use
     * @param id       The song ID.
     * @param fileType media file type id
     */
    public static void setRingtone(final Context context, final long id, byte fileType) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> setRingtone(context, id, fileType));
            return;
        }
        if (context == null) {
            LOG.warn("context was null, not setting ringtone.");
            return;
        }
        final ContentResolver resolver = context.getContentResolver();

        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        if (fileType == Constants.FILE_TYPE_RINGTONES) {
            contentUri = Media.INTERNAL_CONTENT_URI;
        }

        final Uri uri = ContentUris.withAppendedId(contentUri, id);
        try {
            final ContentValues values = new ContentValues(2);
            values.put(AudioColumns.IS_RINGTONE, "1");
            values.put(AudioColumns.IS_ALARM, "1");
            resolver.update(uri, values, null, null);
        } catch (final Throwable t) {
            //return;
            LOG.error(t.getMessage(), t);
        }

        final String[] projection = new String[]{BaseColumns._ID, MediaColumns.DATA, MediaColumns.TITLE};

        final String selection = BaseColumns._ID + "=" + id;
        try (Cursor cursor = resolver.query(contentUri, projection, selection, null, null)) {
            if (cursor != null && cursor.getCount() == 1) {
                cursor.moveToFirst();
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri);
                final String message = context.getString(R.string.set_as_ringtone, cursor.getString(2));
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            } else {
                UIUtils.showLongMessage(context, R.string.ringtone_not_set);
            }
        } catch (Throwable ignored) {
            UIUtils.showLongMessage(context, R.string.ringtone_not_set);
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id      The id of the album.
     * @return The song count for an album.
     */
    public static String getSongCountForAlbum(final Context context, final long id) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getSongCountForAlbum(context, id));
            return null;
        }
        if (context == null || id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{AlbumColumns.NUMBER_OF_SONGS}, null, null, null);
            return getFirstStringResult(cursor, true);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getFirstStringResult(Cursor cursor, boolean closeCursor) {
        String result = null;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                result = cursor.getString(0);
            }
            if (closeCursor) {
                cursor.close();
            }
        }
        return result;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id      The id of the album.
     * @return The release date for an album.
     */
    static String getReleaseDateForAlbum(final Context context, final long id) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> getReleaseDateForAlbum(context, id));
            return null;
        }
        if (context == null || id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{AlbumColumns.FIRST_YEAR}, null, null, null);
            return getFirstStringResult(cursor, true);
        } catch (Throwable e) {
            // ignore this error since it's not critical
            LOG.error("Error getting release date for album", e);
            return null;
        }
    }

    /**
     * @return The path to the currently playing file as {@link String}
     */
    public static String getFilePath() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                return svc.getPath();
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    /**
     * @param from The index the item is currently at.
     * @param to   The index the item is moving to.
     */
    public static void moveQueueItem(final int from, final int to) {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.moveQueueItem(from, to);
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Toggles the current song as a favorite.
     */
    public static void toggleFavorite() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.toggleFavorite();
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * @return True if the current song is a favorite, false otherwise.
     */
    public static boolean isFavorite() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                return svc.isFavorite();
            }
        } catch (final Throwable ignored) {
        }
        return false;
    }

    /**
     * @param context    The {@link Context} to sue
     * @param playlistId The playlist Id
     * @return The track list for a playlist
     */
    public static long[] getSongListForPlaylist(final Context context, final long playlistId) {
        if (context == null) {
            return sEmptyList;
        }

        try {
            final String[] projection = new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID};
            Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId), projection, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
            if (cursor != null) {
                final long[] list = getSongListForCursor(cursor);
                cursor.close();
                return list;
            }
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
            return sEmptyList;
        }
        return sEmptyList;
    }

    /**
     * Plays a user created playlist.
     *
     * @param context    The {@link Context} to use.
     * @param playlistId The playlist Id.
     */
    public static void playPlaylist(final Context context, final long playlistId) {
        final long[] playlistList = getSongListForPlaylist(context, playlistId);
        if (playlistList != null) {
            playFDs(playlistList, -1, MusicUtils.isShuffleEnabled());
        }
    }

    /**
     * @param cursor The {@link Cursor} used to gather the list in our favorites
     *               database
     * @return The song list for the favorite playlist
     */
    private static long[] getSongListForFavoritesCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        final int len = cursor.getCount();
        final long[] list = new long[len];
        cursor.moveToFirst();
        int colidx = -1;
        try {
            colidx = cursor.getColumnIndexOrThrow(FavoriteColumns.ID);
        } catch (final Exception ignored) {
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(colidx);
            cursor.moveToNext();
        }
        cursor.close();
        return list;
    }

    /**
     * @param context The {@link Context} to use
     * @return The song list from our favorites database
     */
    public static long[] getSongListForFavorites(final Context context) {
        if (context == null) {
            return sEmptyList;
        }
        Cursor cursor = FavoritesLoader.makeFavoritesCursor(context);
        if (cursor != null) {
            final long[] list = getSongListForFavoritesCursor(cursor);
            cursor.close();
            return list;
        }
        return sEmptyList;
    }

    /**
     * Play the songs that have been marked as favorites.
     *
     * @param context The {@link Context} to use
     */
    public static void playFavorites(final Context context) {
        playFDs(getSongListForFavorites(context), 0, MusicUtils.isShuffleEnabled());
    }

    /**
     * @param context The {@link Context} to use
     * @return The song list for the last added playlist
     */
    public static long[] getSongListForLastAdded(final Context context) {
        if (context == null) {
            return sEmptyList;
        }
        final Cursor cursor = LastAddedLoader.makeLastAddedCursor(context);
        if (cursor != null) {
            final int count = cursor.getCount();
            final long[] list = new long[count];
            for (int i = 0; i < count; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            return list;
        }
        return sEmptyList;
    }

    /**
     * Plays the last added songs from the past two weeks.
     *
     * @param context The {@link Context} to use
     */
    public static void playLastAdded(final Context context) {
        playFDs(getSongListForLastAdded(context), 0, MusicUtils.isShuffleEnabled());
    }

    /**
     * Creates a sub menu used to add items to a new playlist or an existing
     * one.
     *
     * @param context       The {@link Context} to use.
     * @param groupId       The group Id of the menu.
     * @param subMenu       The {@link SubMenu} to add to.
     * @param showFavorites True if we should show the option to add to the
     *                      Favorites cache.
     */
    public static void makePlaylistMenu(final Context context, final int groupId, final SubMenu subMenu, final boolean showFavorites) {
        if (context == null) {
            LOG.warn("context was null, not making playlist menu");
            return;
        }

        subMenu.clearHeader();
        if (showFavorites) {
            subMenu.add(groupId, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE, R.string.add_to_favorites).setIcon(R.drawable.contextmenu_icon_favorite);
        }
        subMenu.add(groupId, FragmentMenuItems.NEW_PLAYLIST, Menu.NONE, R.string.new_empty_playlist).setIcon(R.drawable.contextmenu_icon_playlist_add_dark);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            final Cursor cursor = PlaylistLoader.makePlaylistCursor(context);

            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    final Intent intent = new Intent();
                    String name = cursor.getString(1);
                    if (name != null) {
                        intent.putExtra("playlist", getIdForPlaylist(context, name));

                        // Apply tint color to the icon
                        Drawable icon = ContextCompat.getDrawable(context, R.drawable.contextmenu_icon_add_to_existing_playlist_dark);
                        if (icon != null) {
                            icon = DrawableCompat.wrap(icon);
                            DrawableCompat.setTint(icon, UIUtils.getAppIconPrimaryColor(context));
                        }
                        final Drawable icon2 = icon;

                        SystemUtils.postToUIThread(() -> subMenu.add(groupId, FragmentMenuItems.PLAYLIST_SELECTED, Menu.NONE, name).setIntent(intent).setIcon(icon2));
                    }
                    cursor.moveToNext();
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        });
    }

    /**
     * Called when one of the lists should refresh or re-query.
     * Results in a throttled background task submission on our MusicHandlerThread
     */
    public static void refresh() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.refresh();
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Queries {@link RecentStore} for the last album played by an artist
     *
     * @param context    The {@link Context} to use
     * @param artistName The artist name
     * @return The last album name played by an artist
     */
    public static String getLastAlbumForArtist(final Context context, final String artistName) {
        RecentStore recentStore = RecentStore.getInstance(context);
        if (recentStore == null) {
            return null;
        }
        return recentStore.getAlbumName(artistName);
    }

    /**
     * Seeks the current track to a desired position
     *
     * @param position The position to seek to
     */
    public static void seek(final long position) {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                svc.seek(position);
            } catch (final Throwable ignored) {
            }
        }
    }

    /**
     * @return The current position time of the track
     */
    public static long position() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.position();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The total length of the current track
     */
    public static long duration() {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                return svc.duration();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @param position The position to move the queue to
     */
    public static void setQueuePosition(final int position) {
        MusicPlaybackService svc = getService();
        if (svc != null) {
            try {
                svc.setQueuePosition(position);
            } catch (final Throwable ignored) {
            }
        }
    }

    /**
     * Clears the queue.
     */
    public static void clearQueue() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.removeTracks(0, Integer.MAX_VALUE);
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Permanently deletes item(s) from the user's device.
     *
     * @param context The {@link Context} to use.
     * @param list    The item(s) to delete.
     */
    public static void deleteTracks(final Activity activity, final long[] list, final Runnable onDeleted) {
        deleteTracks(activity, list, false, onDeleted);
    }

    public static void deleteTracks(final Activity activity, final long[] list, boolean showNotification, final Runnable onDeleted) {
        if (activity == null || list == null || list.length == 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final ArrayList<Uri> deleteUris = buildTrackDeleteUris(activity, list);
            if (!deleteUris.isEmpty()) {
                try {
                    synchronized (pendingTrackDeleteLock) {
                        pendingTrackDelete = new PendingTrackDelete(activity, Arrays.copyOf(list, list.length), showNotification, onDeleted);
                    }
                    activity.startIntentSenderForResult(
                            MediaStore.createDeleteRequest(activity.getContentResolver(), deleteUris).getIntentSender(),
                            DELETE_TRACKS_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                    );
                    return;
                } catch (Throwable t) {
                    synchronized (pendingTrackDeleteLock) {
                        pendingTrackDelete = null;
                    }
                    LOG.error("deleteTracks: failed to launch MediaStore delete request", t);
                }
            }
        }
        final Context appContext = activity.getApplicationContext() != null ? activity.getApplicationContext() : activity;
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            deleteTracks(appContext, list, showNotification);
            if (onDeleted != null) {
                SystemUtils.postToUIThread(onDeleted);
            }
        });
    }

    public static boolean handleDeleteTracksActivityResult(final Activity activity, int requestCode, int resultCode) {
        if (requestCode != DELETE_TRACKS_REQUEST_CODE) {
            return false;
        }
        final PendingTrackDelete pendingDelete;
        synchronized (pendingTrackDeleteLock) {
            pendingDelete = pendingTrackDelete;
            pendingTrackDelete = null;
        }
        if (pendingDelete == null) {
            return true;
        }
        if (resultCode != Activity.RESULT_OK) {
            LOG.info("deleteTracks: MediaStore delete request cancelled");
            return true;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            cleanupDeletedTracks(pendingDelete.context, pendingDelete.trackIds, pendingDelete.showNotification);
            if (pendingDelete.onDeleted != null) {
                SystemUtils.postToUIThread(pendingDelete.onDeleted);
            }
        });
        return true;
    }

    public static void deleteTracks(final Context context, final long[] list, boolean showNotification) {
        if (context == null || list == null) {
            return;
        }
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> deleteTracks(context, list, showNotification));
            return;
        }
        final String[] projection = new String[]{BaseColumns._ID, MediaColumns.DATA, ALBUM_ID};
        final String selection = buildTrackIdSelection(list);
        final Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null);
        if (c != null) {
            try {
                context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, null);
                FileSystem fs = Platforms.fileSystem();
                if (c.moveToFirst()) {
                    do {
                        final String name = c.getString(1);
                        try {
                            final File f = new File(name);
                            if (!fs.delete(f)) {
                                Log.e("MusicUtils", "Failed to delete file " + name);
                            }
                        } catch (final Throwable ex) {
                            LOG.debug("deleteTracks: file delete failed for " + name + ": " + ex.getMessage());
                        }
                    } while (c.moveToNext());
                }
            } finally {
                c.close();
            }
        }

        cleanupDeletedTracks(context, list, showNotification);
    }

    private static ArrayList<Uri> buildTrackDeleteUris(final Context context, final long[] list) {
        final ArrayList<Uri> deleteUris = new ArrayList<>(list.length);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{BaseColumns._ID},
                    buildTrackIdSelection(list),
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    deleteUris.add(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)));
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            LOG.error("deleteTracks: failed to query track URIs", t);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return deleteUris;
    }

    private static String buildTrackIdSelection(final long[] list) {
        final StringBuilder selection = new StringBuilder();
        selection.append(BaseColumns._ID).append(" IN (");
        for (int i = 0; i < list.length; i++) {
            selection.append(list[i]);
            if (i < list.length - 1) {
                selection.append(",");
            }
        }
        selection.append(")");
        return selection.toString();
    }

    private static void cleanupDeletedTracks(final Context context, final long[] list, boolean showNotification) {
        if (context == null || list == null) {
            return;
        }
        for (long id : list) {
            removeTrack(id);
            removeSongFromAllPlaylists(context, id);
        }

        RecentStore recentStore = RecentStore.getInstance(context);
        FavoritesStore favoritesStore = FavoritesStore.getInstance(context);
        for (long id : list) {
            if (recentStore != null) {
                recentStore.removeItem(id);
            }
            if (favoritesStore != null) {
                favoritesStore.removeItem(id);
            }
        }

        if (showNotification) {
            try {
                final String message = makeLabel(context, R.plurals.NNNtracksdeleted, list.length);
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            } catch (Throwable ignored) {
            }
        }

        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
        refresh();
    }

    public static void deleteTracks(final Context context, final long[] list) {
        // TODO: return the information of real tracks deleted to provide
        // better feedback
        // TODO: refactor to provide better handling of SecurityException
        // ignoring for now, since we can't do anything about it, the result
        // is not tracks are deleted without feedback
        try {
            deleteTracks(context, list, false);
        } catch (SecurityException e) {
            LOG.error("Error in deleteTracks", e);
        }
    }

    public static void playAllFromUserItemClick(final ArrayAdapter<Song> adapter, final int position) {
        playAllFromUserItemClick(adapter, position, null);
    }

    public static void playAllFromUserItemClick(final ArrayAdapter<Song> adapter,
                                                final int position,
                                                final Runnable playbackReadyCallback) {
        if (adapter.getViewTypeCount() > 1 && position == 0) {
            return;
        }
        final long[] list = MusicUtils.getSongListFromAdapter(adapter);
        int pos = adapter.getViewTypeCount() > 1 ? position - 1 : position;
        if (list.length == 0) {
            pos = 0;
        }

        Runnable notifyPlaybackReady = () -> {
            if (playbackReadyCallback != null) {
                SystemUtils.postToUIThread(playbackReadyCallback);
            }
        };

        // Always use the service callback mechanism to ensure proper initialization
        // especially on first startup when service might not be fully connected
        if (!isMusicPlaybackServiceRunning()) {
            LOG.info("playAllFromUserItemClick() service not running, starting it with callback");
            final Context context = adapter.getContext();
            final int posCopy = pos;
            startMusicPlaybackService(context, buildStartMusicPlaybackServiceIntent(context),
                    () -> {
                        MusicUtils.playFDs(list, posCopy, MusicUtils.isShuffleEnabled());
                        notifyPlaybackReady.run();
                    });
        } else {
            LOG.info("playAllFromUserItemClick() service running, playing directly");
            MusicUtils.playFDs(list, pos, MusicUtils.isShuffleEnabled());
            notifyPlaybackReady.run();
        }
    }

    private static void removeSongFromAllPlaylists(final Context context, final long songId) {
        final List<Playlist> playlists = getPlaylists(context);

        if (!playlists.isEmpty()) {
            for (Playlist playlist : playlists) {
                removeFromPlaylist(context, songId, playlist.mPlaylistId);
            }
        }
    }

    private static long[] getSongListFromAdapter(final ArrayAdapter<Song> adapter) {
        if (adapter == null) {
            return sEmptyList;
        }

        int count = adapter.getCount() - (adapter.getViewTypeCount() > 1 ? 1 : 0);
        List<Long> songList = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            try {
                long songId = Objects.requireNonNull(adapter.getItem(i)).mSongId;
                songList.add(songId);
            } catch (Throwable ignored) {
                // possible array out of bounds on adapter.getItem(i)
            }
        }

        if (songList.size() == 0) {
            return sEmptyList;
        }

        // until Java supports primitive types as generics, we'll live with this double copy. O(2n)
        Long[] list = new Long[songList.size()];
        long[] result = ArrayUtils.toPrimitive(songList.toArray(list));
        songList.clear();
        return result;
    }

    public static void playSimple(String path) {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.playSimple(path);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void stopSimplePlayer() {
        try {
            MusicPlaybackService svc = getService();
            if (svc != null) {
                svc.stopSimplePlayer();
            }
        } catch (Throwable ignored) {
        }
    }
}
