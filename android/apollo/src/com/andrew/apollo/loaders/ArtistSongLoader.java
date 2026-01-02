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
import android.provider.MediaStore.Audio.AudioColumns;
import com.andrew.apollo.utils.PreferenceUtils;

/**
 * Used to query {MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the songs for a particular artist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class ArtistSongLoader extends SongLoader {
    /**
     * The Id of the artist the songs belong to.
     */
    private final Long mArtistID;

    /**
     * Constructor of <code>ArtistSongLoader</code>
     * 
     * @param context The {@link Context} to use.
     * @param artistId The Id of the artist the songs belong to.
     */
    public ArtistSongLoader(final Context context, final Long artistId) {
        super(context);
        mArtistID = artistId;
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeArtistSongCursor(getContext(), mArtistID);
    }

    /**
     * @param context The {@link Context} to use.
     * @param artistId The Id of the artist the songs belong to.
     * @return The {@link Cursor} used to run the query.
     */
    private static Cursor makeArtistSongCursor(final Context context, final Long artistId) {
        // Match the songs up with the artist
        String selection = AudioColumns.IS_MUSIC + "=1" +
                " AND " + AudioColumns.TITLE + " != ''" +
                " AND " + AudioColumns.ARTIST_ID + "=" + artistId;
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
                }, selection, null,
                PreferenceUtils.getInstance().getArtistSongSortOrder());
    }
}
