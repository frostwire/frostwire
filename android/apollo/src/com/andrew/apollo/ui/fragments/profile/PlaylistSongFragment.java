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
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.dragdrop.DragSortListView;
import com.andrew.apollo.dragdrop.DragSortListView.DragScrollProfile;
import com.andrew.apollo.dragdrop.DragSortListView.DropListener;
import com.andrew.apollo.dragdrop.DragSortListView.RemoveListener;
import com.andrew.apollo.loaders.PlaylistSongLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs from a particular playlist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistSongFragment extends Fragment implements LoaderCallbacks<List<Song>>,
        OnItemClickListener, DropListener, RemoveListener, DragScrollProfile {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 8;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 312931820;

    /**
     * The adapter for the list
     */
    private ProfileSongAdapter mAdapter;

    /**
     * The list view
     */
    private DragSortListView mListView;

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
     * Profile header
     */
    private ProfileTabCarousel mProfileTabCarousel;

    /**
     * The Id of the playlist the songs belong to
     */
    private long mPlaylistId;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PlaylistSongFragment() {
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
        // Create the adapter
        mAdapter = new ProfileSongAdapter(
                getActivity(),
                R.layout.edit_track_list_item,
                ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING
        );

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
        mListView = (DragSortListView)rootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Release any references to the recycled Views
        mListView.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        mListView.setOnCreateContextMenuListener(this);
        // Play the selected song
        mListView.setOnItemClickListener(this);
        // Set the drop listener
        mListView.setDropListener(this);
        // Set the swipe to remove listener
        mListView.setRemoveListener(this);
        // Quick scroll while dragging
        mListView.setDragScrollProfile(this);
        // To help make scrolling smooth
        mListView.setOnScrollListener(new VerticalScrollListener(null, mProfileTabCarousel, 0));
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
            mPlaylistId = arguments.getLong(Config.ID);
            getLoaderManager().initLoader(LOADER, arguments, this);
        }
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
        mSelectedPosition = info.position - 1;
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

        // Remove the song from playlist
        menu.add(GROUP_ID, FragmentMenuItems.REMOVE_FROM_PLAYLIST, Menu.NONE,
                getString(R.string.context_menu_remove_from_playlist));

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
                    final long playlistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), new long[] {
                        mSelectedId
                    }, playlistId);
                    return true;
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(getActivity(), mArtistName);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(getActivity(), mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    onDelete();
                    return true;
                case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                    onRemoveFromPlaylist();
                    return true;
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void onRemoveFromPlaylist() {
        mAdapter.remove(mSong);
        mAdapter.notifyDataSetChanged();
        MusicUtils.removeFromPlaylist(getActivity(), mSong.mSongId, mPlaylistId);
        getLoaderManager().restartLoader(LOADER, null, this);
    }

    private void onDelete() {
        DeleteDialog.newInstance(mSong.mSongName,
                new long[]{ mSelectedId },
                null).setOnDeleteCallback(new DeleteDialog.DeleteDialogCallback() {
            @Override
            public void onDelete(long[] id) {
                mAdapter.notifyDataSetChanged();
                getLoaderManager().restartLoader(LOADER, null, PlaylistSongFragment.this);
            }
        }).show(getFragmentManager(), "DeleteDialog");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        MusicUtils.playAllFromUserItemClick(getActivity(), mAdapter, position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistSongLoader(getActivity(), mPlaylistId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
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
        for (final Song song : data) {
            mAdapter.add(song);
        }
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
     * {@inheritDoc}
     */
    @Override
    public float getSpeed(final float w, final long t) {
        if (w > 0.8f) {
            return mAdapter.getCount() / 0.001f;
        } else {
            return 10.0f * w;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final int which) {
        mSong = mAdapter.getItem(which - 1);
        mAdapter.remove(mSong);
        mListView.invalidate();
        mAdapter.notifyDataSetChanged();
        final Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistId);
        getActivity().getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + mSong.mSongId,
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drop(final int from, final int to) {
        if (from == 0 || to == 0 || from == to) {
            mAdapter.notifyDataSetChanged();
            return;
        }

        int realFrom = from - 1;
        int realTo = to - 1;

        try {
            mSong = mAdapter.getItem(realFrom);
            mAdapter.remove(mSong);
            mAdapter.insert(mSong, realTo);
            mAdapter.notifyDataSetChanged();
            final ContentResolver resolver = getActivity().getContentResolver();
            MediaStore.Audio.Playlists.Members.moveItem(resolver, mPlaylistId, realFrom, realTo);
        } catch (Throwable t) {
        }
    }
}
