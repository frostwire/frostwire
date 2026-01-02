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

import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.FavoritesStore.FavoriteColumns;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query the {@link FavoritesStore} for the tracks marked as favorites.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public class FavoritesLoader extends SongLoader {
    /**
     * Constructor of <code>FavoritesHandler</code>
     *
     * @param context The {@link Context} to use.
     */
    public FavoritesLoader(final Context context) {
        super(context);
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeFavoritesCursor(context);
    }

    /**
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the favorites query.
     */
    public static Cursor makeFavoritesCursor(final Context context) {
        if (context == null) {
            return null;
        }

        FavoritesStore favoritesStore = FavoritesStore.getInstance(context);
        if (favoritesStore == null) {
            return null;
        }

        return favoritesStore
                .getReadableDatabase()
                .query(FavoriteColumns.NAME,
                        new String[]{
                                FavoriteColumns.ID + " as _id", FavoriteColumns.ID,
                                FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                                FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
                        }, null, null, null, null, FavoriteColumns.PLAYCOUNT + " DESC");
    }

    @Override
    public List<Song> loadInBackground() {
        Cursor mCursor = makeFavoritesCursor(getContext());
        ArrayList<Song> mSongList = new ArrayList<>();
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the song Id
                final long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(FavoriteColumns.ID));
                // Copy the song name
                final String songName = mCursor.getString(mCursor.getColumnIndexOrThrow(FavoriteColumns.SONGNAME));
                // Copy the artist name
                final String artist = mCursor.getString(mCursor.getColumnIndexOrThrow(FavoriteColumns.ARTISTNAME));
                // Copy the album name
                final String album = mCursor.getString(mCursor.getColumnIndexOrThrow(FavoriteColumns.ALBUMNAME));
                // Create a new song
                final Song song = new Song(id, songName, artist, album, -1);
                // Add everything up
                mSongList.add(song);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mSongList;
    }
}
