package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio.AudioColumns;

import com.andrew.apollo.provider.RecentSongStore;
import com.andrew.apollo.provider.RecentSongStore.RecentStoreColumns;


public class RecentSongLoader extends SongLoader {

    /**
     * Constructor of <code>SongLoader</code>
     *
     * @param context The {@link Context} to use
     */
    public RecentSongLoader(final Context context) {
        super(context);
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeRecentCursor(getContext());
    }

    private static Cursor makeRecentCursor(final Context context) {
        return RecentSongStore
                .getInstance(context)
                .getReadableDatabase()
                .query(RecentStoreColumns.TABLE_NAME,
                        new String[] {
                                BaseColumns._ID + " as id",  /* 0 - id */
                                AudioColumns.TITLE,      /* 2 - songname */
                                AudioColumns.ARTIST,    /* 3 - artistname */
                                AudioColumns.ALBUM,     /* 4 - albumname */
                                AudioColumns.DURATION,       /* 5 - duration */
                        }, null, null, null, null,
                        RecentStoreColumns.TIME_PLAYED + " DESC");

    }
}
