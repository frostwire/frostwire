package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;

import com.andrew.apollo.model.Song;
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
                                RecentStoreColumns.ID + " as id",  /* 0 - id */
                                RecentStoreColumns.ID,             /* 1 - songid */
                                RecentStoreColumns.SONG_NAME,      /* 2 - songname */
                                RecentStoreColumns.ARTIST_NAME,    /* 3 - artistname */
                                RecentStoreColumns.ALBUM_NAME,     /* 4 - albumname */
                                RecentStoreColumns.DURATION,       /* 5 - duration */
                                RecentStoreColumns.TIME_PLAYED     /* 6 - timeplayed */
                        }, null, null, null, null,
                        RecentStoreColumns.TIME_PLAYED + " DESC");

    }

    protected Song getSongEntryFromCursor(Cursor cursor) {
        final long songId = cursor.getLong(0);
        final String songName = cursor.getString(2);
        final String artistName = cursor.getString(3);
        final String albumName = cursor.getString(4);
        final int duration = cursor.getInt(5);

        return new Song(songId, songName, artistName, albumName, duration);

    }
}
