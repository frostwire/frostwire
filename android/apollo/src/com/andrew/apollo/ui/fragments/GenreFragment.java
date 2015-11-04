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

import android.content.Intent;
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
import android.widget.TextView;

import com.andrew.apollo.Config;
import com.frostwire.android.R;
import com.andrew.apollo.adapters.GenreAdapter;
import com.andrew.apollo.loaders.GenreLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.utils.MusicUtils;

import java.util.List;

/**
 * This class is used to display all of the genres on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class GenreFragment extends Fragment implements LoaderCallbacks<List<Genre>>,
        OnItemClickListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 5;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * Fragment UI
     */
    private ViewGroup mRootView;

    /**
     * The adapter for the list
     */
    private GenreAdapter mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Genre song list
     */
    private long[] mGenreList;

    /**
     * Represents a genre
     */
    private Genre mGenre;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public GenreFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adpater
        mAdapter = new GenreAdapter(getActivity(), R.layout.list_item_simple);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        // Initialize the list
        mListView = (ListView)mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        mListView.setOnCreateContextMenuListener(this);
        // Show the albums and songs from the selected genre
        mListView.setOnItemClickListener(this);
        return mRootView;
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
        // Create a new genre
        mGenre = mAdapter.getItem(info.position);
        // Create a list of the genre's songs
        mGenreList = MusicUtils.getSongListForGenre(getActivity(), mGenre.mGenreId);

        // Play the genre
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                R.string.context_menu_play_selection);
        // Add the genre to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, R.string.add_to_queue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(getActivity(), mGenreList, 0, false);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), mGenreList);
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
        mGenre = mAdapter.getItem(position);
        // Create a new bundle to transfer the artist info
        final Bundle bundle = new Bundle();
        bundle.putLong(Config.ID, mGenre.mGenreId);
        bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Genres.CONTENT_TYPE);
        bundle.putString(Config.NAME, mGenre.mGenreName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Genre>> onCreateLoader(final int id, final Bundle args) {
        return new GenreLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Genre>> loader, final List<Genre> data) {
        // Check for any errors
        if (data.isEmpty()) {
            // Set the empty text
            final TextView empty = (TextView)mRootView.findViewById(R.id.empty);
            empty.setText(getString(R.string.empty_music));
            mListView.setEmptyView(empty);
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Add the data to the adpater
        for (final Genre genre : data) {
            mAdapter.add(genre);
        }
        // Build the cache
        mAdapter.buildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Genre>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

}
