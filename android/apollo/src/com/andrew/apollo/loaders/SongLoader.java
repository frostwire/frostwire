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
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Media.EXTERNAL_CONTENT_URI and return
 * the songs on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SongLoader extends WrappedAsyncTaskLoader<List<Song>> {
    private static Logger LOGGER = Logger.getLogger(SongLoader.class);
    /**
     * Constructor of <code>SongLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public SongLoader(final Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Song> loadInBackground() {
        ArrayList<Song> mSongList = Lists.newArrayList();
        // Create the Cursor
        Cursor mCursor;
        try {
            mCursor = makeCursor(getContext());
        } catch (Throwable ignored) {
            LOGGER.error("SongLoader.loadInBackground(): " + ignored.getMessage(), ignored);
            return Collections.EMPTY_LIST;
        }

        if (mCursor == null) {
            //LOGGER.warn("loadInBackground() - cursor == null, returning empty list.");
            return Collections.EMPTY_LIST;
        }

        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the song Id
                final long id = mCursor.getLong(0);

                // Copy the song name
                final String songName = mCursor.getString(1);

                // Copy the artist name
                final String artist = mCursor.getString(2);

                // Copy the album name
                final String album = mCursor.getString(3);

                // Copy the duration (Not available for all song Cursors, like on FavoritesLoader's)
                long duration = -1;
                int durationInSecs = -1;
                try {
                    duration = mCursor.getLong(4);
                    durationInSecs = (int) duration / 1000;
                } catch (Throwable ignored) {
                    LOGGER.error("SongLoader.loadInBackground(): " +ignored.getMessage(), ignored);
                }

                // Create a new song
                final Song song = new Song(id, songName, artist, album, durationInSecs);

                // Add everything up
                mSongList.add(song);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        //LOGGER.info("loadInBackground() done (" + mSongList.size() + " songs)");
        return mSongList;
    }

    public Cursor makeCursor(final Context context) {
        return makeSongCursor(context);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the song query.
     */
    private static Cursor makeSongCursor(final Context context) {
        final StringBuilder mSelection = new StringBuilder();
        mSelection.append(AudioColumns.IS_MUSIC + "=1");
        mSelection.append(" AND " + AudioColumns.TITLE + " != ''"); //$NON-NLS-2$
        return context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
                }, mSelection.toString(), null,
                PreferenceUtils.getInstance(context).getSongSortOrder());
    }
}
