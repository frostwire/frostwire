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
        String selection = MediaStore.Audio.Genres.Members.IS_MUSIC + "=1" +
                " AND " + MediaStore.Audio.Genres.Members.TITLE + "!=''";//$NON-NLS-2$
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
                }, selection, null, MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);
    }
}
