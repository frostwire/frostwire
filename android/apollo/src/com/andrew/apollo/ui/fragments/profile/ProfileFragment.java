/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.ui.fragments.profile;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;
import com.andrew.apollo.adapters.ApolloFragmentAdapter;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.frostwire.android.R;

import java.util.List;

/**
 * Created by gubatron on 1/26/16 on a plane.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class ProfileFragment<T extends ApolloFragmentAdapter<I>, I> extends Fragment implements
        LoaderManager.LoaderCallbacks<List<I>>,
        AdapterView.OnItemClickListener {

    private final int GROUP_ID;
    /**
     * LoaderCallbacks identifier
     */
    protected final int LOADER;
    /**
     * The list view
     */
    protected ListView mListView;
    /**
     * The adapter for the list
     */
    protected T mAdapter;
    /**
     * Represents a song/album/
     */
    protected I mItem;
    /**
     * Id of a context menu item
     */
    protected long mSelectedId;
    /**
     * Song, album, and artist name used in the context menu
     */
    protected String mSongName, mAlbumName, mArtistName;
    /**
     * Profile header
     */
    private ProfileTabCarousel mProfileTabCarousel;

    abstract T createAdapter();

    public abstract void onItemClick(final AdapterView<?> parent, final View view, final int position,
                                     final long id);

    protected ProfileFragment(int groupId, int loaderId) {
        this.GROUP_ID = groupId;
        this.LOADER = loaderId;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        mProfileTabCarousel = (ProfileTabCarousel)activity
                .findViewById(R.id.activity_profile_base_tab_carousel);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        // Initialize the list
        mListView = (ListView)rootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        mListView.setOnCreateContextMenuListener(this);
        // Play the selected song
        mListView.setOnItemClickListener(this);
        // To help make scrolling smooth
        mListView.setOnScrollListener(new VerticalScrollListener(null, mProfileTabCarousel, 0));
        // Remove the scrollbars and padding for the fast scroll
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setFastScrollEnabled(false);
        mListView.setPadding(0, 0, 0, 0);
        return rootView;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adapter
        mAdapter = createAdapter();
    }

    public T getAdapter() {
        return mAdapter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Get the position of the selected item
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int mSelectedPosition = info.position - 1;

        // Create a new song
        mItem = mAdapter.getItem(mSelectedPosition);

        if (mItem instanceof Song) {
            Song mSong = (Song) mItem;
            mSelectedId = mSong.mSongId;
            mSongName = mSong.mSongName;
            mAlbumName = mSong.mAlbumName;
            mArtistName = mSong.mArtistName;
        }

        // Play the song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Play the song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE,
                getString(R.string.context_menu_play_next));

        // Add the song to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the song to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, true);

        // Make the song a ringtone
        menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE,
                getString(R.string.context_menu_use_as_ringtone));

        // Delete the song
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(getActivity(), new long[] {
                            mSelectedId
                    }, 0, false);
                    return true;
                case FragmentMenuItems.PLAY_NEXT:
                    MusicUtils.playNext(new long[] {
                            mSelectedId
                    });
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), new long[] {
                            mSelectedId
                    });
                    return true;
                case FragmentMenuItems.ADD_TO_FAVORITES:
                    FavoritesStore.getInstance(getActivity()).addSongId(
                            mSelectedId, mSongName, mAlbumName, mArtistName);
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(new long[] {
                            mSelectedId
                    }).show(getFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long mPlaylistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), new long[] {
                            mSelectedId
                    }, mPlaylistId);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(getActivity(), mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    String title = null;

                    if (mItem instanceof Song) {
                        title = ((Song) mItem).mSongName;
                    } else if (mItem instanceof Album) {
                        title = ((Album) mItem).mAlbumName;
                    } else if (mItem instanceof Artist) {
                        title = ((Artist) mItem).mArtistName;
                    }

                    DeleteDialog.newInstance(title, new long[] {
                            mSelectedId
                    }, null).setOnDeleteCallback(new DeleteDialog.DeleteDialogCallback() {
                        @Override
                        public void onDelete(long[] id) {
                            refresh();
                        }
                    }).show(getFragmentManager(), "DeleteDialog");
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

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(getArguments() != null ? getArguments() : new Bundle());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<I>> loader, final List<I> data) {
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
        // Add the data to the adapter
        for (final I item : data) {
            mAdapter.add(item);
        }
        mAdapter.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<I>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

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
