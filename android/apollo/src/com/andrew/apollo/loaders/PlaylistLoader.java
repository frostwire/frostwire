/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to query MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI and
 * return the playlists on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistLoader extends WrappedAsyncTaskLoader<List<Playlist>> {
    public static final int FAVORITE_PLAYLIST_ID = -1;
    public static final int LAST_ADDED_PLAYLIST_ID = -2;
    public static final int NEW_PLAYLIST_ID = -3;

    private static final Logger LOG = Logger.getLogger(PlaylistLoader.class);

    /**
     * Constructor of <code>PlaylistLoader</code>
     *
     * @param context The {@link Context} to use
     */
    public PlaylistLoader(final Context context) {
        super(context);
    }

    @Override
    public List<Playlist> loadInBackground() {

        // Add the default playlists
        List<Playlist> mPlaylistList = new ArrayList<>(makeDefaultPlaylists());

        // Fetch user-created playlists using MusicUtils
        List<Playlist> userPlaylists = MusicUtils.getPlaylists(getContext());

        if (!userPlaylists.isEmpty()) {
            mPlaylistList.addAll(userPlaylists);
        } else {
            LOG.info("No custom playlists found.");
        }

        return mPlaylistList;
    }


    /* Adds the favorites and last added playlists */
    private List<Playlist> makeDefaultPlaylists() {
        final Resources resources = getContext().getResources();
        ArrayList<Playlist> mPlaylistList = new ArrayList<>();

        /* New Empty list */
        final Playlist newPlaylist = new Playlist(NEW_PLAYLIST_ID, resources.getString(R.string.new_empty_playlist));
        mPlaylistList.add(newPlaylist);

        /* Favorites list */
        final Playlist favorites = new Playlist(FAVORITE_PLAYLIST_ID, resources.getString(R.string.playlist_favorites));
        mPlaylistList.add(favorites);

        /* Last added list */
        final Playlist lastAdded = new Playlist(LAST_ADDED_PLAYLIST_ID, resources.getString(R.string.playlist_last_added));
        mPlaylistList.add(lastAdded);
        return mPlaylistList;
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     *
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the playlist query.
     */
    public static Cursor makePlaylistCursor(final Context context) {
        try {
            Uri playlistUri = MusicUtils.getPlaylistContentUri();

            String selection = null;
            String[] selectionArgs = null;

            // Add a filter for OWNER_PACKAGE_NAME on Android 11+, turns out we were saving playlists using the wrong
            // playlistUri for newer androids and they end up without an owner, and then we cannot add songs nor rename, nor delete them
            // therefore, we should just filter playlists by the owner package name, so that we only get playlists created by this app
            // or playlists created properly
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                selection = "owner_package_name = ?";
                selectionArgs = new String[]{context.getPackageName()};
            }

            return context.getContentResolver().query(
                    playlistUri,
                    new String[]{BaseColumns._ID, MediaStore.Audio.PlaylistsColumns.NAME},
                    selection, selectionArgs, MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER
            );

        } catch (Throwable t) {
            LOG.error("PlaylistLoader.makePlaylistCursor(): Error querying playlists", t);
            return null;
        }
    }
}
