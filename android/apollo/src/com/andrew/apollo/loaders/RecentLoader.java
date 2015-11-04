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

import com.frostwire.android.R;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.provider.RecentStore.RecentStoreColumns;
import com.andrew.apollo.utils.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query {@link RecentStore} and return the last listened to albums.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RecentLoader extends WrappedAsyncTaskLoader<List<Album>> {

    /**
     * The result
     */
    private final ArrayList<Album> mAlbumsList = Lists.newArrayList();

    /**
     * The {@link Cursor} used to run the query.
     */
    private Cursor mCursor;

    /**
     * Constructor of <code>RecentLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public RecentLoader(final Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Album> loadInBackground() {
        // Create the Cursor
        mCursor = makeRecentCursor(getContext());
        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the album id
                final long id = mCursor.getLong(mCursor
                        .getColumnIndexOrThrow(RecentStoreColumns.ID));

                // Copy the album name
                final String albumName = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(RecentStoreColumns.ALBUMNAME));

                // Copy the artist name
                final String artist = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(RecentStoreColumns.ARTISTNAME));

                // Copy the number of songs
                final int songCount = mCursor.getInt(mCursor
                        .getColumnIndexOrThrow(RecentStoreColumns.ALBUMSONGCOUNT));

                // Copy the release year
                final String year = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(RecentStoreColumns.ALBUMYEAR));

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
    public static final Cursor makeRecentCursor(final Context context) {
        return RecentStore
                .getInstance(context)
                .getReadableDatabase()
                .query(RecentStoreColumns.NAME,
                        new String[] {
                                RecentStoreColumns.ID + " as id", RecentStoreColumns.ID,
                                RecentStoreColumns.ALBUMNAME, RecentStoreColumns.ARTISTNAME,
                                RecentStoreColumns.ALBUMSONGCOUNT, RecentStoreColumns.ALBUMYEAR,
                                RecentStoreColumns.TIMEPLAYED
                        }, null, null, null, null, RecentStoreColumns.TIMEPLAYED + " DESC");
    }
}
