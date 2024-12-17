/*
 * Copyright (C) 2012, 2025 Andrew Neal and Angel Leon Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.menu;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.util.Logger;

/**
 * Alert dialog used to rename playlists.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 */
public class RenamePlaylist extends BasePlaylistDialog {

    private static Logger LOG = Logger.getLogger(RenamePlaylist.class);

    private long mRenameId;

    /**
     * @param id The Id of the playlist to rename
     * @return A new instance of this dialog.
     */
    public static RenamePlaylist getInstance(final Long id) {
        final RenamePlaylist frag = new RenamePlaylist();
        final Bundle args = new Bundle();
        args.putLong("rename", id);
        frag.setArguments(args);
        return frag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(final Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
        outcicle.putLong("rename", mRenameId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initObjects(final Bundle savedInstanceState) {
        mRenameId = savedInstanceState != null ? savedInstanceState.getLong("rename")
                : getArguments().getLong("rename", -1);
        String mOriginalName = getPlaylistNameFromId(mRenameId);
        mDefaultname = savedInstanceState != null ? savedInstanceState.getString("defaultname")
                : mOriginalName;
        if (mRenameId < 0 || mOriginalName == null || mDefaultname == null) {
            getDialog().dismiss();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveClick() {
        if (mPlaylist.getText() == null) {
            return;
        }
        final String playlistName = mPlaylist.getText().toString();
        if (!playlistName.isEmpty()) {
            final ContentResolver resolver = getActivity().getContentResolver();
            final ContentValues values = new ContentValues(1);
            values.put(MediaStore.Audio.Playlists.NAME, Capitalize.capitalize(playlistName));

            // Use the specific URI for the playlist
            Uri playlistUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                playlistUri = MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            Uri specificPlaylistUri = ContentUris.withAppendedId(playlistUri, mRenameId);

            try {
                int rowsUpdated = resolver.update(specificPlaylistUri, values, null, null);
                if (rowsUpdated > 0) {
                    LOG.info("RenamePlaylist.onSaveClick() Playlist renamed successfully");
                } else {
                    LOG.error("RenamePlaylist.onSaveClick() Failed to rename playlist");
                }
            } catch (IllegalArgumentException e) {
                LOG.error("RenamePlaylist.onSaveClick() Invalid URI: " + specificPlaylistUri, e);
            }

            getDialog().dismiss();
        }
    }


    /**
     * @param id The Id of the playlist
     * @return The name of the playlist
     */
    private String getPlaylistNameFromId(final long id) {
        Cursor cursor = getActivity().getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[] {
                    MediaStore.Audio.Playlists.NAME
                }, MediaStore.Audio.Playlists._ID + "=?", new String[] {
                    String.valueOf(id)
                }, MediaStore.Audio.Playlists.NAME);
        return MusicUtils.getFirstStringResult(cursor, true);
    }
}
