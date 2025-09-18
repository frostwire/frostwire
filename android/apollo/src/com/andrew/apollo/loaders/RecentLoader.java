/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio.AudioColumns;

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
        if (context == null) {
            return null;
        }

        RecentStore recentStore = RecentStore.getInstance(context);

        if (recentStore == null) {
            return null;
        }

        String durationColumn = "duration";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            durationColumn = AudioColumns.DURATION;
        }
        return recentStore
                .getReadableDatabase()
                .query(RecentStore.TABLE_NAME,
                        new String[]{
                                BaseColumns._ID + " as id",  /* 0 - id */
                                AudioColumns.TITLE,          /* 2 - songname */
                                AudioColumns.ARTIST,         /* 3 - artistname */
                                AudioColumns.ALBUM,          /* 4 - albumname */
                                durationColumn,              /* 5 - duration */
                                /* Can't add AudioColumns.ALBUM_ID since RecentStore is a sqlitedb created by Apollo and doesn't seem to need it */
                        }, null, null, null, null,
                        RecentStore.RecentStoreColumns.LAST_TIME_PLAYED + " DESC");

    }
}
