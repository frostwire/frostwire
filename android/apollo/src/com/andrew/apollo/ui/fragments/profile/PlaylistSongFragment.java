/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
 *
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

package com.andrew.apollo.ui.fragments.profile;

import android.content.ContentResolver;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.dragdrop.DragSortListView;
import com.andrew.apollo.dragdrop.DragSortListView.DragScrollProfile;
import com.andrew.apollo.dragdrop.DragSortListView.DropListener;
import com.andrew.apollo.dragdrop.DragSortListView.RemoveListener;
import com.andrew.apollo.loaders.PlaylistSongLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.Fragments;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;

import java.util.List;

/**
 * This class is used to display all of the songs from a particular playlist.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 * @author Jose Molina (@votaguz)
 */
public final class PlaylistSongFragment extends ApolloFragment<ProfileSongAdapter, Song> implements DropListener, RemoveListener, DragScrollProfile {

    public PlaylistSongFragment() {
        super(Fragments.PLAYLIST_SONG_PROFILE_FRAGMENT_GROUP_ID,
                Fragments.PLAYLIST_SONG_PROFILE_FRAGMENT_LOADER_ID,
                R.string.empty_playlist);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        final Bundle arguments = getArguments();
        if (arguments != null) {
            mPlaylistId = arguments.getLong(Config.ID);
        }
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        // Remove the song from playlist
        menu.add(Fragments.PLAYLIST_SONG_PROFILE_FRAGMENT_GROUP_ID, FragmentMenuItems.REMOVE_FROM_PLAYLIST, Menu.NONE,
                getString(R.string.context_menu_remove_from_playlist))
                .setIcon(R.drawable.contextmenu_icon_remove_transfer);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        DragSortListView dsListView = (DragSortListView) mListView;
        dsListView.setDropListener(this);
        // Set the swipe to remove listener
        dsListView.setRemoveListener(this);
        // Quick scroll while dragging
        dsListView.setDragScrollProfile(this);
        //fix scrollbar todo figure out why was it disabled in ApolloFragment
        dsListView.setVerticalScrollBarEnabled(true);
        dsListView.setFastScrollEnabled(true);
        mEmptyTextView.setOnClickListener(v -> UIUtils.openURL(v.getContext(), Constants.FROSTWIRE_ANDROID_FAQ_HOW_TO_ADD_SONGS_TO_PLAYLIST_URL));
        return mRootView;
    }

    @Override
    protected ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(getActivity(), R.layout.edit_track_list_item, ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        onSongItemClick(position);
    }

    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistSongLoader(getActivity(), mPlaylistId);
    }

    @Override
    public float getSpeed(final float w, final long t) {
        if (w > 0.8f) {
            return mAdapter.getCount() / 0.001f;
        } else {
            return 10.0f * w;
        }
    }

    @Override
    public void remove(final int which) {
        mItem = mAdapter.getItem(which - 1);
        mAdapter.remove(mItem);
        mListView.invalidate();
        mAdapter.notifyDataSetChanged();
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistId);
        getActivity().getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + mItem.mSongId,
                null);
    }

    @Override
    public void drop(final int from, final int to) {
        if (from == 0 || to == 0 || from == to) {
            mAdapter.notifyDataSetChanged();
            return;
        }

        int realFrom = from - 1;
        int realTo = to - 1;

        try {
            mItem = mAdapter.getItem(realFrom);
            mAdapter.remove(mItem);
            int count = mAdapter.getCount();
            int adjustedTo = (realTo >= count) ? count-1 : realTo;
            mAdapter.insert(mItem, adjustedTo);
            mAdapter.notifyDataSetChanged();
            final ContentResolver resolver = getActivity().getContentResolver();
            MediaStore.Audio.Playlists.Members.moveItem(resolver, mPlaylistId, realFrom, adjustedTo);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }
}
