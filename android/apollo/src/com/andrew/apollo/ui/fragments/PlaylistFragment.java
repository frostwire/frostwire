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

package com.andrew.apollo.ui.fragments;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.PlaylistAdapter;
import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.menu.RenamePlaylist;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the playlists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public class PlaylistFragment extends ApolloFragment<PlaylistAdapter, Playlist> {

    public PlaylistFragment() {
        super(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, Fragments.PLAYLIST_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final int mPosition = info.position;

        menu.clear();
        mItem = mAdapter.getItem(mPosition);

        // Play the playlist
        menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE, R.string.context_menu_play_selection);

        // Add the playlist to the queue
        menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, R.string.add_to_queue);

        // Delete and rename (user made playlists)
        if (info.position > 1) {
            menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.RENAME_PLAYLIST, Menu.NONE, R.string.context_menu_rename_playlist);
            menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, R.string.context_menu_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == Fragments.PLAYLIST_FRAGMENT_GROUP_ID) {
            final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    if (info.position == 0) {
                        MusicUtils.playFavorites(getActivity());
                    } else if (info.position == 1) {
                        MusicUtils.playLastAdded(getActivity());
                    } else {
                        MusicUtils.playPlaylist(getActivity(), mItem.mPlaylistId);
                    }
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    long[] list;
                    if (info.position == 0) {
                        list = MusicUtils.getSongListForFavorites(getActivity());
                    } else if (info.position == 1) {
                        list = MusicUtils.getSongListForLastAdded(getActivity());
                    } else {
                        list = MusicUtils.getSongListForPlaylist(getActivity(), mItem.mPlaylistId);
                    }
                    MusicUtils.addToQueue(getActivity(), list);
                    return true;
                case FragmentMenuItems.RENAME_PLAYLIST:
                    RenamePlaylist.getInstance(mItem.mPlaylistId).show(getFragmentManager(), "RenameDialog");
                    return true;
                case FragmentMenuItems.DELETE:
                    buildDeleteDialog().show();
                    return true;
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected PlaylistAdapter createAdapter() {
        return new PlaylistAdapter(getActivity(), R.layout.list_item_simple);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        final Bundle bundle = new Bundle();
        mItem = mAdapter.getItem(position);
        String playlistName;
        // Favorites list
        if (position == 0) {
            playlistName = getString(R.string.playlist_favorites);
            bundle.putString(Config.MIME_TYPE, getString(R.string.playlist_favorites));
            // Last added
        } else if (position == 1) {
            playlistName = getString(R.string.playlist_last_added);
            bundle.putString(Config.MIME_TYPE, getString(R.string.playlist_last_added));
        } else {
            // User created
            playlistName = mItem.mPlaylistName;
            bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Playlists.CONTENT_TYPE);
            bundle.putLong(Config.ID, mItem.mPlaylistId);
        }

        bundle.putString(Config.NAME, playlistName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public Loader<List<Playlist>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistLoader(getActivity());
    }

    private AlertDialog buildDeleteDialog() {
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.delete_dialog_title, mItem.mPlaylistName))
                .setPositiveButton(R.string.context_menu_delete, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Uri mUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                mItem.mPlaylistId);
                        getActivity().getContentResolver().delete(mUri, null, null);
                        MusicUtils.refresh();
                    }
                }).setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).setMessage(R.string.cannot_be_undone).create();
    }

}
