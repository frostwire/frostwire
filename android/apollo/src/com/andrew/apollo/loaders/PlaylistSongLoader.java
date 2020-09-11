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
