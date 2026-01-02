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

package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import com.andrew.apollo.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI and
 * return the songs for a particular playlist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class PlaylistSongLoader extends SongLoader {
    /**
     * The Id of the playlist the songs belong to.
     */
    private final Long mPlaylistID;

    /**
     * Constructor of <code>SongLoader</code>
     * 
     * @param context The {@link Context} to use
     * @param playlistId The Id of the playlist the songs belong to.
     */
    public PlaylistSongLoader(final Context context, final Long playlistId) {
        super(context);
        mPlaylistID = playlistId;
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makePlaylistSongCursor(getContext(), mPlaylistID);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @param playlistID The playlist the songs belong to.
     * @return The {@link Cursor} used to run the song query.
     */
    private static Cursor makePlaylistSongCursor(final Context context, final Long playlistID) {
        String mSelection = AudioColumns.IS_MUSIC + "=1" +
                " AND " + AudioColumns.TITLE + " != ''";//$NON-NLS-2$
        return context.getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistID),
                new String[] {
                        /* 0 */
                        MediaStore.Audio.Playlists.Members._ID,
                        /* 1 */
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        /* 2 */
                        AudioColumns.TITLE,
                        /* 3 */
                        AudioColumns.ARTIST,
                        /* 4 */
                        AudioColumns.ALBUM,
                        /* 5 */
                        AudioColumns.DURATION
                }, mSelection, null,
                MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
    }

    @Override
    public List<Song> loadInBackground() {
        final ArrayList<Song> mSongList = new ArrayList<>();
        Cursor mCursor = makePlaylistSongCursor(getContext(), mPlaylistID);
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the song Id
                final long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID));
                // Copy the song name
                final String songName = mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
                // Copy the artist name
                final String artist = mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
                // Copy the album name
                final String album = mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM));
                // Copy the duration
                final long duration = mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.DURATION));
                // Convert the duration into seconds
                final int durationInSecs = (int) duration / 1000;
                // Create a new song
                final Song song = new Song(id, songName, artist, album, durationInSecs);
                // Add everything up
                mSongList.add(song);
            } while (mCursor.moveToNext());
        }
        if (mCursor != null) {
            mCursor.close();
        }
        return mSongList;
    }
}
