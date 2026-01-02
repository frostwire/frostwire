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
import android.provider.MediaStore.Audio.ArtistColumns;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI and
 * return the artists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class ArtistLoader extends WrappedAsyncTaskLoader<List<Artist>> {
    /**
     * Constructor of <code>ArtistLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public ArtistLoader(final Context context) {
        super(context);
    }

    public Cursor makeCursor(Context context) {
        return makeArtistCursor(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Artist> loadInBackground() {
        ArrayList<Artist> mArtistsList = new ArrayList<>();
        // Create the Cursor
        Cursor mCursor = makeCursor(getContext());
        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the artist id
                final long id = mCursor.getLong(0);

                // Copy the artist name
                final String artistName = mCursor.getString(1);

                // Copy the number of albums
                final int albumCount = mCursor.getInt(2);

                // Copy the number of songs
                final int songCount = mCursor.getInt(3);

                // Create a new artist
                final Artist artist = new Artist(id, artistName, songCount, albumCount);

                // Add everything up
                mArtistsList.add(artist);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mArtistsList;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the artist query.
     */
    private static Cursor makeArtistCursor(final Context context) {
        try {
            return context.getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    new String[]{
                        /* 0 */
                            BaseColumns._ID,
                        /* 1 */
                            ArtistColumns.ARTIST,
                        /* 2 */
                            ArtistColumns.NUMBER_OF_ALBUMS,
                        /* 3 */
                            ArtistColumns.NUMBER_OF_TRACKS
                    }, null, null, PreferenceUtils.getInstance().getArtistSortOrder());
        } catch (Throwable ignored) {
            // can throw SecurityException which then ends up in RuntimeException crash
            return null;
        }
    }
}
