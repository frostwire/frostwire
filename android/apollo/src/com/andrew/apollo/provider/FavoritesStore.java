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
public class FavoritesStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASE_NAME = "favorites.db";

    private static FavoritesStore sInstance = null;

    private final SQLiteDatabase writeableDatabase;
    private final SQLiteDatabase readableDatabase;

    /**
     * Constructor of <code>FavoritesStore</code>
     *
     * @param context The {@link Context} to use
     */
    public FavoritesStore(final Context context) {
        super(context, DATABASE_NAME, null, VERSION);
        writeableDatabase = getWritableDatabase();
        writeableDatabase.enableWriteAheadLogging(); // parallel queries, writes don't block reads
        readableDatabase = getReadableDatabase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + FavoriteColumns.NAME + " ("
                + FavoriteColumns.ID + " LONG NOT NULL,"
                + FavoriteColumns.SONGNAME + " TEXT NOT NULL,"
                + FavoriteColumns.ALBUMNAME + " TEXT NOT NULL,"
                + FavoriteColumns.ARTISTNAME + " TEXT NOT NULL,"
                + FavoriteColumns.PLAYCOUNT + " LONG NOT NULL);");
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
    public static synchronized FavoritesStore getInstance(final Context context) {
        if (sInstance == null) {
            try {
                sInstance = new FavoritesStore(context.getApplicationContext());
            } catch (Throwable ignored) {
                sInstance = null;
            }
        }
        return sInstance;
    }

    /**
     * Used to store song Ids in our database
     *
     * @param songId     The album's ID
     * @param songName   The song name
     * @param albumName  The album name
     * @param artistName The artist name
     */
    public void addSongId(final Long songId, final String songName, final String albumName,
                          final String artistName) {
        if (songId == null || songId <= -1 || songName == null || albumName == null || artistName == null) {
            return;
        }
        final Long playCount = getPlayCount(songId);
        final ContentValues values = new ContentValues(5);
        values.put(FavoriteColumns.ID, songId);
        values.put(FavoriteColumns.SONGNAME, songName);
        values.put(FavoriteColumns.ALBUMNAME, albumName);
        values.put(FavoriteColumns.ARTISTNAME, artistName);
        values.put(FavoriteColumns.PLAYCOUNT, (playCount != null && playCount != 0) ? playCount + 1 : 1);
        writeableDatabase.beginTransaction();
        writeableDatabase.delete(FavoriteColumns.NAME,
                FavoriteColumns.ID + " = ?", new String[]{
                        String.valueOf(songId)
                });
        writeableDatabase.insert(FavoriteColumns.NAME, null, values);
        writeableDatabase.setTransactionSuccessful();
        writeableDatabase.endTransaction();
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
            final String[] projection = new String[]{
                    FavoriteColumns.ID, FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                    FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
            };
            final String selection = FavoriteColumns.ID + "=?";
            final String[] having = new String[]{
                    String.valueOf(songId)
            };
            Cursor cursor = readableDatabase.query(FavoriteColumns.NAME, projection, selection, having, null,
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final Long id = cursor.getLong(cursor.getColumnIndexOrThrow(FavoriteColumns.ID));
                cursor.close();
                return id;
            }
            if (cursor != null) {
                cursor.close();
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
        final String[] projection = new String[]{
                FavoriteColumns.ID, FavoriteColumns.SONGNAME, FavoriteColumns.ALBUMNAME,
                FavoriteColumns.ARTISTNAME, FavoriteColumns.PLAYCOUNT
        };
        final String selection = FavoriteColumns.ID + "=?";
        final String[] having = new String[]{
                String.valueOf(songId)
        };
        Cursor cursor = readableDatabase.query(FavoriteColumns.NAME, projection, selection, having, null,
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            final Long playCount = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FavoriteColumns.PLAYCOUNT));
            cursor.close();
            return playCount;
        }
        if (cursor != null) {
            cursor.close();
        }
        return (long) 0;
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
     * @param songId The song Id to remove
     */
    public void removeItem(final Long songId) {
        writeableDatabase.delete(FavoriteColumns.NAME, FavoriteColumns.ID + " = ?", new String[]{
                String.valueOf(songId)
        });

    }

    public interface FavoriteColumns {

        /* Table name */
        String NAME = "favorites";

        /* Song IDs column */
        String ID = "songid";

        /* Song name column */
        String SONGNAME = "songname";

        /* Album name column */
        String ALBUMNAME = "albumname";

        /* Artist name column */
        String ARTISTNAME = "artistname";

        /* Play count column */
        String PLAYCOUNT = "playcount";
    }

}
