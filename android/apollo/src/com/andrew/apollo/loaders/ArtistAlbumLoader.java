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
