/*
 * Copyright (C) 2012 Andrew Neal
 *
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2013-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0
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
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.text.TextUtils;

import com.andrew.apollo.ui.activities.ProfileActivity;

/**
 * The {@link RecentStore} is used to display a list of
 * recently listened songs. In order to populate this list with
 * the correct data, we keep a cache of the song ID, title, artist, album, duration and time it was
 * played for last time to be retrieved later.
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
    public static final String DATABASE_NAME = "songhistory.db";

    /* Table name */
    public static final String TABLE_NAME = "songhistory";
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
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_NAME + " ("
                        + BaseColumns._ID + " LONG NOT NULL,"
                        + AudioColumns.TITLE + " TEXT NOT NULL,"
                        + AudioColumns.ARTIST + " TEXT NOT NULL,"
                        + AudioColumns.ALBUM + " TEXT,"
                        + AudioColumns.DURATION + " LONG NOT NULL,"
                        + RecentStoreColumns.LAST_TIME_PLAYED + " LONG NOT NULL);"
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static synchronized RecentStore getInstance(final Context context) {
        if (sInstance == null) {
            try {
                sInstance = new RecentStore(context.getApplicationContext());
            } catch (Throwable ignored) {
                sInstance = null;
            }
        }
        return sInstance;
    }

    /**
     * Used to store artist IDs in the database.
     *
     * @param songId     Song's ID.
     * @param songName   The song name
     * @param artistName The artist song name.
     * @param albumName  The album name.
     * @param duration   The song total time playback.
     * @param unknownString A default placeholder if some information isn't provided.
     */
    public void addSongId(final Long songId, final String songName,
                          final String artistName, final String albumName,
                          final long duration, String unknownString) {

        if (songId == null || duration <= 0) {
            return ;
        }

        try {
            final ContentValues values = new ContentValues(6);
            values.put(BaseColumns._ID, songId);
            values.put(AudioColumns.TITLE, songName != null ? songName : unknownString);
            values.put(AudioColumns.ARTIST, artistName != null ? artistName: unknownString);
            values.put(AudioColumns.ALBUM, albumName != null ? albumName: unknownString);
            values.put(AudioColumns.DURATION, duration);
            values.put(RecentStoreColumns.LAST_TIME_PLAYED, System.currentTimeMillis());

            writableDatabase.beginTransaction();
            writableDatabase.delete(TABLE_NAME,
                    BaseColumns._ID + " = ?", new String[]{ String.valueOf(songId) });
            writableDatabase.insert(TABLE_NAME, null, values);
            writableDatabase.setTransactionSuccessful();
            writableDatabase.endTransaction();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Used to retrieve the most recently listened song for an artist.
     *
     * @param key The key to reference.
     * @return The most recently listened song name for an artist.
     */
    public String getSongName(final String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        final String[] projection = new String[]{
                BaseColumns._ID,
                AudioColumns.TITLE,
                AudioColumns.ARTIST,
                RecentStoreColumns.LAST_TIME_PLAYED
        };
        final String selection = AudioColumns.ARTIST + "=?";
        final String[] having = new String[]{ key };
        Cursor cursor = readableDatabase.query(TABLE_NAME, projection,
                selection, having,null, null, RecentStoreColumns.LAST_TIME_PLAYED +
                        " DESC", null);

        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            final String song = cursor.getString(
                    cursor.getColumnIndexOrThrow(AudioColumns.TITLE));
            cursor.close();
            return song;
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return null;
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
                BaseColumns._ID,
                AudioColumns.ALBUM,
                AudioColumns.ARTIST,
                RecentStoreColumns.LAST_TIME_PLAYED
        };
        final String selection = AudioColumns.ARTIST + "=?";
        final String[] having = new String[]{
                key
        };
        Cursor cursor = readableDatabase.query(TABLE_NAME, projection, selection, having,
                null, null, RecentStoreColumns.LAST_TIME_PLAYED + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            final String album = cursor.getString(cursor
                    .getColumnIndexOrThrow(AudioColumns.ALBUM));
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
        writableDatabase.delete(TABLE_NAME, null, null);
    }

    /**
     * @param albumId The album Id to remove.
     */
    public void removeItem(final long albumId) {
        writableDatabase.delete(TABLE_NAME, BaseColumns._ID + " = ?",
                new String[]{String.valueOf(albumId)});
    }

    public interface RecentStoreColumns {
        /* Time played column */
        String LAST_TIME_PLAYED = "lasttimeplayed";
    }
}
