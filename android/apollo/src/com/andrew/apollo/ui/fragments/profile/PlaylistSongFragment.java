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

package com.andrew.apollo.ui.fragments.profile;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.dragdrop.DragSortListView.DragScrollProfile;
import com.andrew.apollo.dragdrop.DragSortListView.DropListener;
import com.andrew.apollo.dragdrop.DragSortListView.RemoveListener;
import com.andrew.apollo.loaders.PlaylistSongLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs from a particular playlist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class PlaylistSongFragment extends ProfileFragment<ProfileSongAdapter, Song> implements DropListener, RemoveListener, DragScrollProfile {

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PlaylistSongFragment() {
        super(PLAYLIST_SONG_FRAGMENT_GROUP_ID, PLAYLIST_SONG_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        final Bundle arguments = getArguments();
        if (arguments != null) {
            mPlaylistId = arguments.getLong(Config.ID);
        }
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // View more content by the song artist
        menu.add(PLAYLIST_SONG_FRAGMENT_GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
                getString(R.string.context_menu_more_by_artist));

        // Remove the song from playlist
        menu.add(PLAYLIST_SONG_FRAGMENT_GROUP_ID, FragmentMenuItems.REMOVE_FROM_PLAYLIST, Menu.NONE,
                getString(R.string.context_menu_remove_from_playlist));
    }

    @Override
    ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(getActivity(), R.layout.edit_track_list_item, ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        MusicUtils.playAllFromUserItemClick(mAdapter, position);
    }

    /**
     * {@inheritDoc}
     */
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
            mAdapter.insert(mItem, realTo);
            mAdapter.notifyDataSetChanged();
            final ContentResolver resolver = getActivity().getContentResolver();
            MediaStore.Audio.Playlists.Members.moveItem(resolver, mPlaylistId, realFrom, realTo);
        } catch (Throwable ignored) {
        }
    }
}
