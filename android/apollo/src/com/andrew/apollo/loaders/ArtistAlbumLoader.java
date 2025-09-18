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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import com.andrew.apollo.utils.PreferenceUtils;

/**
 * Used to query {@link MediaStore.Audio.Artists.Albums} and return the albums
 * for a particular artist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class ArtistAlbumLoader extends AlbumLoader {

    /**
     * The Id of the artist the albums belong to.
     */
    private final Long mArtistID;

    /**
     * Constructor of <code>ArtistAlbumHandler</code>
     * 
     * @param context The {@link Context} to use.
     * @param artistId The Id of the artist the albums belong to.
     */
    public ArtistAlbumLoader(final Context context, final Long artistId) {
        super(context);
        mArtistID = artistId;
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeArtistAlbumCursor(context, mArtistID);
    }

    /**
     * @param context The {@link Context} to use.
     * @param artistId The Id of the artist the albums belong to.
     */
    private static Cursor makeArtistAlbumCursor(final Context context, final Long artistId) {
        if (artistId == -1) {
            // fix an error reported in Play console
            return null;
        }

        try {
            return context.getContentResolver().query(
                    MediaStore.Audio.Artists.Albums.getContentUri("external", artistId), new String[]{
                        /* 0 */
                            BaseColumns._ID,
                        /* 1 */
                            AlbumColumns.ALBUM,
                        /* 2 */
                            AlbumColumns.ARTIST,
                        /* 3 */
                            AlbumColumns.NUMBER_OF_SONGS,
                        /* 4 */
                            AlbumColumns.FIRST_YEAR
                    }, null, null, PreferenceUtils.getInstance().getArtistAlbumSortOrder());
        } catch (Throwable e) {
            // ignore any error since it's not critical
            return null;
        }
    }
}
