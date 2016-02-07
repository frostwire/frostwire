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
import android.provider.MediaStore.Audio.AudioColumns;

import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.Lists;
import com.frostwire.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query {MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the Song the user added over the past four of weeks.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class LastAddedLoader extends SongLoader {
    private static Logger LOGGER = Logger.getLogger(LastAddedLoader.class);
    /**
     * Constructor of <code>LastAddedHandler</code>
     * 
     * @param context The {@link Context} to use.
     */
    public LastAddedLoader(final Context context) {
        super(context);
    }

    @Override
    public Cursor makeCursor(Context context) {
        LOGGER.info("makeCursor() start. (Overrides SongLoader's makeCursor())");
        Cursor c = makeLastAddedCursor(getContext());
        LOGGER.info("makCursor() finished.");
        return c;
    }

    /**
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the song query.
     */
    public static Cursor makeLastAddedCursor(final Context context) {
        LOGGER.info("makeLastAddedCursor() start.");
        final int fourWeeks = 4 * 3600 * 24 * 7;
        final StringBuilder selection = new StringBuilder();
        selection.append(AudioColumns.IS_MUSIC + "=1");
        selection.append(" AND " + AudioColumns.TITLE + " != ''"); //$NON-NLS-2$
        selection.append(" AND " + MediaStore.Audio.Media.DATE_ADDED + ">"); //$NON-NLS-2$
        selection.append(System.currentTimeMillis() / 1000 - fourWeeks);
        Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        AudioColumns.TITLE,
                        /* 2 */
                        AudioColumns.ARTIST,
                        /* 3 */
                        AudioColumns.ALBUM,
                        /* 4 */
                        AudioColumns.DURATION
                }, selection.toString(), null, MediaStore.Audio.Media.DATE_ADDED + " DESC");
        LOGGER.info("makeLastAddedCursor() finished with " + c.getCount() + " elements.");
        return c;
    }
}
