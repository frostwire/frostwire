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
import android.text.TextUtils;

import com.andrew.apollo.ui.activities.ProfileActivity;

/**
 * The {@link RecentStore} is used to display a a grid or list of
 * recently listened to albums. In order to populate the this grid or list with
 * the correct data, we keep a cache of the album ID, name, and time it was
 * played to be retrieved later.
 * <p>
 * In {@link ProfileActivity}, when viewing the profile for an artist, the first
 * image the carousel header is the last album the user listened to for that
 * particular artist. That album is retrieved using
 * {@link #getAlbumName(String)}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class RecentStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASE_NAME = "albumhistory.db";

    private static RecentStore sInstance = null;

    private final SQLiteDatabase writableDatabase;
    private final SQLiteDatabase readableDatabase;

    /**
     * Constructor of <code>RecentStore</code>
     *
     * @param context The {@link Context} to use
     */
    public RecentStore(final Context context) {
        super(context, DATABASE_NAME, null, VERSION);
        writableDatabase = getWritableDatabase();
        writableDatabase.enableWriteAheadLogging();
        readableDatabase = getReadableDatabase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + RecentStoreColumns.NAME + " ("
                + RecentStoreColumns.ID + " LONG NOT NULL," + RecentStoreColumns.ALBUMNAME
                + " TEXT NOT NULL," + RecentStoreColumns.ARTISTNAME + " TEXT NOT NULL,"
                + RecentStoreColumns.ALBUMSONGCOUNT + " TEXT NOT NULL,"
                + RecentStoreColumns.ALBUMYEAR + " TEXT," + RecentStoreColumns.TIMEPLAYED
                + " LONG NOT NULL);");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + RecentStoreColumns.NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static synchronized RecentStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new RecentStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to store artist IDs in the database.
     *
     * @param albumId    album's ID.
     * @param albumName  The album name.
     * @param artistName The artist album name.
     * @param songCount  The number of tracks for the album.
     * @param albumYear  The year the album was released.
     */
    public void addAlbumId(final Long albumId, final String albumName, final String artistName,
                           final String songCount, final String albumYear) {
        if (albumId == null || albumName == null || artistName == null || songCount == null) {
            return;
        }
        try {
            final ContentValues values = new ContentValues(6);
            values.put(RecentStoreColumns.ID, albumId);
            values.put(RecentStoreColumns.ALBUMNAME, albumName);
            values.put(RecentStoreColumns.ARTISTNAME, artistName);
            values.put(RecentStoreColumns.ALBUMSONGCOUNT, songCount);
            values.put(RecentStoreColumns.ALBUMYEAR, albumYear);
            values.put(RecentStoreColumns.TIMEPLAYED, System.currentTimeMillis());
            writableDatabase.beginTransaction();
            writableDatabase.delete(RecentStoreColumns.NAME,
                    RecentStoreColumns.ID + " = ?",
                    new String[]{
                            String.valueOf(albumId)
                    });
            writableDatabase.insert(RecentStoreColumns.NAME, null, values);
            writableDatabase.setTransactionSuccessful();
            writableDatabase.endTransaction();
        } catch (Throwable e) {
            // not critical at all
            e.printStackTrace();
        }
    }

    /**
     * Used to retrieve the most recently listened album for an artist.
     *
     * @param key The key to reference.
     * @return The most recently listened album for an artist.
     */
    public String getAlbumName(final String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        final String[] projection = new String[]{
                RecentStoreColumns.ID,
                RecentStoreColumns.ALBUMNAME,
                RecentStoreColumns.ARTISTNAME,
                RecentStoreColumns.TIMEPLAYED
        };
        final String selection = RecentStoreColumns.ARTISTNAME + "=?";
        final String[] having = new String[]{
                key
        };
        Cursor cursor = readableDatabase.query(RecentStoreColumns.NAME, projection, selection, having,
                null, null, RecentStoreColumns.TIMEPLAYED + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            final String album = cursor.getString(cursor
                    .getColumnIndexOrThrow(RecentStoreColumns.ALBUMNAME));
            cursor.close();
            return album;
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return null;
    }

    /**
     * Clear the cache.
     */
    public void deleteDatabase() {
        writableDatabase.delete(RecentStoreColumns.NAME, null, null);
    }

    /**
     * @param albumId The album Id to remove.
     */
    public void removeItem(final long albumId) {
        writableDatabase.delete(RecentStoreColumns.NAME, RecentStoreColumns.ID + " = ?",
                new String[]{String.valueOf(albumId)});
    }

    public interface RecentStoreColumns {
        /* Table name */
        String NAME = "albumhistory";

        /* Album IDs column */
        String ID = "albumid";

        /* Album name column */
        String ALBUMNAME = "itemname";

        /* Artist name column */
        String ARTISTNAME = "artistname";

        /* Album song count column */
        String ALBUMSONGCOUNT = "albumsongcount";

        /* Album year column. It's okay for this to be null */
        String ALBUMYEAR = "albumyear";

        /* Time played column */
        String TIMEPLAYED = "timeplayed";
    }
}
