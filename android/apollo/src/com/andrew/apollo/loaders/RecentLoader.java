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
import android.provider.MediaStore.Audio.AudioColumns;

import com.andrew.apollo.model.Album;
import com.andrew.apollo.provider.RecentStore;

/**
 * Used to query {@link RecentStore} and return the last listened to albums.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class RecentLoader extends SongLoader {

    /**
     * Constructor of <code>RecentLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public RecentLoader(final Context context) {
        super(context);
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeRecentCursor(getContext());
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the album query.
     */
    private static Cursor makeRecentCursor(final Context context) {
        return RecentStore
                .getInstance(context)
                .getReadableDatabase()
                .query(RecentStore.RecentStoreColumns.TABLE_NAME,
                        new String[] {
                                BaseColumns._ID + " as id",  /* 0 - id */
                                AudioColumns.TITLE,          /* 2 - songname */
                                AudioColumns.ARTIST,         /* 3 - artistname */
                                AudioColumns.ALBUM,          /* 4 - albumname */
                                AudioColumns.DURATION,       /* 5 - duration */
                        }, null, null, null, null,
                        RecentStore.RecentStoreColumns.TIME_PLAYED + " DESC");

    }

    // TODO: REMOVE THIS THING WHEN you're sure that isn't used
    protected Album getAlbumEntryFromCursor(Cursor cursor) {
        // Copy the album id
        final long id = cursor.getLong(0);
        // Copy the album name
        final String albumName = cursor.getString(2);
        // Copy the artist name
        final String artist = cursor.getString(3);
        // Copy the number of songs
        final int songCount = cursor.getInt(4);
        // Copy the release year
        final String year = cursor.getString(5);
        // Create a new album
        return new Album(id, albumName, artist, songCount, year);
    }
}
