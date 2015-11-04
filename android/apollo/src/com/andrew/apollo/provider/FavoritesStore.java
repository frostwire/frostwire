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

package com.andrew.apollo.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * This class is used to to create the database used to make the Favorites
 * playlist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class FavoritesStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASENAME = "favorites.db";

    private static FavoritesStore sInstance = null;

    /**
     * Constructor of <code>FavoritesStore</code>
     * 
     * @param context The {@link Context} to use
     */
    public FavoritesStore(final Context context) {
        super(context, DATABASENAME, null, VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + FavoriteColumns.NAME + " (" + FavoriteColumns.ID
                + " LONG NOT NULL," + FavoriteColumns.SONGNAME + " TEXT NOT NULL,"
                + FavoriteColumns.ALBUMNAME + " TEXT NOT NULL," + FavoriteColumns.ARTISTNAME
                + " TEXT NOT NULL," + FavoriteColumns.PLAYCOUNT + " LONG NOT NULL);");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + FavoriteColumns.NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class
     */
    public static final synchronized FavoritesStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new FavoritesStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to store song Ids in our database
     * 
     * @param songId The album's ID
     * @param songName The song name
     * @param albumName The album name
     * @param artistName The artist name
     */
    public void addSongId(final Long songId, final String songName, final String albumName,
            final String artistName) {
        if (songId == null || songName == null || albumName == null || artistName == null) {
            return;
        }

        final Long playCount = getPlayCount(songId);
        final SQLiteDatabase database = getWritableDatabase();
        final ContentValues values = new ContentValues(5);

        database.beginTransaction();

        values.put(FavoriteColumns.ID, songId);
        values.put(FavoriteColumns.SONGNAME, songName);
        values.put(FavoriteColumns.ALBUMNAME, albumName);
        values.put(FavoriteColumns.ARTISTNAME, artistName);
        values.put(FavoriteColumns.PLAYCOUNT, playCount != 0 ? playCount + 1 : 1);

        database.delete(FavoriteColumns.NAME, FavoriteColumns.ID + " = ?", new String[] {
            String.valueOf(songId)
        });
        database.insert(FavoriteColumns.NAME, null, values);
        database.setTransactionSuccessful();
        database.endTransaction();

    }

    /**
     * Used to retrieve a single song Id from our database
     * 
     * @param songId The song Id to reference
     * @return The song Id
     */
    public Long getSongId(final Long songId) {
        if (songId <= -1) {
            return null;
        }

        try {
            final SQLiteDatabase database = getReadableDatabase();
            final String[] projection = new String[]{
                    FavoriteColumns.ID, FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                    FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
            };
            final String selection = FavoriteColumns.ID + "=?";
            final String[] having = new String[]{
                    String.valueOf(songId)
            };
            Cursor cursor = database.query(FavoriteColumns.NAME, projection, selection, having, null,
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final Long id = cursor.getLong(cursor.getColumnIndexOrThrow(FavoriteColumns.ID));
                cursor.close();
                cursor = null;
                return id;
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        } catch (Throwable e) {
            return null;
        }
        return null;
    }

    /**
     * Used to retrieve the play count
     * 
     * @param songId The song Id to reference
     * @return The play count for a song
     */
    public Long getPlayCount(final Long songId) {
        if (songId <= -1) {
            return null;
        }

        final SQLiteDatabase database = getReadableDatabase();
        final String[] projection = new String[] {
                FavoriteColumns.ID, FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
        };
        final String selection = FavoriteColumns.ID + "=?";
        final String[] having = new String[] {
            String.valueOf(songId)
        };
        Cursor cursor = database.query(FavoriteColumns.NAME, projection, selection, having, null,
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            final Long playCount = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FavoriteColumns.PLAYCOUNT));
            cursor.close();
            cursor = null;
            return playCount;
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }

        return (long)0;
    }

    /**
     * Clear the cache.
     * 
     * @param context The {@link Context} to use.
     */
    public static void deleteDatabase(final Context context) {
        context.deleteDatabase(DATABASENAME);
    }

    /**
     * Toggle the current song as favorite
     */
    public void toggleSong(final Long songId, final String songName, final String albumName,
            final String artistName) {
        if (getSongId(songId) == null) {
            addSongId(songId, songName, albumName, artistName);
        } else {
            removeItem(songId);
        }

    }

    /**
     * @param item The song Id to remove
     */
    public void removeItem(final Long songId) {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(FavoriteColumns.NAME, FavoriteColumns.ID + " = ?", new String[] {
            String.valueOf(songId)
        });

    }

    public interface FavoriteColumns {

        /* Table name */
        public static final String NAME = "favorites";

        /* Song IDs column */
        public static final String ID = "songid";

        /* Song name column */
        public static final String SONGNAME = "songname";

        /* Album name column */
        public static final String ALBUMNAME = "albumname";

        /* Artist name column */
        public static final String ARTISTNAME = "artistname";

        /* Play count column */
        public static final String PLAYCOUNT = "playcount";
    }

}
