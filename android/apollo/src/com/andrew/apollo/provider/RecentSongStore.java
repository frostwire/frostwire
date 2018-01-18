/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa), José Molina (@votaguz)
 * Copyright (c) 2011-2018 FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

public final class RecentSongStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASE_NAME = "songhistory.db";
    private static RecentSongStore sInstance = null;
    private final SQLiteDatabase writableDatabase;
    private final SQLiteDatabase readableDatabase;

    /**
     * Constructor of <code>RecentStore</code>
     *
     * @param context The {@link Context} to use
     */
    public RecentSongStore(final Context context) {
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
                + RecentStoreColumns.TABLE_NAME + " ("
                + BaseColumns._ID + " LONG NOT NULL,"
                + AudioColumns.TITLE + " TEXT NOT NULL,"
                + AudioColumns.ARTIST + " TEXT NOT NULL,"
                + AudioColumns.ALBUM + " TEXT,"
                + AudioColumns.DURATION + " LONG NOT NULL,"
                + RecentStoreColumns.TIME_PLAYED + " LONG NOT NULL);"
        );
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + RecentStoreColumns.TABLE_NAME);
        onCreate(db);
    }

    public static synchronized RecentSongStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new RecentSongStore(context.getApplicationContext());
        }
        return sInstance;
    }

    public void addSongId(final Long songId, final String songName,
                          final String artistName, final String albumName,
                          final long duration, String unknownString) {

        if (songId == null || duration == 0) {
            return ;
        }

        try {
            final ContentValues values = new ContentValues(6);
            values.put(BaseColumns._ID, songId);
            values.put(AudioColumns.TITLE, songName != null ? songName : unknownString);
            values.put(AudioColumns.ARTIST, artistName != null ? artistName: unknownString);
            values.put(AudioColumns.ALBUM, albumName != null ? albumName: unknownString);
            values.put(AudioColumns.DURATION, duration);
            values.put(RecentStoreColumns.TIME_PLAYED, System.currentTimeMillis());

            writableDatabase.beginTransaction();
            writableDatabase.delete(RecentStoreColumns.TABLE_NAME,
                BaseColumns._ID + " = ?", new String[]{ String.valueOf(songId) });
            writableDatabase.insert(RecentStoreColumns.TABLE_NAME, null, values);
            writableDatabase.setTransactionSuccessful();
            writableDatabase.endTransaction();
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public String getSongName(final String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }

        final String[] projection = new String[]{
                BaseColumns._ID,
                AudioColumns.TITLE,
                AudioColumns.ARTIST,
                RecentStoreColumns.TIME_PLAYED
        };
        final String selection = AudioColumns.ARTIST + "=?";
        final String[] having = new String[]{ key };
        Cursor cursor = readableDatabase.query(RecentStoreColumns.TABLE_NAME, projection,
                selection, having,null, null, RecentStoreColumns.TIME_PLAYED +
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

    public void deleteDatabase() {
        writableDatabase.delete(RecentStoreColumns.TABLE_NAME, null, null);
    }

    public void removeItem(final long songId) {
        writableDatabase.delete(RecentStoreColumns.TABLE_NAME,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(songId)});
    }

    public interface RecentStoreColumns {
        /* Table name */
        String TABLE_NAME = "songhistory";

        /* Time played column */
        String TIME_PLAYED = "timeplayed";
    }
}
