/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa), José Molina (@votaguz)
 * Copyright (c) 2011-2018 FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio.AudioColumns;

import com.andrew.apollo.provider.RecentSongStore;
import com.andrew.apollo.provider.RecentSongStore.RecentStoreColumns;


public class RecentSongLoader extends SongLoader {
    /**
     * Constructor of <code>SongLoader</code>
     *
     * @param context The {@link Context} to use
     */
    public RecentSongLoader(final Context context) {
        super(context);
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeRecentCursor(getContext());
    }

    private static Cursor makeRecentCursor(final Context context) {
        return RecentSongStore
                .getInstance(context)
                .getReadableDatabase()
                .query(RecentStoreColumns.TABLE_NAME,
                        new String[] {
                                BaseColumns._ID + " as id",  /* 0 - id */
                                AudioColumns.TITLE,      /* 2 - songname */
                                AudioColumns.ARTIST,    /* 3 - artistname */
                                AudioColumns.ALBUM,     /* 4 - albumname */
                                AudioColumns.DURATION,       /* 5 - duration */
                        }, null, null, null, null,
                        RecentStoreColumns.TIME_PLAYED + " DESC");

    }
}
