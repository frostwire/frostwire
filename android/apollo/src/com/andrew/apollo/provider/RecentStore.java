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
 * The {@link RecentlyListenedFragment} is used to display a a grid or list of
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
public class RecentStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASENAME = "albumhistory.db";

    private static RecentStore sInstance = null;

    /**
     * Constructor of <code>RecentStore</code>
     * 
     * @param context The {@link Context} to use
     */
    public RecentStore(final Context context) {
        super(context, DATABASENAME, null, VERSION);
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
    public static final synchronized RecentStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new RecentStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to store artist IDs in the database.
     * 
     * @param albumIDdThe album's ID.
     * @param albumName The album name.
     * @param artistName The artist album name.
     * @param songCount The number of tracks for the album.
     * @param albumYear The year the album was released.
     */
    public void addAlbumId(final Long albumId, final String albumName, final String artistName,
            final String songCount, final String albumYear) {
        if (albumId == null || albumName == null || artistName == null || songCount == null) {
            return;
        }

        try {
            final SQLiteDatabase database = getWritableDatabase();
            final ContentValues values = new ContentValues(6);

            database.beginTransaction();

            values.put(RecentStoreColumns.ID, albumId);
            values.put(RecentStoreColumns.ALBUMNAME, albumName);
            values.put(RecentStoreColumns.ARTISTNAME, artistName);
            values.put(RecentStoreColumns.ALBUMSONGCOUNT, songCount);
            values.put(RecentStoreColumns.ALBUMYEAR, albumYear);
            values.put(RecentStoreColumns.TIMEPLAYED, System.currentTimeMillis());

            database.delete(RecentStoreColumns.NAME, RecentStoreColumns.ID + " = ?", new String[]{
                    String.valueOf(albumId)
            });
            database.insert(RecentStoreColumns.NAME, null, values);
            database.setTransactionSuccessful();
            database.endTransaction();
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
        final SQLiteDatabase database = getReadableDatabase();
        final String[] projection = new String[] {
                RecentStoreColumns.ID, RecentStoreColumns.ALBUMNAME, RecentStoreColumns.ARTISTNAME,
                RecentStoreColumns.TIMEPLAYED
        };
        final String selection = RecentStoreColumns.ARTISTNAME + "=?";
        final String[] having = new String[] {
            key
        };
        Cursor cursor = database.query(RecentStoreColumns.NAME, projection, selection, having,
                null, null, RecentStoreColumns.TIMEPLAYED + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            final String album = cursor.getString(cursor
                    .getColumnIndexOrThrow(RecentStoreColumns.ALBUMNAME));
            cursor.close();
            cursor = null;
            return album;
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
            cursor = null;
        }

        return null;
    }

    /**
     * Clear the cache.
     */
    public void deleteDatabase() {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(RecentStoreColumns.NAME, null, null);
    }

    /**
     * @param item The album Id to remove.
     */
    public void removeItem(final long albumId) {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(RecentStoreColumns.NAME, RecentStoreColumns.ID + " = ?", new String[] {
            String.valueOf(albumId)
        });

    }

    public interface RecentStoreColumns {

        /* Table name */
        public static final String NAME = "albumhistory";

        /* Album IDs column */
        public static final String ID = "albumid";

        /* Album name column */
        public static final String ALBUMNAME = "itemname";

        /* Artist name column */
        public static final String ARTISTNAME = "artistname";

        /* Album song count column */
        public static final String ALBUMSONGCOUNT = "albumsongcount";

        /* Album year column. It's okay for this to be null */
        public static final String ALBUMYEAR = "albumyear";

        /* Time played column */
        public static final String TIMEPLAYED = "timeplayed";
    }
}
