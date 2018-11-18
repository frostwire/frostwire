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
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.PlaylistsColumns;
import com.andrew.apollo.model.Playlist;
import com.frostwire.android.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI and
 * return the playlists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistLoader extends WrappedAsyncTaskLoader<List<Playlist>> {
    public static final int FAVORITE_PLAYLIST_ID = -1;
    public static final int LAST_ADDED_PLAYLIST_ID = -2;
    public static final int NEW_PLAYLIST_ID = -3;

    /**
     * Constructor of <code>PlaylistLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public PlaylistLoader(final Context context) {
        super(context);
    }

    @Override
    public List<Playlist> loadInBackground() {
        // Add the default playlists to the adapter
        List<Playlist> mPlaylistList = makeDefaultPlaylists();
        // Create the Cursor
        Cursor mCursor = makeCursor(getContext());
        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the playlist id
                final long id = mCursor.getLong(0);
                // Copy the playlist name
                final String name = mCursor.getString(1);
                // Create a new playlist
                final Playlist playlist = new Playlist(id, name);
                // Add everything up
                mPlaylistList.add(playlist);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mPlaylistList;
    }

    /* Adds the favorites and last added playlists */
    private List<Playlist> makeDefaultPlaylists() {
        final Resources resources = getContext().getResources();
        ArrayList<Playlist> mPlaylistList = new ArrayList<>();

        /* New Empty list */
        final Playlist newPlaylist = new Playlist(NEW_PLAYLIST_ID,
                resources.getString(R.string.new_empty_playlist));
        mPlaylistList.add(newPlaylist);

        /* Favorites list */
        final Playlist favorites = new Playlist(FAVORITE_PLAYLIST_ID,
                resources.getString(R.string.playlist_favorites));
        mPlaylistList.add(favorites);

        /* Last added list */
        final Playlist lastAdded = new Playlist(LAST_ADDED_PLAYLIST_ID,
                resources.getString(R.string.playlist_last_added));
        mPlaylistList.add(lastAdded);
        return mPlaylistList;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the playlist query.
     */
    public static Cursor makePlaylistCursor(final Context context) {
        try {
            return context.getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    new String[]{
                            /* 0 */
                            BaseColumns._ID,
                            /* 1 */
                            PlaylistsColumns.NAME
                    }, null, null, MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER);
        } catch (android.database.sqlite.SQLiteException t) {
            return null;
        }
    }

    public Cursor makeCursor(Context context) {
        return makePlaylistCursor(context);
    }
}
