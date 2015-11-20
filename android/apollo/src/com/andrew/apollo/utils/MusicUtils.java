/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.utils;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.*;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.view.Menu;
import android.view.SubMenu;
import android.widget.ArrayAdapter;
import com.andrew.apollo.IApolloService;
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
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;


/**
 * A collection of helpers directly related to music or Apollo's service.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class MusicUtils {

    private static Logger LOG = Logger.getLogger(MusicUtils.class);

    public static IApolloService mService = null;

    private static int sForegroundActivities = 0;

    private static final WeakHashMap<Context, ServiceBinder> mConnectionMap;

    private static final long[] sEmptyList;

    private static ContentValues[] mContentValuesCache = null;

    static {
        mConnectionMap = new WeakHashMap<Context, ServiceBinder>();
        sEmptyList = new long[0];
    }

    /* This class is never initiated */
    public MusicUtils() {
    }

    /**
     * @param context The {@link Context} to use
     * @param callback The {@link ServiceConnection} to use
     * @return The new instance of {@link ServiceToken}
     */
    public static final ServiceToken bindToService(final Context context,
            final ServiceConnection callback) {
        Activity realActivity = ((Activity)context).getParent();
        if (realActivity == null) {
            realActivity = (Activity)context;
        }
        final ContextWrapper contextWrapper = new ContextWrapper(realActivity);
        contextWrapper.startService(new Intent(contextWrapper, MusicPlaybackService.class));
        final ServiceBinder binder = new ServiceBinder(callback);
        if (contextWrapper.bindService(
                new Intent().setClass(contextWrapper, MusicPlaybackService.class), binder, 0)) {
            mConnectionMap.put(contextWrapper, binder);
            return new ServiceToken(contextWrapper);
        }
        return null;
    }

    /**
     * @param token The {@link ServiceToken} to unbind from
     */
    public static void unbindFromService(final ServiceToken token) {
        if (token == null) {
            return;
        }
        final ContextWrapper mContextWrapper = token.mWrappedContext;
        final ServiceBinder mBinder = mConnectionMap.remove(mContextWrapper);
        if (mBinder == null) {
            return;
        }
        mContextWrapper.unbindService(mBinder);
        if (mConnectionMap.isEmpty()) {
            mService = null;
        }
    }

    public static final class ServiceBinder implements ServiceConnection {
        private final ServiceConnection mCallback;

        /**
         * Constructor of <code>ServiceBinder</code>
         *
         * @param callback The {@link ServiceConnection} to use
         */
        public ServiceBinder(final ServiceConnection callback) {
            mCallback = callback;
        }

        @Override
        public void onServiceConnected(final ComponentName className, final IBinder service) {
            mService = IApolloService.Stub.asInterface(service);
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            mService = null;
        }
    }

    public static final class ServiceToken {
        public ContextWrapper mWrappedContext;

        /**
         * Constructor of <code>ServiceToken</code>
         *
         * @param context The {@link ContextWrapper} to use
         */
        public ServiceToken(final ContextWrapper context) {
            mWrappedContext = context;
        }
    }

    /**
     * Used to make number of labels for the number of artists, albums, songs,
     * genres, and playlists.
     *
     * @param context The {@link Context} to use.
     * @param pluralInt The ID of the plural string to use.
     * @param number The number of artists, albums, songs, genres, or playlists.
     * @return A {@link String} used as a label for the number of artists,
     *         albums, songs, genres, and playlists.
     */
    public static final String makeLabel(final Context context, final int pluralInt,
            final int number) {
        return context.getResources().getQuantityString(pluralInt, number, number);
    }

    /**
     * * Used to create a formatted time string for the duration of tracks.
     *
     * @param context The {@link Context} to use.
     * @param secs The track in seconds.
     * @return Duration of a track that's properly formatted.
     */
    public static final String makeTimeString(final Context context, long secs) {
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
            if (mService != null) {
                mService.next();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Changes to the previous track.
     *
     * @NOTE The AIDL isn't used here in order to properly use the previous
     *       action. When the user is shuffling, because {@link
     *       MusicPlaybackService#openCurrentAndNext()} is used, the user won't
     *       be able to travel to the previously skipped track. To remedy this,
     *       {@link MusicPlaybackService#openCurrent()} is called in {@link
     *       MusicPlaybackService#prev()}. {@code #startService(Intent intent)}
     *       is called here to specifically invoke the onStartCommand used by
     *       {@link MusicPlaybackService}, which states if the current position
     *       less than 2000 ms, start the track over, otherwise move to the
     *       previously listened track.
     */
    public static void previous(final Context context) {
        final Intent previous = new Intent(context, MusicPlaybackService.class);
        previous.setAction(MusicPlaybackService.PREVIOUS_ACTION);
        context.startService(previous);
    }

    /**
     * Plays or pauses the music.
     */
    public static void playOrPause() {
        try {
            // TODO: Check for PHONE_STATE Permissions here.
            if (mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Cycles through the repeat options.
     */
    public static void cycleRepeat() {
        try {
            if (mService != null) {
                switch (mService.getRepeatMode()) {
                    case MusicPlaybackService.REPEAT_NONE:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        break;
                    case MusicPlaybackService.REPEAT_ALL:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_CURRENT);
                        if (mService.getShuffleMode() != MusicPlaybackService.SHUFFLE_NONE) {
                            mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        }
                        break;
                    default:
                        mService.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
                        break;
                }
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Cycles through the shuffle options.
     */
    public static void cycleShuffle() {
        try {
            if (mService != null) {
                switch (mService.getShuffleMode()) {
                    case MusicPlaybackService.SHUFFLE_NONE:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
                        if (mService.getRepeatMode() == MusicPlaybackService.REPEAT_CURRENT) {
                            mService.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
                        }
                        break;
                    case MusicPlaybackService.SHUFFLE_NORMAL:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        break;
                    case MusicPlaybackService.SHUFFLE_AUTO:
                        mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
                        break;
                    default:
                        break;
                }
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @return True if we're playing music, false otherwise.
     */
    public static final boolean isPlaying() {
        if (mService != null) {
            try {
                return mService.isPlaying();
            } catch (final RemoteException ignored) {
            }
        }
        return false;
    }

    public static final boolean isStopped() {
        if (mService != null) {
            try {
                return mService.isStopped();
            } catch (final RemoteException ignored) {
            }
        }
        return true;
    }

    /**
     * @return The current shuffle mode.
     */
    public static final int getShuffleMode() {
        if (mService != null) {
            try {
                return mService.getShuffleMode();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current repeat mode.
     */
    public static final int getRepeatMode() {
        if (mService != null) {
            try {
                return mService.getRepeatMode();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The current track name.
     */
    public static final String getTrackName() {
        if (mService != null) {
            try {
                return mService.getTrackName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current artist name.
     */
    public static final String getArtistName() {
        if (mService != null) {
            try {
                return mService.getArtistName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album name.
     */
    public static final String getAlbumName() {
        if (mService != null) {
            try {
                return mService.getAlbumName();
            } catch (final RemoteException ignored) {
            }
        }
        return null;
    }

    /**
     * @return The current album Id.
     */
    public static final long getCurrentAlbumId() {
        if (mService != null) {
            try {
                return mService.getAlbumId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current song Id.
     */
    public static final long getCurrentAudioId() {
        if (mService != null) {
            try {
                return mService.getAudioId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The current artist Id.
     */
    public static final long getCurrentArtistId() {
        if (mService != null) {
            try {
                return mService.getArtistId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The audio session Id.
     */
    public static final int getAudioSessionId() {
        if (mService != null) {
            try {
                return mService.getAudioSessionId();
            } catch (final RemoteException ignored) {
            }
        }
        return -1;
    }

    /**
     * @return The queue.
     */
    public static final long[] getQueue() {
        try {
            if (mService != null) {
                return mService.getQueue();
            } else {
            }
        } catch (final RemoteException ignored) {
        }
        return sEmptyList;
    }

    /**
     * @param id The ID of the track to remove.
     * @return removes track from a playlist or the queue.
     */
    public static final int removeTrack(final long id) {
        try {
            if (mService != null) {
                return mService.removeTrack(id);
            }
        } catch (final RemoteException ingored) {
        }
        return 0;
    }

    /**
     * @return The position of the current track in the queue.
     */
    public static final int getQueuePosition() {
        try {
            if (mService != null) {
                return mService.getQueuePosition();
            }
        } catch (final RemoteException ignored) {
        }
        return 0;
    }

    /**
     * @param cursor The {@link Cursor} used to perform our query.
     * @return The song list for a MIME type.
     */
    public static final long[] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        final int len = cursor.getCount();
        final long[] list = new long[len];
        cursor.moveToFirst();
        int columnIndex = -1;
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
        cursor = null;
        return list;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the artist.
     * @return The song list for an artist.
     */
    public static final long[] getSongListForArtist(final Context context, final long id) {
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
                if (mList == null) {
                    return sEmptyList;
                }
                return mList;
            }
        } catch (Throwable t) {
          return sEmptyList;
        }
        return sEmptyList;
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the album.
     * @return The song list for an album.
     */
    public static final long[] getSongListForAlbum(final Context context, final long id) {
        final String[] projection = new String[] {
            BaseColumns._ID
        };
        final String selection = AudioColumns.ALBUM_ID + "=" + id + " AND " + AudioColumns.IS_MUSIC
                + "=1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                AudioColumns.TRACK + ", " + MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return mList;
        }
        return sEmptyList;
    }

    /**
     * Plays songs by an artist.
     *
     * @param context The {@link Context} to use.
     * @param artistId The artist Id.
     * @param position Specify where to start.
     */
    public static void playArtist(final Context context, final long artistId, int position) {
        final long[] artistList = getSongListForArtist(context, artistId);
        if (artistList != null) {
            playAll(context, artistList, position, false);
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The ID of the genre.
     * @return The song list for an genre.
     */
    public static final long[] getSongListForGenre(final Context context, final long id) {
        final String[] projection = new String[] {
            BaseColumns._ID
        };
        final StringBuilder selection = new StringBuilder();
        selection.append(AudioColumns.IS_MUSIC + "=1");
        selection.append(" AND " + MediaColumns.TITLE + "!=''");
        final Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", Long.valueOf(id));
        Cursor cursor = context.getContentResolver().query(uri, projection, selection.toString(),
                null, null);
        if (cursor != null) {
            final long[] mList = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return mList;
        }
        return sEmptyList;
    }

    /**
     * @param context The {@link Context} to use
     * @param uri The source of the file
     */
    public static void playFile(final Context context, final Uri uri) {
        // TODO: Check for PHONE_STATE Permissions here.

        if (uri == null || mService == null) {
            return;
        }

        // If this is a file:// URI, just use the path directly instead
        // of going through the open-from-filedescriptor codepath.
        String filename;
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            filename = uri.getPath();
        } else {
            filename = uri.toString();
        }

        try {
            mService.stop();
            mService.openFile(filename);
            mService.play();
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param list The list of songs to play.
     * @param position Specify where to start.
     * @param forceShuffle True to force a shuffle, false otherwise.
     */
    public static void playAll(final Context context, final long[] list, int position,
            final boolean forceShuffle) {
        // TODO: Check for PHONE_STATE Permissions here.

        if (list == null || list.length == 0 || mService == null) {
            return;
        }

        try {
            if (forceShuffle) {
                mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
            } else {
                mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NONE);
            }
            final long currentId = mService.getAudioId();
            final int currentQueuePosition = getQueuePosition();
            if (position != -1 && currentQueuePosition == position && currentId == list[position]) {
                final long[] playlist = getQueue();
                if (Arrays.equals(list, playlist)) {
                    mService.play();
                    return;
                }
            }
            if (position < 0) {
                position = 0;
            }

            mService.open(list, forceShuffle ? -1 : position);
            mService.play();

        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param list The list to enqueue.
     */
    public static void playNext(final long[] list) {
        if (mService == null || list == null) {
            return;
        }
        try {
            mService.enqueue(list, MusicPlaybackService.NEXT);
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use.
     */
    public static void shuffleAll(final Context context) {
        // TODO: Check for PHONE_STATE Permissions here.

        Cursor cursor = SongLoader.makeSongCursor(context);
        final long[] mTrackList = getSongListForCursor(cursor);
        final int position = 0;
        if (mTrackList.length == 0 || mService == null) {
            return;
        }
        try {
            mService.setShuffleMode(MusicPlaybackService.SHUFFLE_NORMAL);
            final long mCurrentId = mService.getAudioId();
            final int mCurrentQueuePosition = getQueuePosition();
            if (position != -1 && mCurrentQueuePosition == position
                    && mCurrentId == mTrackList[position]) {
                final long[] mPlaylist = getQueue();
                if (Arrays.equals(mTrackList, mPlaylist)) {
                    mService.play();
                    return;
                }
            }

            if (mTrackList != null && mTrackList.length > 0) {
                mService.open(mTrackList, -1);
                mService.play();
            }
            cursor.close();
            cursor = null;
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Returns The ID for a playlist.
     *
     * @param context The {@link Context} to use.
     * @param name The name of the playlist.
     * @return The ID for a playlist.
     */
    public static final long getIdForPlaylist(final Context context, final String name) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[]{
                        BaseColumns._ID
                }, PlaylistsColumns.NAME + "=?", new String[]{
                        name
                }, PlaylistsColumns.NAME);
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
            cursor = null;
        }
        return id;
    }

    /**
     * Returns the Id for an artist.
     *
     * @param context The {@link Context} to use.
     * @param name The name of the artist.
     * @return The ID for an artist.
     */
    public static final long getIdForArtist(final Context context, final String name) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, new String[] {
                    BaseColumns._ID
                }, ArtistColumns.ARTIST + "=?", new String[] {
                    name
                }, ArtistColumns.ARTIST);
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
            cursor = null;
        }
        return id;
    }

    /**
     * Returns the ID for an album.
     *
     * @param context The {@link Context} to use.
     * @param albumName The name of the album.
     * @param artistName The name of the artist
     * @return The ID for an album.
     */
    public static final long getIdForAlbum(final Context context, final String albumName,
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
     * @param context The {@link Context} to use.
     * @param albumId The album Id.
     * @param position Specify where to start.
     */
    public static void playAlbum(final Context context, final long albumId, int position) {
        final long[] albumList = getSongListForAlbum(context, albumId);
        if (albumList != null) {
            playAll(context, albumList, position, false);
        }
    }

    /*  */
    public static void makeInsertItems(final long[] ids, final int offset, int len, final int base) {
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
        final List<Playlist> result = new ArrayList<Playlist>();
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[]{
                BaseColumns._ID,
                MediaStore.Audio.PlaylistsColumns.NAME
        };

        try {
            final Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    projection, null, null, null);

            if (cursor != null) {
                cursor.moveToFirst();
                do {
                    result.add(new Playlist(cursor.getLong(0), cursor.getString(1)));
                } while (cursor.moveToNext());

                cursor.close();
            }
        } catch (Throwable e) {
            LOG.error("Could not fetch playlists", e);
        }

        return result;
    }

    /**
     * @param context The {@link Context} to use.
     * @param name The name of the new playlist.
     * @return A new playlist ID.
     */
    public static final long createPlaylist(final Context context, final String name) {
        long result = -1;
        if (name != null && name.length() > 0) {
            final ContentResolver resolver = context.getContentResolver();
            final String[] projection = new String[] {
                PlaylistsColumns.NAME
            };
            final String selection = PlaylistsColumns.NAME + " = ?";
            Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    projection, selection, new String[] {name}, null);
            if (cursor != null && cursor.getCount() <= 0) {
                final ContentValues values = new ContentValues(1);
                values.put(PlaylistsColumns.NAME, name);
                final Uri uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values);

                if (uri != null) {
                    result = Long.parseLong(uri.getLastPathSegment());
                }
            }

            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return result;
    }

    /**
     * @param context The {@link Context} to use.
     * @param playlistId The playlist ID.
     */
    public static void clearPlaylist(final Context context, final int playlistId) {
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        context.getContentResolver().delete(uri, null, null);
        return;
    }

    /**
     * @param context The {@link Context} to use.
     * @param ids The id of the song(s) to add.
     * @param playlistid The id of the playlist being added to.
     */
    public static void addToPlaylist(final Context context, final long[] ids, final long playlistid) {
        if (ids == null) {
            LOG.warn("song ids given null, not adding anything to playlist.");
            return;
        }

        if (ids == sEmptyList) {
            LOG.warn("song ids was empty, not adding anything to playlist.");
            return;
        }

        final int size = ids.length;
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[] {
            "count(*)"
        };
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid);
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
        } catch (Throwable ignored) {}

        if (cursor != null) {
            cursor.moveToFirst();
            final int base = cursor.getInt(0);
            cursor.close();
            int numinserted = 0;
            for (int offSet = 0; offSet < size; offSet += 1000) {
                makeInsertItems(ids, offSet, 1000, base);
                numinserted += resolver.bulkInsert(uri, mContentValuesCache);
            }
            final String message = context.getResources().getQuantityString(
                    R.plurals.NNNtrackstoplaylist, numinserted, numinserted);
            AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
        } else {
            LOG.warn("Unable to complete addToPlaylist, review the logic");
        }
    }

    /**
     * Removes a single track from a given playlist
     * @param context The {@link Context} to use.
     * @param id The id of the song to remove.
     * @param playlistId The id of the playlist being removed from.
     * @param showNotification if true shows a notification at the top.
     */
    public static void removeFromPlaylist(final Context context, final long id,
            final long playlistId, boolean showNotification) {
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        final ContentResolver resolver = context.getContentResolver();
        resolver.delete(uri, Playlists.Members.AUDIO_ID + " = ? ", new String[]{
                Long.toString(id)
        });

        if (showNotification) {
            try {
                final String message = context.getResources().getQuantityString(
                        R.plurals.NNNtracksfromplaylist, 1, 1);
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            } catch (Throwable t) {
                // java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
            }
        }
    }

    /**
     * Removes a single track from a given playlist
     * @param context The {@link Context} to use.
     * @param id The id of the song to remove.
     * @param playlistId The id of the playlist being removed from.
     */
    public static void removeFromPlaylist(final Context context, final long id,
                                          final long playlistId) {
        removeFromPlaylist(context, id, playlistId, false);
    }

    /**
     * @param context The {@link Context} to use.
     * @param list The list to enqueue.
     */
    public static void addToQueue(final Context context, final long[] list) {
        if (mService == null || list == null) {
            return;
        }
        try {
            mService.enqueue(list, MusicPlaybackService.LAST);
            final String message = makeLabel(context, R.plurals.NNNtrackstoqueue, list.length);
            AppMsg.makeText((Activity) context, message, AppMsg.STYLE_CONFIRM).show();
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @param context The {@link Context} to use
     * @param id The song ID.
     */
    public static void setRingtone(final Context context, final long id) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            final ContentValues values = new ContentValues(2);
            values.put(AudioColumns.IS_RINGTONE, "1");
            values.put(AudioColumns.IS_ALARM, "1");
            resolver.update(uri, values, null, null);
        } catch (final UnsupportedOperationException ingored) {
            return;
        }

        final String[] projection = new String[] {
                BaseColumns._ID, MediaColumns.DATA, MediaColumns.TITLE
        };

        final String selection = BaseColumns._ID + "=" + id;
        Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                selection, null, null);
        try {
            if (cursor != null && cursor.getCount() == 1) {
                cursor.moveToFirst();
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri);
                final String message = context.getString(R.string.set_as_ringtone,
                        cursor.getString(2));
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            }
        } catch (Throwable ignored) {
            UIUtils.showLongMessage(context, R.string.ringtone_not_set);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The id of the album.
     * @return The song count for an album.
     */
    public static final String getSongCountForAlbum(final Context context, final long id) {
        if (id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{
                    AlbumColumns.NUMBER_OF_SONGS
            }, null, null, null);
            String songCount = null;
            if (cursor != null) {
                cursor.moveToFirst();
                if (!cursor.isAfterLast()) {
                    songCount = cursor.getString(0);
                }
                cursor.close();
                cursor = null;
            }
            return songCount;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param context The {@link Context} to use.
     * @param id The id of the album.
     * @return The release date for an album.
     */
    public static final String getReleaseDateForAlbum(final Context context, final long id) {
        if (id == -1) {
            return null;
        }
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{
                    AlbumColumns.FIRST_YEAR
            }, null, null, null);
            String releaseDate = null;
            if (cursor != null) {
                cursor.moveToFirst();
                if (!cursor.isAfterLast()) {
                    releaseDate = cursor.getString(0);
                }
                cursor.close();
                cursor = null;
            }
            return releaseDate;
        } catch (Throwable e) {
            // ignore this error since it's not critical
            LOG.error("Error getting release date for album", e);
            return null;
        }
    }

    /**
     * @return The path to the currently playing file as {@link String}
     */
    public static final String getFilePath() {
        try {
            if (mService != null) {
                return mService.getPath();
            }
        } catch (final RemoteException ignored) {
        }
        return null;
    }

    /**
     * @param from The index the item is currently at.
     * @param to The index the item is moving to.
     */
    public static void moveQueueItem(final int from, final int to) {
        try {
            if (mService != null) {
                mService.moveQueueItem(from, to);
            } else {
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Toggles the current song as a favorite.
     */
    public static void toggleFavorite() {
        try {
            if (mService != null) {
                mService.toggleFavorite();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * @return True if the current song is a favorite, false otherwise.
     */
    public static final boolean isFavorite() {
        try {
            if (mService != null) {
                return mService.isFavorite();
            }
        } catch (final RemoteException ignored) {
        }
        return false;
    }

    /**
     * @param context The {@link Context} to sue
     * @param playlistId The playlist Id
     * @return The track list for a playlist
     */
    public static final long[] getSongListForPlaylist(final Context context, final long playlistId) {
        final String[] projection = new String[] {
            MediaStore.Audio.Playlists.Members.AUDIO_ID
        };
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external",
                        Long.valueOf(playlistId)), projection, null, null,
                MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            final long[] list = getSongListForCursor(cursor);
            cursor.close();
            cursor = null;
            return list;
        }
        return sEmptyList;
    }

    /**
     * Plays a user created playlist.
     *
     * @param context The {@link Context} to use.
     * @param playlistId The playlist Id.
     */
    public static void playPlaylist(final Context context, final long playlistId) {
        final long[] playlistList = getSongListForPlaylist(context, playlistId);
        if (playlistList != null) {
            playAll(context, playlistList, -1, false);
        }
    }

    /**
     * @param cursor The {@link Cursor} used to gather the list in our favorites
     *            database
     * @return The song list for the favorite playlist
     */
    public final static long[] getSongListForFavoritesCursor(Cursor cursor) {
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
        cursor = null;
        return list;
    }

    /**
     * @param context The {@link Context} to use
     * @return The song list from our favorites database
     */
    public final static long[] getSongListForFavorites(final Context context) {
        Cursor cursor = FavoritesLoader.makeFavoritesCursor(context);
        if (cursor != null) {
            final long[] list = getSongListForFavoritesCursor(cursor);
            cursor.close();
            cursor = null;
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
        playAll(context, getSongListForFavorites(context), 0, false);
    }

    /**
     * @param context The {@link Context} to use
     * @return The song list for the last added playlist
     */
    public static final long[] getSongListForLastAdded(final Context context) {
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
        playAll(context, getSongListForLastAdded(context), 0, false);
    }

    /**
     * Creates a sub menu used to add items to a new playlist or an existsing
     * one.
     *
     * @param context The {@link Context} to use.
     * @param groupId The group Id of the menu.
     * @param subMenu The {@link SubMenu} to add to.
     * @param showFavorites True if we should show the option to add to the
     *            Favorites cache.
     */
    public static void makePlaylistMenu(final Context context, final int groupId,
            final SubMenu subMenu, final boolean showFavorites) {
        subMenu.clear();
        if (showFavorites) {
            subMenu.add(groupId, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE,
                    R.string.add_to_favorites);
        }
        subMenu.add(groupId, FragmentMenuItems.NEW_PLAYLIST, Menu.NONE, R.string.new_playlist);
        Cursor cursor = PlaylistLoader.makePlaylistCursor(context);
        if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                final Intent intent = new Intent();
                String name = cursor.getString(1);
                if (name != null) {
                    intent.putExtra("playlist", getIdForPlaylist(context, name));
                    subMenu.add(groupId, FragmentMenuItems.PLAYLIST_SELECTED, Menu.NONE,
                            name).setIntent(intent);
                }
                cursor.moveToNext();
            }
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }

    /**
     * Called when one of the lists should refresh or requery.
     */
    public static void refresh() {
        try {
            if (mService != null) {
                mService.refresh();
            }
        } catch (final RemoteException ignored) {
        }
    }

    /**
     * Queries {@link RecentStore} for the last album played by an artist
     *
     * @param context The {@link Context} to use
     * @param artistName The artist name
     * @return The last album name played by an artist
     */
    public static final String getLastAlbumForArtist(final Context context, final String artistName) {
        return RecentStore.getInstance(context).getAlbumName(artistName);
    }

    /**
     * Seeks the current track to a desired position
     *
     * @param position The position to seek to
     */
    public static void seek(final long position) {
        if (mService != null) {
            try {
                mService.seek(position);
            } catch (final RemoteException ignored) {
            }
        }
    }

    /**
     * @return The current position time of the track
     */
    public static final long position() {
        if (mService != null) {
            try {
                return mService.position();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @return The total length of the current track
     */
    public static final long duration() {
        if (mService != null) {
            try {
                return mService.duration();
            } catch (final RemoteException ignored) {
            }
        }
        return 0;
    }

    /**
     * @param position The position to move the queue to
     */
    public static void setQueuePosition(final int position) {
        if (mService != null) {
            try {
                mService.setQueuePosition(position);
            } catch (final RemoteException ignored) {
            }
        }
    }

    /**
     * Clears the qeueue
     */
    public static void clearQueue() {
        try {
            mService.removeTracks(0, Integer.MAX_VALUE);
        } catch (final RemoteException ignored) {
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
            final Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.FOREGROUND_STATE_CHANGED);
            intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, sForegroundActivities != 0);
            context.startService(intent);
        }
    }

    /**
     * Perminately deletes item(s) from the user's device
     *
     * @param context The {@link Context} to use.
     * @param list The item(s) to delete.
     */
    public static void deleteTracks(final Context context, final long[] list, boolean showNotification) {
        if (list == null) {
            return;
        }
        final String[] projection = new String[] {
                BaseColumns._ID, MediaColumns.DATA, AudioColumns.ALBUM_ID
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
                FavoritesStore.getInstance(context).removeItem(id);
                // Remove any items in the recents database
                RecentStore.getInstance(context).removeItem(c.getLong(2));
                // Remove from all remaining playlists.
                removeSongFromAllPlaylists(context, id);

                c.moveToNext();
            }

            // Step 2: Remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    selection.toString(), null);

            // Step 3: Remove files from card
            c.moveToFirst();
            while (!c.isAfterLast()) {
                final String name = c.getString(1);
                try { // File.delete can throw a security exception
                    final File f = new File(name);
                    if (!f.delete()) {
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
        }

        if (showNotification) {
            try {
                final String message = makeLabel(context, R.plurals.NNNtracksdeleted, list.length);
                AppMsg.makeText(context, message, AppMsg.STYLE_CONFIRM).show();
            } catch (Throwable e) {}
        }

        // We deleted a number of tracks, which could affect any number of
        // things
        // in the media content domain, so update everything.
        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
        // Notify the lists to update
        refresh();
    }

    public static void deleteTracks(final Context context, final long[] list) {
        deleteTracks(context, list, false);
    }

    public static void playAllFromUserItemClick(final Context context,
            final ArrayAdapter<Song> adapter, final int position) {
        if (adapter.getViewTypeCount() > 1 && position == 0) {
            return;
        }
        final long[] list = MusicUtils.getSongListForAdapter(adapter);
        int pos = adapter.getViewTypeCount() > 1 ? position - 1 : position;
        if (list.length == 0) {
            pos = 0;
        }
        MusicUtils.playAll(context, list, pos, false);
    }

    public static void removeSongFromAllPlaylists(final Context context, final long songId) {
        final List<Playlist> playlists = getPlaylists(context);

        if (!playlists.isEmpty()) {
            for (Playlist playlist : playlists) {
                removeFromPlaylist(context, songId, playlist.mPlaylistId);
            }
        }
    }

    private static final long[] getSongListForAdapter(ArrayAdapter<Song> adapter) {
        if (adapter == null) {
            return sEmptyList;
        }
        long[] list = {};
        if (adapter != null) {
            int count = adapter.getCount() - (adapter.getViewTypeCount() > 1 ? 1 : 0);
            list = new long[count];
            for (int i = 0; i < count; i++) {
                list[i] = ((Song) adapter.getItem(i)).mSongId;
            }
        }
        return list;
    }
}
