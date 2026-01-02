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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI and return
 * the albums on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class AlbumLoader extends WrappedAsyncTaskLoader<List<Album>> {

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
        final ArrayList<Album> mAlbumsList = new ArrayList<>();

        // Create the Cursor
        Cursor mCursor;
        try {
            mCursor = makeCursor(getContext());
        } catch (Throwable e) {
            return Collections.emptyList();
        }

        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                mAlbumsList.add(getAlbumEntryFromCursor(mCursor));
                //String an = mCursor.getString(1) != null ? mCursor.getString(1) : "n/a";
                //String art = mCursor.getString(2) != null ? mCursor.getString(2) : "n/a";
                //LOGGER.info("Adding id: " + mCursor.getLong(0) + " - albumName: " + an + " - artist: " + art);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mAlbumsList;
    }

    protected Album getAlbumEntryFromCursor(Cursor cursor) {
        // Copy the album id
        final long id = cursor.getLong(0);
        // Copy the album name
        final String albumName = cursor.getString(1);
        // Copy the artist name
        final String artist = cursor.getString(2);
        // Copy the number of songs
        final int songCount = cursor.getInt(3);
        // Copy the release year
        final String year = cursor.getString(4);
        // Create a new album
        return new Album(id, albumName, artist, songCount, year);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the album query.
     */
    private Cursor makeAlbumCursor(final Context context) {
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
                }, null, null, PreferenceUtils.getInstance().getAlbumSortOrder());
    }

    public Cursor makeCursor(final Context context) {
        return makeAlbumCursor(context);
    }
}
