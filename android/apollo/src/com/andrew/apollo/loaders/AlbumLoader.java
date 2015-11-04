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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.utils.Lists;
import com.andrew.apollo.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to query {@link MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI} and return
 * the albums on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumLoader extends WrappedAsyncTaskLoader<List<Album>> {

    /**
     * The result
     */
    private final ArrayList<Album> mAlbumsList = Lists.newArrayList();

    /**
     * The {@link Cursor} used to run the query.
     */
    private Cursor mCursor;

    /**
     * Constructor of <code>AlbumLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public AlbumLoader(final Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Album> loadInBackground() {
        // Create the Cursor
        try {
            mCursor = makeAlbumCursor(getContext());
        } catch (Throwable e) {
            return Collections.EMPTY_LIST;
        }

        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the album id
                final long id = mCursor.getLong(0);

                // Copy the album name
                final String albumName = mCursor.getString(1);

                // Copy the artist name
                final String artist = mCursor.getString(2);

                // Copy the number of songs
                final int songCount = mCursor.getInt(3);

                // Copy the release year
                final String year = mCursor.getString(4);

                // Create a new album
                final Album album = new Album(id, albumName, artist, songCount, year);

                // Add everything up
                mAlbumsList.add(album);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        return mAlbumsList;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the album query.
     */
    public static final Cursor makeAlbumCursor(final Context context) {
        return context.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        AlbumColumns.ALBUM,
                        /* 2 */
                        AlbumColumns.ARTIST,
                        /* 3 */
                        AlbumColumns.NUMBER_OF_SONGS,
                        /* 4 */
                        AlbumColumns.FIRST_YEAR
                }, null, null, PreferenceUtils.getInstance(context).getAlbumSortOrder());
    }
}
