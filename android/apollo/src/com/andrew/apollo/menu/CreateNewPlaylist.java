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

package com.andrew.apollo.menu;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.utils.MusicUtils;
import com.devspark.appmsg.AppMsg;
import com.frostwire.android.R;

/**
 * @author Andrew Neal (andrewdneal@gmail.com) TODO - The playlist names are
 *         automatically capitalized to help when you want to play one via voice
 *         actions, but it really needs to work either way. As in, capitalized
 *         or not.
 */
public final class CreateNewPlaylist extends BasePlaylistDialog {

    // The playlist list
    private long[] mPlaylistList = new long[]{};

    /**
     * @param list The list of tracks to add to the playlist
     * @return A new instance of this dialog.
     */
    public static CreateNewPlaylist getInstance(final long[] list) {
        final CreateNewPlaylist frag = new CreateNewPlaylist();
        final Bundle args = new Bundle();
        args.putLongArray("playlist_list", list);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onSaveInstanceState(final Bundle bundle) {
        bundle.putString("defaultname", mPlaylist.getText().toString());
    }

    @Override
    public void initObjects(final Bundle savedInstanceState) {
        mPlaylistList = getArguments().getLongArray("playlist_list");
        mDefaultname = savedInstanceState != null ? savedInstanceState.getString("defaultname")
                : makePlaylistName();
        if (mDefaultname == null && getDialog() != null) {
            getDialog().dismiss();
        }
    }

    @Override
    public void onSaveClick() {
        if (mPlaylist.getText() == null) {
            return;
        }
        final String playlistName = mPlaylist.getText().toString();
        if (playlistName.length() > 0) {
            final int playlistId = (int) MusicUtils.getIdForPlaylist(getActivity(),
                    playlistName);
            if (playlistId >= 0) {
                MusicUtils.clearPlaylist(getActivity(), playlistId);
                MusicUtils.addToPlaylist(getActivity(), mPlaylistList, playlistId);
            } else {
                final long newId = MusicUtils.createPlaylist(getActivity(),
                        Capitalize.capitalize(playlistName));
                MusicUtils.addToPlaylist(getActivity(), mPlaylistList, newId);
            }
            int added;
            if (mPlaylistList != null && (added = mPlaylistList.length) > 0) {
                final String message = getResources().getQuantityString(R.plurals.NNNtrackstoplaylist, added, added);
                AppMsg.makeText(getActivity(), message, AppMsg.STYLE_CONFIRM).show();
            }
            getDialog().dismiss();
        }
    }

    private String makePlaylistName() {
        final String template = getString(R.string.new_playlist_name_template);
        int num = 1;
        final String[] projection = new String[]{
                MediaStore.Audio.Playlists.NAME
        };
        final ContentResolver resolver = getActivity().getContentResolver();
        final String selection = MediaStore.Audio.Playlists.NAME + " != ''";

        Uri playlistContentUri = MusicUtils.getPlaylistContentUri();

        Cursor cursor = resolver.query(playlistContentUri, projection,
                selection, null, MediaStore.Audio.Playlists.NAME);
        if (cursor == null) {
            return null;
        }

        String suggestedName;
        suggestedName = String.format(template, num++);
        boolean done = false;
        while (!done) {
            done = true;
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                final String playlistName = cursor.getString(0);
                if (playlistName.compareToIgnoreCase(suggestedName) == 0) {
                    suggestedName = String.format(template, num++);
                    done = false;
                }
                cursor.moveToNext();
            }
        }
        cursor.close();
        return suggestedName;
    }
}
