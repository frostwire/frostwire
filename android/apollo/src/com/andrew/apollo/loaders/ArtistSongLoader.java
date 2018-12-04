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
        final StringBuilder selection = new StringBuilder();
        selection.append(AudioColumns.IS_MUSIC + "=1");
        selection.append(" AND " + AudioColumns.TITLE + " != ''");
        selection.append(" AND " + AudioColumns.ARTIST_ID + "=").append(artistId);
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
                }, selection.toString(), null,
                PreferenceUtils.getInstance().getArtistSongSortOrder());
    }
}
