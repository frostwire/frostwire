/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.menu;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.devspark.appmsg.AppMsg;
import com.frostwire.android.R;
import com.frostwire.util.Logger;

import java.util.List;

/**
 * @author Andrew Neal (andrewdneal@gmail.com) TODO - The playlist names are
 *         automatically capitalized to help when you want to play one via voice
 *         actions, but it really needs to work either way. As in, capitalized
 *         or not.
 */
public class CreateNewPlaylist extends BasePlaylistDialog {

    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(CreateNewPlaylist.class);

    // The playlist list
    private long[] mPlaylistList = new long[] {};

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(final Bundle bundle) {
        bundle.putString("defaultname", mPlaylist.getText().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initObjects(final Bundle savedInstanceState) {
        mPlaylistList = getArguments().getLongArray("playlist_list");
        mDefaultname = savedInstanceState != null ? savedInstanceState.getString("defaultname")
                : makePlaylistName();
        if (mDefaultname == null) {
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
            final int playlistId = (int)MusicUtils.getIdForPlaylist(getActivity(),
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
            if (mPlaylistList != null && (added=mPlaylistList.length) > 0) {
                final String message = getResources().getQuantityString(R.plurals.NNNtrackstoplaylist, added, added);
                AppMsg.makeText(getActivity(), message, AppMsg.STYLE_CONFIRM).show();
            }
            closeKeyboard();
            getDialog().dismiss();
        }
    }

    private String makePlaylistName() {
        final String template = getString(R.string.new_playlist_name_template);
        int num = 1;
        final String[] projection = new String[] {
            MediaStore.Audio.Playlists.NAME
        };
        final ContentResolver resolver = getActivity().getContentResolver();
        final String selection = MediaStore.Audio.Playlists.NAME + " != ''";
        Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, projection,
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

    /** Looks for the PlaylistFragment and keeps it as a weak reference in case its found.
     * This way we can invoke that fragment's refresh() method when we need it, keeping
     * all the on Ok logic here and not forcing ApolloFragment to implement OnDialogClick
     * and having to handle each possible's dialog click with a bunch of conditions. */
    private void lookForPlaylistFragment() {
        final List<Fragment> fragments = getFragmentManager().getFragments();
        if (fragments != null && !fragments.isEmpty()) {
            for (Fragment f : fragments) {
                if (f instanceof PlaylistFragment) {
                    updateApolloFragmentReference((ApolloFragment) f);
                    return;
                }
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        lookForPlaylistFragment();
    }
}
