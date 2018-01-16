package com.andrew.apollo.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

/**
 * Created by votaguz on 1/15/18.
 */

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
                + RecentStoreColumns.ID + " LONG NOT NULL,"
                + RecentStoreColumns.SONG_NAME + " TEXT NOT NULL,"
                + RecentStoreColumns.ARTIST_NAME + " TEXT NOT NULL,"
                + RecentStoreColumns.ALBUM_NAME + " TEXT,"
                + RecentStoreColumns.DURATION + " TEXT NOT NULL,"
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
                          final int duration) {

        if (songId == null || songName == null || artistName == null || albumName == null ||
                duration == 0) {
            return;
        }

        try {
            final ContentValues values = new ContentValues(6);
            values.put(RecentStoreColumns.ID, songId);
            values.put(RecentStoreColumns.SONG_NAME, songName);
            values.put(RecentStoreColumns.ARTIST_NAME, artistName);
            values.put(RecentStoreColumns.ALBUM_NAME, albumName);
            values.put(RecentStoreColumns.DURATION, duration);
            values.put(RecentStoreColumns.TIME_PLAYED, System.currentTimeMillis());

            writableDatabase.beginTransaction();
            writableDatabase.delete(RecentStoreColumns.TABLE_NAME,
                RecentStoreColumns.ID + " = ?", new String[]{ String.valueOf(songId) });
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
                RecentStoreColumns.ID,
                RecentStoreColumns.SONG_NAME,
                RecentStoreColumns.ARTIST_NAME,
                RecentStoreColumns.TIME_PLAYED
        };
        final String selection = RecentStoreColumns.ARTIST_NAME + "=?";
        final String[] having = new String[]{ key };
        Cursor cursor = readableDatabase.query(RecentStoreColumns.TABLE_NAME, projection,
                selection, having,null, null, RecentStoreColumns.TIME_PLAYED +
                " DESC", null);

        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            final String song = cursor.getString(
                    cursor.getColumnIndexOrThrow(RecentStoreColumns.SONG_NAME));
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
                RecentStoreColumns.ID + " = ?",
                new String[]{String.valueOf(songId)});
    }

    public interface RecentStoreColumns {
        /* Table name */
        String TABLE_NAME = "songhistory";

        /* Song IDs column */
        String ID = "id";

        /* Song name column */
        String SONG_NAME = "name";

        /* Song Artist name column */
        String ARTIST_NAME = "artistname";

        /* Song Album name column */
        String ALBUM_NAME = "albumname";

        /* Song duration column */
        String DURATION = "duration";

        /* Time played column */
        String TIME_PLAYED = "timeplayed";
    }
}
