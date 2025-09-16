/*
 * Copyright (C) 2012, 2025 Andrew Neal and Angel Leon Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.menu;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

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

        final WeakReference<Dialog> dialogRef = new WeakReference<>(getDialog());

        final String playlistName = mPlaylist.getText().toString();
        if (!playlistName.isEmpty()) {
            try {
                final ContentResolver resolver = requireActivity().getContentResolver();
                final ContentValues values = new ContentValues(1);
                values.put(MediaStore.Audio.Playlists.NAME, Capitalize.capitalize(playlistName));

                // Use the specific URI for the playlist
                Uri playlistUri = MusicUtils.getPlaylistContentUri();
                Uri specificPlaylistUri = ContentUris.withAppendedId(playlistUri, mRenameId);

                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                    LOG.info("RenamePlaylist.onSaveClick() Renaming playlist: " + mDefaultname + " to: " + playlistName);
                    try {
                        int rowsUpdated = resolver.update(specificPlaylistUri, values, null, null);
                        if (rowsUpdated > 0) {
                            LOG.info("RenamePlaylist.onSaveClick() Playlist renamed successfully");
                        } else {
                            LOG.error("RenamePlaylist.onSaveClick() Failed to rename playlist");
                        }
                    } catch (Throwable e) {
                        LOG.error("RenamePlaylist.onSaveClick() resolver.update(uri=" + specificPlaylistUri + "...) failed", e);
                    }

                    SystemUtils.postToUIThread(() -> {
                        try {
                            if (Ref.alive(dialogRef)) {
                                Dialog dialog = dialogRef.get();
                                if (dialog != null) {
                                    dialog.dismiss();
                                    LOG.info("RenamePlaylist.onSaveClick() Dismissed dialog successfully");
                                }
                            }
                        } catch (Throwable t) {
                            LOG.error("RenamePlaylist.onSaveClick() Failed to dismiss dialog", t);
                        }
                    });
                });
            } catch (Throwable e) {
                LOG.error("RenamePlaylist.onSaveClick() Failed to rename playlist", e);
            }
        }
    }


    /**
     * @param id The Id of the playlist
     * @return The name of the playlist
     */
    private String getPlaylistNameFromId(final long id) {
        Uri playlistContentUri = MusicUtils.getPlaylistContentUri();
        Cursor cursor = getActivity().getContentResolver().query(
                playlistContentUri, new String[]{
                        MediaStore.Audio.Playlists.NAME
                }, MediaStore.Audio.Playlists._ID + "=?", new String[]{
                        String.valueOf(id)
                }, MediaStore.Audio.Playlists.NAME);
        return MusicUtils.getFirstStringResult(cursor, true);
    }
}
