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
    public static Cursor makePlaylistSongCursor(final Context context, final Long playlistID) {
        final StringBuilder mSelection = new StringBuilder();
        mSelection.append(AudioColumns.IS_MUSIC + "=1");
        mSelection.append(" AND " + AudioColumns.TITLE + " != ''"); //$NON-NLS-2$
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
                }, mSelection.toString(), null,
                MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
    }
}
