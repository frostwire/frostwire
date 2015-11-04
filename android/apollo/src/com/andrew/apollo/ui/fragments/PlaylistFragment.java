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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.andrew.apollo.Config;
import com.andrew.apollo.MusicStateListener;
import com.frostwire.android.R;
import com.andrew.apollo.adapters.PlaylistAdapter;
import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.menu.RenamePlaylist;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.utils.MusicUtils;

import java.util.List;

/**
 * This class is used to display all of the playlists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistFragment extends Fragment implements LoaderCallbacks<List<Playlist>>,
        OnItemClickListener, MusicStateListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = TabFragmentOrder.PLAYLISTS_POSITION;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * The adapter for the list
     */
    private PlaylistAdapter mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Represents a playlist
     */
    private Playlist mPlaylist;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PlaylistFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        // Register the music status listener
        ((BaseActivity)activity).setMusicStateListenerListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adpater
        mAdapter = new PlaylistAdapter(getActivity(), R.layout.list_item_simple);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        // Initialize the list
        mListView = (ListView)rootView.findViewById(R.id.list_base);
        // Set the data behind the grid
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        mListView.setOnCreateContextMenuListener(this);
        // Play the selected song
        mListView.setOnItemClickListener(this);
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        getLoaderManager().initLoader(LOADER, null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final int mPosition = info.position;
        // Create a new playlist
        mPlaylist = mAdapter.getItem(mPosition);

        // Play the playlist
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                R.string.context_menu_play_selection);

        // Add the playlist to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, R.string.add_to_queue);

        // Delete and rename (user made playlists)
        if (info.position > 1) {
            menu.add(GROUP_ID, FragmentMenuItems.RENAME_PLAYLIST, Menu.NONE,
                    R.string.context_menu_rename_playlist);

            menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, R.string.context_menu_delete);

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            final AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    if (info.position == 0) {
                        MusicUtils.playFavorites(getActivity());
                    } else if (info.position == 1) {
                        MusicUtils.playLastAdded(getActivity());
                    } else {
                        MusicUtils.playPlaylist(getActivity(), mPlaylist.mPlaylistId);
                    }
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    long[] list = null;
                    if (info.position == 0) {
                        list = MusicUtils.getSongListForFavorites(getActivity());
                    } else if (info.position == 1) {
                        list = MusicUtils.getSongListForLastAdded(getActivity());
                    } else {
                        list = MusicUtils.getSongListForPlaylist(getActivity(),
                                mPlaylist.mPlaylistId);
                    }
                    MusicUtils.addToQueue(getActivity(), list);
                    return true;
                case FragmentMenuItems.RENAME_PLAYLIST:
                    RenamePlaylist.getInstance(mPlaylist.mPlaylistId).show(
                            getFragmentManager(), "RenameDialog");
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        final Bundle bundle = new Bundle();
        mPlaylist = mAdapter.getItem(position);
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
            playlistName = mPlaylist.mPlaylistName;
            bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Playlists.CONTENT_TYPE);
            bundle.putLong(Config.ID, mPlaylist.mPlaylistId);
        }

        bundle.putString(Config.NAME, playlistName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Playlist>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Playlist>> loader, final List<Playlist> data) {
        // Check for any errors
        if (data.isEmpty()) {
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Add the data to the adpater
        for (final Playlist playlist : data) {
            mAdapter.add(playlist);
        }
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Playlist>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        // Refresh the list when a playlist is deleted or renamed
        getLoaderManager().restartLoader(LOADER, null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // Nothing to do
    }

    /**
     * Create a new {@link AlertDialog} for easy playlist deletion
     * 
     * @param context The {@link Context} to use
     * @param title The title of the playlist being deleted
     * @param id The ID of the playlist being deleted
     * @return A new {@link AlertDialog} used to delete playlists
     */
    private final AlertDialog buildDeleteDialog() {
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.delete_dialog_title, mPlaylist.mPlaylistName))
                .setPositiveButton(R.string.context_menu_delete, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Uri mUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                mPlaylist.mPlaylistId);
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
