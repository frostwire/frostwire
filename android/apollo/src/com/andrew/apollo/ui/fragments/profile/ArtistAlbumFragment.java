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

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.andrew.apollo.Config;
import com.frostwire.android.R;
import com.andrew.apollo.adapters.ArtistAlbumAdapter;
import com.andrew.apollo.loaders.ArtistAlbumLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.andrew.apollo.widgets.VerticalScrollListener.ScrollableHeader;

import java.util.List;

/**
 * This class is used to display all of the albums from a particular artist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistAlbumFragment extends Fragment implements LoaderCallbacks<List<Album>>,
        OnItemClickListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 10;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * The adapter for the grid
     */
    private ArtistAlbumAdapter mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Album song list
     */
    private long[] mAlbumList;

    /**
     * Represents an album
     */
    private Album mAlbum;

    /**
     * Profile header
     */
    private ProfileTabCarousel mProfileTabCarousel;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ArtistAlbumFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        mProfileTabCarousel = (ProfileTabCarousel)activity
                .findViewById(R.id.activity_profile_base_tab_carousel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adpater
        mAdapter = new ArtistAlbumAdapter(getActivity(),
                R.layout.list_item_detailed_no_background);
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
        // Show the songs from the selected album
        mListView.setOnItemClickListener(this);
        // To help make scrolling smooth
        mListView.setOnScrollListener(new VerticalScrollListener(mScrollableHeader,
                mProfileTabCarousel, 1));
        // Remove the scrollbars and padding for the fast scroll
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setFastScrollEnabled(false);
        mListView.setPadding(0, 0, 0, 0);
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
        final Bundle arguments = getArguments();
        if (arguments != null) {
            getLoaderManager().initLoader(LOADER, arguments, this);
        }
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
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(getArguments() != null ? getArguments() : new Bundle());
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
        // Create a new album
        mAlbum = mAdapter.getItem(info.position - 1);
        // Create a list of the album's songs
        mAlbumList = MusicUtils.getSongListForAlbum(getActivity(), mAlbum.mAlbumId);

        // Play the album
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Add the album to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the album to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, false);

        // Delete the album
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        // Avoid leaking context menu selections
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(getActivity(), mAlbumList, 0, false);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), mAlbumList);
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(mAlbumList).show(getFragmentManager(),
                            "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long id = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), mAlbumList, id);
                    return true;
                case FragmentMenuItems.DELETE:
                    DeleteDialog.newInstance(mAlbum.mAlbumName, mAlbumList, null).
                            setOnDeleteCallback(new DeleteDialog.DeleteDialogCallback() {
                                @Override
                                public void onDelete(long[] id) {
                                    refresh();
                                }
                            }).
                            show(getFragmentManager(), "DeleteDialog");
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
        if (position == 0) {
            return;
        }
        mAlbum = mAdapter.getItem(position - 1);
        NavUtils.openAlbumProfile(getActivity(), mAlbum.mAlbumName,
                mAlbum.mArtistName, mAlbum.mAlbumId);
        getActivity().finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Album>> onCreateLoader(final int id, final Bundle args) {
        return new ArtistAlbumLoader(getActivity(), args.getLong(Config.ID));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Album>> loader, final List<Album> data) {
        // Check for any errors
        if (data.isEmpty()) {
            mAdapter.unload();
            mAdapter.notifyDataSetChanged();
            return;
        }

        // Start fresh
        mAdapter.unload();
        // Return the correct count
        mAdapter.setCount(data);
        // Add the data to the adpater
        for (final Album album : data) {
            mAdapter.add(album);
        }
        mAdapter.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Album>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    // Pause disk cache access to ensure smoother scrolling
    private final ScrollableHeader mScrollableHeader = new ScrollableHeader() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onScrollStateChanged(final AbsListView view, final int scrollState) {
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                    || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                mAdapter.setPauseDiskCache(true);
            } else {
                mAdapter.setPauseDiskCache(false);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * Restarts the loader.
     */
    public void refresh() {
        // Scroll to the stop of the list before restarting the loader.
        // Otherwise, if the user has scrolled enough to move the header, it
        // becomes misplaced and needs to be reset.
        mListView.setSelection(0);
        mAdapter.notifyDataSetChanged();
        getLoaderManager().restartLoader(LOADER, getArguments(), this);
    }
}
