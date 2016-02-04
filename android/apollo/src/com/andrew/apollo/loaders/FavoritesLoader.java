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
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.FavoritesStore.FavoriteColumns;

/**
 * Used to query the {@link FavoritesStore} for the tracks marked as favorites.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
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
        return FavoritesStore
                .getInstance(context)
                .getReadableDatabase()
                .query(FavoriteColumns.NAME,
                        new String[] {
                                FavoriteColumns.ID + " as _id", FavoriteColumns.ID,
                                FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                                FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
                        }, null, null, null, null, FavoriteColumns.PLAYCOUNT + " DESC");
    }
}
