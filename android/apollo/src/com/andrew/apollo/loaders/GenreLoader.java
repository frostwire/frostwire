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
import android.provider.MediaStore.Audio.GenresColumns;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.utils.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI and return
 * the genres on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class GenreLoader extends WrappedAsyncTaskLoader<List<Genre>> {

    /**
     * Constructor of <code>GenreLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public GenreLoader(final Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Genre> loadInBackground() {
        ArrayList<Genre> mGenreList = Lists.newArrayList();

        // Create the Cursor
        Cursor mCursor = makeGenreCursor(getContext());
        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the genre id
                final long id = mCursor.getLong(0);

                // Copy the genre name
                final String name = mCursor.getString(1);

                // Create a new genre
                final Genre genre = new Genre(id, name);

                // Add everything up
                mGenreList.add(genre);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mGenreList;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the genre query.
     */
    public static Cursor makeGenreCursor(final Context context) {
        return context.getContentResolver().query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[] {
                        /* 0 */
                        BaseColumns._ID,
                        /* 1 */
                        GenresColumns.NAME
                }, (MediaStore.Audio.Genres.NAME + " != ''"), null, MediaStore.Audio.Genres.DEFAULT_SORT_ORDER);
    }
}
