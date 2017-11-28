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
import android.provider.MediaStore;

/**
 * Used to query MediaStore.Audio.Genres.Members.EXTERNAL_CONTENT_URI
 * and return the songs for a particular genre.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class GenreSongLoader extends SongLoader {
    /**
     * The Id of the genre the songs belong to.
     */
    private final Long mGenreID;

    /**
     * Constructor of <code>GenreSongHandler</code>
     * 
     * @param context The {@link Context} to use.
     * @param genreId The Id of the genre the songs belong to.
     */
    public GenreSongLoader(final Context context, final Long genreId) {
        super(context);
        mGenreID = genreId;
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeGenreSongCursor(getContext(), mGenreID);
    }

    /**
     * @param context The {@link Context} to use.
     * @param genreId The Id of the genre the songs belong to.
     * @return The {@link Cursor} used to run the query.
     */
    private static Cursor makeGenreSongCursor(final Context context, final Long genreId) {
        // Match the songs up with the genre
        final StringBuilder selection = new StringBuilder();
        selection.append(MediaStore.Audio.Genres.Members.IS_MUSIC + "=1");
        selection.append(" AND " + MediaStore.Audio.Genres.Members.TITLE + "!=''"); //$NON-NLS-2$
        return context.getContentResolver().query(
                MediaStore.Audio.Genres.Members.getContentUri("external", genreId), new String[] {
                        /* 0 */
                        MediaStore.Audio.Genres.Members._ID,
                        /* 1 */
                        MediaStore.Audio.Genres.Members.TITLE,
                        /* 2 */
                        MediaStore.Audio.Genres.Members.ALBUM,
                        /* 3 */
                        MediaStore.Audio.Genres.Members.ARTIST,
                        /* 4 */
                        MediaStore.Audio.Genres.Members.DURATION
                }, selection.toString(), null, MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);
    }
}
