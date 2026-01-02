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

package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.andrew.apollo.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class SearchLoader extends SongLoader {
    private String mQuery;

    /**
     * Constructor of <code>SongLoader</code>
     *
     * @param context The {@link Context} to use
     * @param query   The search query
     */
    public SearchLoader(final Context context, final String query) {
        super(context);
        mQuery = query;
    }

    @Override
    public Cursor makeCursor(Context context) {
        return makeSearchCursor(context, mQuery);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Song> loadInBackground() {
        ArrayList<Song> mSongList = new ArrayList<>();
        Cursor mCursor;
        try {
            mCursor = makeCursor(getContext());
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }

        // Gather the data
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                // Copy the song Id
                long id = -1;
                long songId = -1;
                long albumId = -1;
                long artistId = -1;

                // Copy the song name
                final String songName = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

                // Copy the artist name
                final String artist = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST));

                // Copy the album name
                final String album = mCursor.getString(mCursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));


                // This logic prioritizes ARTIST ID > ALBUM ID > SONG ID

                // Check for a song Id
                if (!TextUtils.isEmpty(songName)) {
                    songId = mCursor.getLong(mCursor
                            .getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                }

                if (songId > 0) {
                    id = songId;
                }

                // Check for a album Id
                if (songId < 0 && !TextUtils.isEmpty(album)) {
                    albumId = mCursor.getLong(mCursor
                            .getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
                }

                if (albumId > 0) {
                    id = albumId;
                }

                // Check for a artist Id
                if (albumId < 0 && !TextUtils.isEmpty(artist)) {
                    artistId = mCursor.getLong(mCursor
                            .getColumnIndexOrThrow(MediaStore.Audio.Artists._ID));
                }

                if (artistId > 0) {
                    id = artistId;
                }

                // Create a new song
                final Song song = new Song(id, songName, artist, album, -1);

                // Add everything up
                mSongList.add(song);
            } while (mCursor.moveToNext());
        }
        // Close the cursor
        if (mCursor != null) {
            mCursor.close();
        }
        return mSongList;
    }

    /**
     * * @param context The {@link Context} to use.
     *
     * @param query The user's query.
     * @return The {@link Cursor} used to perform the search.
     */
    private static Cursor makeSearchCursor(final Context context, final String query) {
        SearchCursorParameters searchCursorParameters = SearchCursorParameters.buildSearchCursorParameters(query);
        return context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                searchCursorParameters.projection,
                searchCursorParameters.selection,
                searchCursorParameters.selectionArgs,
                null);
    }

    public final static class SearchCursorParameters {
        public final String[] projection = new String[]{
                BaseColumns._ID,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Media.TITLE
        };

        public final String selection = MediaStore.Audio.Artists.ARTIST + " like ? or " +
                MediaStore.Audio.Albums.ALBUM + " like ? or " +
                MediaStore.Audio.Media.TITLE + " like ?";

        public final String[] selectionArgs;

        private SearchCursorParameters(String query) {
            String encodedQuery = "%" + Uri.encode(query) + "%";
            selectionArgs = new String[] {encodedQuery, encodedQuery, encodedQuery};
        }

        public static SearchCursorParameters buildSearchCursorParameters(String query) {
            return new SearchCursorParameters(query);
        }
    }
}
