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

import static com.andrew.apollo.utils.PreferenceUtils.ARTIST_LAYOUT;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import com.andrew.apollo.MusicStateListener;
import com.frostwire.android.R;
import com.andrew.apollo.adapters.ArtistAdapter;
import com.andrew.apollo.loaders.ArtistLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.jlibtorrent.Logger;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.List;

/**
 * This class is used to display all of the artists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistFragment extends Fragment implements LoaderCallbacks<List<Artist>>,
        OnScrollListener, OnItemClickListener, MusicStateListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 2;

    /**
     * Grid view column count. ONE - list, TWO - normal grid, FOUR - landscape
     */
    private static final int ONE = 1, TWO = 2, FOUR = 4;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * Fragment UI
     */
    private ViewGroup mRootView;

    /**
     * The adapter for the grid
     */
    private ArtistAdapter mAdapter;

    /**
     * The grid view
     */
    private GridView mGridView;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Artist song list
     */
    private long[] mArtistList;

    /**
     * Represents an artist
     */
    private Artist mArtist;

    /**
     * True if the list should execute {@code #restartLoader()}.
     */
    private boolean mShouldRefresh = false;

    private static Logger LOG = Logger.getLogger(ArtistFragment.class);

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ArtistFragment() {
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
        int layout = R.layout.grid_items_normal;
        if (isSimpleLayout()) {
            layout = R.layout.list_item_simple;
        } else if (isDetailedLayout()) {
            layout = R.layout.list_item_detailed;
        } else {
            layout = R.layout.grid_items_normal;
        }
        mAdapter = new ArtistAdapter(getActivity(), layout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        if (isSimpleLayout()) {
            mRootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
            initListView();
        } else {
            mRootView = (ViewGroup)inflater.inflate(R.layout.grid_base, null);
            initGridView();
        }
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
    public void onPause() {
        super.onPause();
        mAdapter.flush();
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
        // Creat a new model
        mArtist = mAdapter.getItem(info.position);
        // Create a list of the artist's songs
        mArtistList = MusicUtils.getSongListForArtist(getActivity(), mArtist.mArtistId);

        // Play the artist
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Add the artist to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the artist to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, false);

        // Delete the artist
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        // Avoid leaking context menu selections
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(getActivity(), mArtistList, 0, true);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), mArtistList);
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(mArtistList).show(getFragmentManager(),
                            "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long id = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), mArtistList, id);
                    return true;
                case FragmentMenuItems.DELETE:
                    mShouldRefresh = true;
                    if (mArtist != null) {
                        final String artist = mArtist.mArtistName;
                        DeleteDialog.newInstance(artist, mArtistList, artist).show(
                                getFragmentManager(), "DeleteDialog");
                    } else {
                        LOG.error("Could not delete artist: mArtist == null");
                    }
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
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        mArtist = mAdapter.getItem(position);
        NavUtils.openArtistProfile(getActivity(), mArtist.mArtistName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Artist>> onCreateLoader(final int id, final Bundle args) {
        return new ArtistLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Artist>> loader, final List<Artist> data) {
        // Check for any errors
        if (data.isEmpty()) {
            // Set the empty text
            final TextView empty = (TextView)mRootView.findViewById(R.id.empty);
            empty.setText(getString(R.string.empty_music));
            if (isSimpleLayout()) {
                mListView.setEmptyView(empty);
            } else {
                mGridView.setEmptyView(empty);
            }
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Add the data to the adpater
        for (final Artist artist : data) {
            mAdapter.add(artist);
        }
        // Build the cache
        mAdapter.buildCache();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Artist>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * Scrolls the list to the currently playing artist when the user touches
     * the header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentArtist() {
        final int currentArtistPosition = getItemPositionByArtist();

        if (currentArtistPosition != 0) {
            if (isSimpleLayout()) {
                mListView.setSelection(currentArtistPosition);
            } else {
                mGridView.setSelection(currentArtistPosition);
            }
        }
    }

    /**
     * @return The position of an item in the list or grid based on the name of
     *         the currently playing artist.
     */
    private int getItemPositionByArtist() {
        final long artistId = MusicUtils.getCurrentArtistId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (mAdapter.getItem(i).mArtistId == artistId) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Restarts the loader.
     */
    public void refresh() {
        // Wait a moment for the preference to change.
        SystemClock.sleep(10);
        getLoaderManager().restartLoader(LOADER, null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        // Update the list when the user deletes any items
        if (mShouldRefresh) {
            getLoaderManager().restartLoader(LOADER, null, this);
        }
        mShouldRefresh = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // Nothing to do
    }

    /**
     * Sets up various helpers for both the list and grid
     * 
     * @param list The list or grid
     */
    private void initAbsListView(final AbsListView list) {
        // Release any references to the recycled Views
        list.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        list.setOnCreateContextMenuListener(this);
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);
        // To help make scrolling smooth
        list.setOnScrollListener(this);
    }

    /**
     * Sets up the list view
     */
    private void initListView() {
        // Initialize the grid
        mListView = (ListView)mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Set up the helpers
        initAbsListView(mListView);
    }

    /**
     * Sets up the grid view
     */
    private void initGridView() {
        // Initialize the grid
        mGridView = (GridView)mRootView.findViewById(R.id.grid_base);
        // Set the data behind the grid
        mGridView.setAdapter(mAdapter);
        // Set up the helpers
        initAbsListView(mGridView);
        if (ApolloUtils.isLandscape(getActivity())) {
            if (isDetailedLayout()) {
                mAdapter.setLoadExtraData(true);
                mGridView.setNumColumns(TWO);
            } else {
                mGridView.setNumColumns(FOUR);
            }
        } else {
            if (isDetailedLayout()) {
                mAdapter.setLoadExtraData(true);
                mGridView.setNumColumns(ONE);
            } else {
                mGridView.setNumColumns(TWO);
            }
        }
    }

    private boolean isSimpleLayout() {
        return PreferenceUtils.getInstance(getActivity()).isSimpleLayout(ARTIST_LAYOUT,
                getActivity());
    }

    private boolean isDetailedLayout() {
        return PreferenceUtils.getInstance(getActivity()).isDetailedLayout(ARTIST_LAYOUT,
                getActivity());
    }
}
