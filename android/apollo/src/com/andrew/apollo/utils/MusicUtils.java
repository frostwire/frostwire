/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2021, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrew.apollo.utils;

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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.BaseColumns;
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

import androidx.annotation.RequiresApi;
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
import com.frostwire.android.gui.util.UIUtils;
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

import static android.provider.MediaStore.Audio.AudioColumns.ALBUM_ID;


/**
 * A collection of helpers directly related to music or Apollo's service.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class MusicUtils {

    private static final Logger LOG = Logger.getLogger(MusicUtils.class);

    private static MusicPlaybackService musicPlaybackService = null;

    private static int sForegroundActivities = 0;

    private static final long[] sEmptyList;

    private static ServiceConnectionListener serviceConnectionListener;

    private static ContentValues[] mContentValuesCache = null;

    private static final Object startMusicPlaybackServiceLock = new Object();

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
        musicPlaybackServiceIntent.putExtra(MusicPlaybackService.CMDNAME, MusicPlaybackService.CMDPLAY);
        return musicPlaybackServiceIntent;
    }

    public static void startMusicPlaybackService(final Context context, final Intent intent, Runnable onServiceBoundCallback) {
        // MusicPlaybackService has to be a foreground service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LOG.info("startMusicPlaybackService() startForegroundService(MusicPlaybackService)", true);
                // should end with a android.app.Service#startForeground(int, android.app.Notification) call
                // otherwise if in 5 seconds it's not invoked, the system will crash the app
                context.startForegroundService(intent);
            } else {
                LOG.info("startMusicPlaybackService() startService(MusicPlaybackService)", true);
                context.startService(intent);
            }
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
        try {
            synchronized (startMusicPlaybackServiceLock) {
                serviceConnectionListener = new ServiceConnectionListener(onServiceBoundCallback);
            }
            context.getApplicationContext().bindService(intent, serviceConnectionListener, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            LOG.error("startMusicPlaybackService() error " + t.getMessage(), t);
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
        if (old == 0 || sForegroundActivities == 0) {
            boolean startedServiceNow = false;
            LOG.info("notifyForegroundStateChanged trying to start the MusicPlaybackService");
            final Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.FOREGROUND_STATE_CHANGED);
            intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, sForegroundActivities != 0);
            try {
                if (MusicUtils.isMusicPlaybackServiceRunning()) {
                    // no need to be calling start service to make it do what we want if it's already there
                    LOG.info("notifyForegroundStateChanged() -> telling existing MusicPlaybackService to handle our intent", true);
                    MusicUtils.getMusicPlaybackService().handleCommandIntent(intent);
                }
            } catch (Throwable t) {
                LOG.error("notifyForegroundStateChanged() failed:" + t.getMessage(), t);
            }
        }
    }

    public static ServiceConnectionListener getServiceConnectionListener() {
        return serviceConnectionListener;
    }

    public static MusicPlaybackService getMusicPlaybackService() {
        return musicPlaybackService;
    }

    public static boolean isMusicPlaybackServiceRunning() {
        return musicPlaybackService != null;
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

    public static int deletePlaylist(Activity activity, long playlistId) {
        if (activity.getContentResolver() == null) {
            return 0;
        }
        final Uri mUri = ContentUris.withAppendedId(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                playlistId);
        return activity.getContentResolver().delete(mUri, null, null);
    }

    public static boolean isPaused() {
        return !MusicUtils.isPlaying() && !MusicUtils.isStopped();
    }

    private static class ServiceConnectionListener implements ServiceConnection {
        private static final Logger LOG = Logger.getLogger(ServiceConnectionListener.class);
        private Runnable callback;
        private final AtomicBoolean bound = new AtomicBoolean(false);

        public ServiceConnectionListener(Runnable callback_) {
            callback = callback_;
        }


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicPlaybackService = MusicPlaybackService.getInstance();//IApolloService.Stub.asInterface(service);
            if (musicPlaybackService == null) {
                RuntimeException t = new RuntimeException("MusicUtils::ServiceConnectionListener.onServiceConnected aborted, musicPlaybackService is null, we're calling this too early - check your logic)");
                LOG.error(t.getMessage(), t);
                throw t;
            }
            try {
                LOG.info("ServiceConnectionListener::onServiceConnected(componentName=" + name + ") -> MusicPlaybackService::updateNotification()!", true);
                musicPlaybackService.updateNotification();
            } catch (Throwable e) {
                LOG.error("ServiceConnectionListener::onServiceConnected(componentName=" + name + ") " + e.getMessage(), e, true);
            }

            if (callback != null) {
                try {
                    MusicPlaybackService.safePost(callback);
                } catch (Throwable t) {
                    LOG.info("onServiceConnected() listener threw an exception -> " + t.getMessage(), t);
                }
            }

            // Do not hold on to old Runnable objects, we don't want unexpected things happening later on if we shutdown and restart
            callback = null;
            bound.set(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LOG.info("onServiceDisconnected() invoked!");
            callback = null;
            bound.set(false);
        }

        public boolean isBound() {
            return bound.get();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            LOG.warn("onNullBinding(componentName=" + name + ")");
            callback = null;
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
    public static String makeLabel(final Context context, final int pluralInt,
                                   final int number) {
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

        final String durationFormat = context.getResources().getString(
                hours == 0 ? R.string.durationformatshort : R.string.durationformatlong);
        return String.format(durationFormat, hours, mins, secs);
    }

    /**
     * Changes to the next track
     */

    public static void next() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.stopPlayer();
                musicPlaybackService.gotoNext(true);
            }
        } catch (final Throwable ignored) {
        }
    }


    public static void previous() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.gotoPrev();
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Starts Playback, Pauses, or Resumes.
     */

    public static void playPauseOrResume() {
        try {
            if (musicPlaybackService != null) {
                // PAUSED
                if (!musicPlaybackService.isPlaying() && !musicPlaybackService.isStopped()) {
                    musicPlaybackService.resume();
                }
                // STOPPED or UNSTARTED
                else if (musicPlaybackService.isStopped()) {
                    musicPlaybackService.play();
                }
                // PLAYING
                else if (musicPlaybackService.isPlaying()) {
                    musicPlaybackService.pause();
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Gets back to playing whatever it was playing before.
     */

    public static void play() {
        if (musicPlaybackService != null) {
            try {
                musicPlaybackService.play();
            } catch (Throwable ignored) {
            }
        }
    }


    public static void pause() {
        if (musicPlaybackService != null) {
            try {
                musicPlaybackService.pause();
            } catch (Throwable ignored) {

            }
        }
    }

    /**
     * Cycles through the repeat options.
     */

    public static void cycleRepeat() {
        try {
            if (musicPlaybackService != null) {
                switch (musicPlaybackService.getRepeatMode()) {
                    case MusicPlaybackService.REPEAT_NONE:
                        musicPlaybackService.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        break;
                    case MusicPlaybackService.REPEAT_ALL:
                        musicPlaybackService.setRepeatMode(MusicPlaybackService.REPEAT_CURRENT);
                        break;
                    default:
                        musicPlaybackService.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
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
        if (musicPlaybackService != null) {
            try {
                musicPlaybackService.enableShuffle(!isShuffleEnabled());
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * @return True if we're playing music, false otherwise.
     */

    public static boolean isPlaying() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.isPlaying();
            } catch (final Throwable ignored) {
            }
        }
        return false;
    }


    public static boolean isStopped() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.isStopped();
            } catch (final Throwable ignored) {
            }
        }
        return true;
    }

    /**
     * @return The current shuffle mode.
     */

    public static boolean isShuffleEnabled() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.isShuffleEnabled();
            } catch (final Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * @return The current repeat mode.
     */

    public static int getRepeatMode() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getRepeatMode();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current track name.
     */

    public static String getTrackName() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getTrackName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current artist name.
     */

    public static String getArtistName() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getArtistName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album name.
     */

    public static String getAlbumName() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getAlbumName();
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album Id.
     */

    public static long getCurrentAlbumId() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getAlbumId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id.
     */

    public static long getCurrentAudioId() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getAudioId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id played by Simple Player.
     */

    public static long getCurrentSimplePlayerAudioId() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getCurrentSimplePlayerAudioId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current artist Id.
     */
    public static long getCurrentArtistId() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getArtistId();
            } catch (final Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The audio session Id.
     */

    static int getAudioSessionId() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.getAudioSessionId();
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
            if (musicPlaybackService != null) {
                return musicPlaybackService.getQueue();
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
            if (musicPlaybackService != null) {
                return musicPlaybackService.removeTrack(id);
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
            if (musicPlaybackService != null) {
                return musicPlaybackService.getQueuePosition();
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
        String mSelection = BaseColumns._ID + "=?" + " AND " + AudioColumns.IS_MUSIC + "=1" +
                " AND " + AudioColumns.TITLE + " != ''";//$NON-NLS-2$
        final Cursor cursor;
        cursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        AudioColumns.TITLE,
                        /* 2 */
                        AudioColumns.ARTIST,
                        /* 3 */
                        AudioColumns.ALBUM,
                        /* 4 */
                        AudioColumns.DURATION
                },
                mSelection,
                new String[]{String.valueOf(songId)},
                PreferenceUtils.getInstance().getSongSortOrder());

        if (cursor != null && cursor.getCount() == 1) {
            cursor.moveToFirst();
            cursor.close();
            return new Song(songId, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4));
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
        try {
            final String[] projection = new String[]{
                    BaseColumns._ID
            };
            final String selection = AudioColumns.ARTIST_ID + "=" + id + " AND "
                    + AudioColumns.IS_MUSIC + "=1";
            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                    AudioColumns.ALBUM_KEY + "," + AudioColumns.TRACK);
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
        final String[] projection = new String[]{
                AudioColumns.ALBUM_ID
        };
        //DEBUG (to get all fields, select *)
        //String[] projection = null;
        final String selection = AudioColumns._ID + "=" + songId + " AND " + AudioColumns.IS_MUSIC + "=1";
        Cursor cursor;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null,
                    null
            );
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
        final String[] projection = new String[]{
                AudioColumns.ALBUM
        };
        final String selection = ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC + "=1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null,
                null
        );
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
        if (Looper.myLooper() == null) {
            // The cursor loader can only be created in a Looper Thread.
            Looper.prepare();
        }
        String[] projection = {MediaStore.Downloads.DATA};
        CursorLoader loader = new CursorLoader(context, contentUri, projection, null, null, null);
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

    /**
     * @param context The {@link Context} to use.
     * @param id      The ID of the album.
     * @return The song list for an album.
     */
    public static long[] getSongListForAlbum(final Context context, final long id) {
        final String[] projection = new String[]{
                BaseColumns._ID
        };
        final String selection = ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC
                + "=1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                AudioColumns.TRACK + ", " + MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            return mList;
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
        final String[] projection = new String[]{
                BaseColumns._ID
        };
        final Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
        String selection = AudioColumns.IS_MUSIC + "=1" +
                " AND " + MediaColumns.TITLE + "!=''";
        Cursor cursor = context.getContentResolver().query(uri, projection, selection,
                null, null);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            return mList;
        }
        return sEmptyList;
    }


    public static void playFile(final File file) {
        playFileFromUri(Uri.fromFile(file));
    }

    /**
     * @param uri The source of the file
     */

    public static void playFileFromUri(final Uri uri) {
        if (uri == null || musicPlaybackService == null) {
            return;
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

        try {
            musicPlaybackService.stopPlayer();
            musicPlaybackService.openFile(filename);
            musicPlaybackService.play();
        } catch (final Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    /**
     * @param list         The list of songs to play.
     * @param position     Specify where to start.
     * @param forceShuffle True to force a shuffle, false otherwise.
     */

    public static void playFDs(final long[] list, int position,
                               final boolean forceShuffle) {
        if (list == null) {
            LOG.info("playFDs() aborted, song list null");
            return;
        }
        if (list.length == 0) {
            LOG.info("playFDs() aborted, empty song list");
            return;
        }
        if (musicPlaybackService == null) {
            LOG.info("playFDs() aborted, musicPlaybackService is null");
            return;
        }
        try {
            musicPlaybackService.enableShuffle(forceShuffle);
            final long currentId = musicPlaybackService.getAudioId();
            final int currentQueuePosition = getQueuePosition();
            if (continuedPlayingCurrentQueue(list, position, currentId, currentQueuePosition)) {
                return;
            }
            if (position < 0) {
                position = 0;
            }
            musicPlaybackService.open(list, position);
            musicPlaybackService.play();
        } catch (NullPointerException e) {
            // we are getting this error because musicPlaybackService is
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
                    musicPlaybackService.play();
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
        if (musicPlaybackService == null || list == null) {
            return;
        }
        try {
            musicPlaybackService.enqueue(list, MusicPlaybackService.NEXT);
        } catch (final Throwable ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     */
    public static void shuffleAll(final Context context) {
        // TODO: Check for PHONE_STATE Permissions here.
        Cursor cursor = new SongLoader(context).makeCursor(context);
        final long[] mTrackList = getSongListForCursor(cursor);
        final int position = 0;
        if (mTrackList.length == 0 || musicPlaybackService == null) {
            return;
        }
        try {
            musicPlaybackService.enableShuffle(true);
            final long mCurrentId = musicPlaybackService.getAudioId();
            final int mCurrentQueuePosition = getQueuePosition();

            if (continuedPlayingCurrentQueue(mTrackList, position, mCurrentId, mCurrentQueuePosition)) {
                return;
            }
            musicPlaybackService.open(mTrackList, -1);
            musicPlaybackService.play();
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
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[]{
                        BaseColumns._ID
                }, PlaylistsColumns.NAME + "=?", new String[]{
                        name
                }, PlaylistsColumns.NAME);
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
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, new String[]{
                        BaseColumns._ID
                }, ArtistColumns.ARTIST + "=?", new String[]{
                        name
                }, ArtistColumns.ARTIST);
        return getFirstId(cursor, -1);
    }

    /**
     * Returns the ID for an album.
     *
     * @param context    The {@link Context} to use.
     * @param albumName  The name of the album.
     * @param artistName The name of the artist
     * @return The ID for an album.
     */
    public static long getIdForAlbum(final Context context, final String albumName,
                                     final String artistName) {
        Cursor cursor;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[]{
                            BaseColumns._ID
                    }, AlbumColumns.ALBUM + "=? AND " + AlbumColumns.ARTIST + "=?", new String[]{
                            albumName, artistName
                    }, AlbumColumns.ALBUM);
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

    public static List<Playlist> getPlaylists(final Context context) {
        final List<Playlist> result = new ArrayList<>();
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[]{
                BaseColumns._ID,
                MediaStore.Audio.PlaylistsColumns.NAME
        };

        try {
            final Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    projection, null, null, null);

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
     * @param context The {@link Context} to use.
     * @param name    The name of the new playlist.
     * @return A new playlist ID.
     */
    public static long createPlaylist(final Context context, final String name) {
        long result = -1;
        if (context != null && name != null && name.length() > 0) {
            final ContentResolver resolver = context.getContentResolver();
            final String[] projection = new String[]{
                    PlaylistsColumns.NAME
            };
            final String selection = PlaylistsColumns.NAME + " = ?";
            Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    projection, selection, new String[]{name}, null);
            if (cursor != null && cursor.getCount() <= 0) {
                final ContentValues values = new ContentValues(1);
                values.put(PlaylistsColumns.NAME, name);
                final Uri uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values);

                if (uri != null && uri.getLastPathSegment() != null) {
                    result = Long.parseLong(uri.getLastPathSegment());
                }
            }

            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
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

        if (MusicPlaybackService.getMusicPlayerHandler() != null &&
                MusicPlaybackService.getMusicPlayerHandler().getLooperThread() != Thread.currentThread()) {
            MusicPlaybackService.getMusicPlayerHandler().safePost(() -> addToPlaylist(context, ids, playlistid));
            return;
        }

        long[] currentQueue = getQueue();
        long[] playlist = getSongListForPlaylist(context, playlistid);
        boolean updateQueue = isPlaylistInQueue(playlist, currentQueue);
        final int size = ids.length;
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[]{
                MediaStore.Audio.Playlists.Members._ID
        };
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid);
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
                }
            }
            if (updateQueue) {
                addToQueue(context, ids);
            }
            final String message = context.getResources().getQuantityString(
                    R.plurals.NNNtrackstoplaylist, numinserted, numinserted);

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                        AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
                        refresh();
                    }
            );
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
     * @param id         The id of the song to remove.
     * @param playlistId The id of the playlist being removed from.
     */
    public static void removeFromPlaylist(final Context context,
                                          final long id,
                                          final long playlistId) {
        if (context == null) {
            return;
        }
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        final ContentResolver resolver = context.getContentResolver();
        try {
            resolver.delete(uri, Playlists.Members.AUDIO_ID + " = ? ", new String[]{
                    Long.toString(id)
            });
        } catch (Throwable ignored) {
            // could not acquire provider for uri
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param list    The list to enqueue.
     */
    public static void addToQueue(final Context context, final long[] list) {
        if (context == null || musicPlaybackService == null || list == null) {
            return;
        }
        try {
            musicPlaybackService.enqueue(list, MusicPlaybackService.LAST);
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

        final String[] projection = new String[]{
                BaseColumns._ID, MediaColumns.DATA, MediaColumns.TITLE
        };

        final String selection = BaseColumns._ID + "=" + id;
        try (Cursor cursor = resolver.query(contentUri, projection,
                selection, null, null)) {
            if (cursor != null && cursor.getCount() == 1) {
                cursor.moveToFirst();
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri);
                final String message = context.getString(R.string.set_as_ringtone,
                        cursor.getString(2));
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
        if (context == null || id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{
                    AlbumColumns.NUMBER_OF_SONGS
            }, null, null, null);
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
        if (context == null || id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{
                    AlbumColumns.FIRST_YEAR
            }, null, null, null);
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
            if (musicPlaybackService != null) {
                return musicPlaybackService.getPath();
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
            if (musicPlaybackService != null) {
                musicPlaybackService.moveQueueItem(from, to);
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Toggles the current song as a favorite.
     */
    public static void toggleFavorite() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.toggleFavorite();
            }
        } catch (final Throwable ignored) {
        }
    }

    /**
     * @return True if the current song is a favorite, false otherwise.
     */
    public static boolean isFavorite() {
        try {
            if (musicPlaybackService != null) {
                return musicPlaybackService.isFavorite();
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
            final String[] projection = new String[]{
                    MediaStore.Audio.Playlists.Members.AUDIO_ID
            };
            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                    projection,
                    null,
                    null,
                    MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
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
    public static void makePlaylistMenu(final Context context, final int groupId,
                                        final SubMenu subMenu, final boolean showFavorites) {
        if (context == null) {
            LOG.warn("context was null, not making playlist menu");
            return;
        }

        subMenu.clearHeader();
        if (showFavorites) {
            subMenu.add(groupId, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE,
                    R.string.add_to_favorites).setIcon(R.drawable.contextmenu_icon_favorite);
        }
        subMenu.add(groupId, FragmentMenuItems.NEW_PLAYLIST, Menu.NONE, R.string.new_empty_playlist)
                .setIcon(R.drawable.contextmenu_icon_playlist_add_dark);
        Cursor cursor = PlaylistLoader.makePlaylistCursor(context);
        if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                final Intent intent = new Intent();
                String name = cursor.getString(1);
                if (name != null) {
                    intent.putExtra("playlist", getIdForPlaylist(context, name));
                    subMenu.add(groupId, FragmentMenuItems.PLAYLIST_SELECTED, Menu.NONE,
                            name).setIntent(intent).setIcon(R.drawable.contextmenu_icon_add_to_existing_playlist_dark);
                }
                cursor.moveToNext();
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    /**
     * Called when one of the lists should refresh or re-query.
     * Results in a throttled background task submission on our MusicHandlerThread
     */
    public static void refresh() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.refresh();
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
        if (musicPlaybackService != null) {
            try {
                musicPlaybackService.seek(position);
            } catch (final Throwable ignored) {
            }
        }
    }

    /**
     * @return The current position time of the track
     */
    public static long position() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.position();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The total length of the current track
     */
    public static long duration() {
        if (musicPlaybackService != null) {
            try {
                return musicPlaybackService.duration();
            } catch (final Throwable ignored) {
            }
        }
        return 0;
    }

    /**
     * @param position The position to move the queue to
     */
    public static void setQueuePosition(final int position) {
        if (musicPlaybackService != null) {
            try {
                musicPlaybackService.setQueuePosition(position);
            } catch (final Throwable ignored) {
            }
        }
    }

    /**
     * Clears the queue.
     */
    public static void clearQueue() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.removeTracks(0, Integer.MAX_VALUE);
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
    public static void deleteTracks(final Context context, final long[] list, boolean showNotification) {
        if (list == null) {
            return;
        }
        final String[] projection = new String[]{
                BaseColumns._ID, MediaColumns.DATA, ALBUM_ID
        };
        final StringBuilder selection = new StringBuilder();
        selection.append(BaseColumns._ID + " IN (");
        for (int i = 0; i < list.length; i++) {
            selection.append(list[i]);
            if (i < list.length - 1) {
                selection.append(",");
            }
        }
        selection.append(")");
        final Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection.toString(),
                null, null);
        if (c != null) {
            // Step 1: Remove selected tracks from the current playlist, as well
            // as from the album art cache
            c.moveToFirst();
            while (!c.isAfterLast()) {
                // Remove from current playlist.
                final long id = c.getLong(0);
                removeTrack(id);
                // Remove from the favorites playlist.
                FavoritesStore favoritesStore = FavoritesStore.getInstance(context);
                if (favoritesStore != null) {
                    favoritesStore.removeItem(id);
                }
                // Remove any items in the recent's database
                RecentStore recentStore = RecentStore.getInstance(context);
                if (recentStore != null) {
                    recentStore.removeItem(id);
                }
                // Remove from all remaining playlists.
                removeSongFromAllPlaylists(context, id);
                c.moveToNext();
            }

            // Step 2: Remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    selection.toString(), null);

            // Step 3: Remove files from card
            FileSystem fs = Platforms.fileSystem();
            c.moveToFirst();
            while (!c.isAfterLast()) {
                final String name = c.getString(1);
                try { // File.delete can throw a security exception
                    final File f = new File(name);
                    if (!fs.delete(f)) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e("MusicUtils", "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (final Throwable ex) {
                    c.moveToNext();
                }
            }
            c.close();
            UIUtils.broadcastAction(context,
                    Constants.ACTION_FILE_ADDED_OR_REMOVED,
                    new UIUtils.IntentByteExtra(Constants.EXTRA_REFRESH_FILE_TYPE, Constants.FILE_TYPE_AUDIO));
        }

        if (showNotification) {
            try {
                final String message = makeLabel(context, R.plurals.NNNtracksdeleted, list.length);
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            } catch (Throwable ignored) {
            }
        }

        // We deleted a number of tracks, which could affect any number of
        // things
        // in the media content domain, so update everything.
        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
        // Notify the lists to update
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
        if (adapter.getViewTypeCount() > 1 && position == 0) {
            return;
        }
        final long[] list = MusicUtils.getSongListForAdapter(adapter);
        int pos = adapter.getViewTypeCount() > 1 ? position - 1 : position;
        if (list.length == 0) {
            pos = 0;
        }
        if (getMusicPlaybackService() == null) {
            // first time they invoke us from an ApolloFragment the service
            // needs to be started and we need can get a callback from it
            final Context context = adapter.getContext();
            final int posCopy = pos;
            startMusicPlaybackService(
                    context,
                    buildStartMusicPlaybackServiceIntent(context),
                    () -> MusicUtils.playFDs(list, posCopy, MusicUtils.isShuffleEnabled()));
        } else {
            MusicUtils.playFDs(list, pos, MusicUtils.isShuffleEnabled());
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

    private static long[] getSongListForAdapter(final ArrayAdapter<Song> adapter) {
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
            if (musicPlaybackService != null) {
                musicPlaybackService.playSimple(path);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void stopSimplePlayer() {
        try {
            if (musicPlaybackService != null) {
                musicPlaybackService.stopSimplePlayer();
            }
        } catch (Throwable ignored) {
        }
    }
}
