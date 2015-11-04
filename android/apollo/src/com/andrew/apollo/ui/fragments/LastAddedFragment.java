/*
 * Copyright (C) 2015 Angel Leon, Alden Torres, Andrew Neal Licensed under the Apache License, Version 2.0
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
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.adapters.SongAdapter;
import com.andrew.apollo.loaders.LastAddedLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.frostwire.android.R;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.List;

/**
 * This class is used to display all of the songs on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public class LastAddedFragment extends Fragment implements LoaderCallbacks<List<Song>>,
        OnItemClickListener, MusicStateListener {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = TabFragmentOrder.LAST_ADDED_POSITION;

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
    private SongAdapter mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Represents a song
     */
    private Song mSong;

    /**
     * Position of a context menu item
     */
    private int mSelectedPosition;

    /**
     * Id of a context menu item
     */
    private long mSelectedId;

    /**
     * Song, album, and artist name used in the context menu
     */
    private String mSongName, mAlbumName, mArtistName;

    /**
     * True if the list should execute {@code #restartLoader()}.
     */
    private boolean mShouldRefresh = false;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public LastAddedFragment() {
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
        mAdapter = new SongAdapter(getActivity(), R.layout.list_item_simple);
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
        // Play the selected song
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
        mSelectedPosition = info.position;
        // Creat a new song
        mSong = mAdapter.getItem(mSelectedPosition);
        mSelectedId = mSong.mSongId;
        mSongName = mSong.mSongName;
        mAlbumName = mSong.mAlbumName;
        mArtistName = mSong.mArtistName;

        // Play the song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
                getString(R.string.context_menu_play_selection));

        // Play next
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE,
                getString(R.string.context_menu_play_next));

        // Add the song to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
                getString(R.string.add_to_queue));

        // Add the song to a playlist
        final SubMenu subMenu = menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST,
                Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, true);

        // View more content by the song artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
                getString(R.string.context_menu_more_by_artist));

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
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(getActivity(), mArtistName);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(getActivity(), mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    mShouldRefresh = true;
                    DeleteDialog.newInstance(mSong.mSongName, new long[] {
                            mSelectedId
                    }, null).show(getFragmentManager(), "DeleteDialog");
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
        if (mAdapter != null) {
            MusicUtils.playAllFromUserItemClick(getActivity(), mAdapter, position);
        }
        notifyAdapterDataSetChanged();
    }

    public void notifyAdapterDataSetChanged() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new LastAddedLoader(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
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
        for (final Song song : data) {
            mAdapter.add(song);
        }
        // Build the cache
        mAdapter.buildCache();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<List<Song>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * Scrolls the list to the currently playing song when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentSong() {
        final int currentSongPosition = getItemPositionBySong();

        if (currentSongPosition != 0) {
            mListView.setSelection(currentSongPosition);
        }
    }

    /**
     * @return The position of an item in the list based on the name of the
     *         currently playing song.
     */
    private int getItemPositionBySong() {
        final long trackId = MusicUtils.getCurrentAudioId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (mAdapter.getItem(i).mSongId == trackId) {
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
        notifyAdapterDataSetChanged();
    }
}