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

package com.andrew.apollo.ui.fragments;

import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.andrew.apollo.adapters.SongAdapter;
import com.andrew.apollo.dragdrop.DragSortListView;
import com.andrew.apollo.dragdrop.DragSortListView.DragScrollProfile;
import com.andrew.apollo.dragdrop.DragSortListView.DropListener;
import com.andrew.apollo.dragdrop.DragSortListView.RemoveListener;
import com.andrew.apollo.loaders.NowPlayingCursor;
import com.andrew.apollo.loaders.QueueLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs in the queue.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class QueueFragment extends ApolloFragment<SongAdapter, Song>
        implements DropListener, RemoveListener, DragScrollProfile {

    public QueueFragment() {
        super(Fragments.QUEUE_FRAGMENT_GROUP_ID, Fragments.QUEUE_FRAGMENT_LOADER_ID);
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
        return mRootView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (inflater != null) {
            inflater.inflate(R.menu.player_queue, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_player_save_queue:
                NowPlayingCursor queue = (NowPlayingCursor) QueueLoader
                        .makeQueueCursor(getActivity());
                CreateNewPlaylist.getInstance(MusicUtils.getSongListForCursor(queue)).show(
                        getFragmentManager(), "CreatePlaylist");
                queue.close();
                return true;
            case R.id.menu_player_clear_queue:
                long currentAudioId = MusicUtils.getCurrentAudioId();
                MusicUtils.clearQueue();
                MusicUtils.playFDs(new long[] { currentAudioId }, 0, MusicUtils.isShuffleEnabled() );
                refreshQueue();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected SongAdapter createAdapter() {
        return new SongAdapter(getActivity(), R.layout.edit_track_list_item);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    protected boolean isSimpleLayout() {
        return true;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        // When selecting a track from the queue, just jump there instead of
        // reloading the queue. This is both faster, and prevents accidentally
        // dropping out of party shuffle.
        MusicUtils.setQueuePosition(position);
        notifyAdapterDataSetChanged();
    }

    public void notifyAdapterDataSetChanged() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new QueueLoader(getActivity());
    }

    public void remove(final int which) {
        if (mAdapter != null) {
            mItem = mAdapter.getItem(which);
            mAdapter.remove(mItem);
            MusicUtils.removeTrack(mItem.mSongId);
            // Build the cache
            mAdapter.buildCache();
            mAdapter.notifyDataSetChanged();
        }
    }

    public void drop(final int from, final int to) {
        if (from == to) {
            mAdapter.notifyDataSetChanged();
            return;
        }

        try {
            mItem = mAdapter.getItem(from);
            int count = mAdapter.getCount();
            mAdapter.remove(mItem);
            int adjustedTo = (to >= count) ? count-1 : to;
            mAdapter.insert(mItem, adjustedTo);
            mAdapter.notifyDataSetChanged();
            MusicUtils.moveQueueItem(from, adjustedTo);
            mAdapter.buildCache();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called to restart the loader callbacks
     */
    public void refreshQueue() {
        if (isAdded()) {
            getLoaderManager().restartLoader(Fragments.QUEUE_FRAGMENT_LOADER_ID, null, this);
        }
    }

    @Override
    public float getSpeed(final float w, final long t) {
        if (w > 0.8f) {
            return mAdapter.getCount() / 0.001f;
        } else {
            return 10.0f * w;
        }
    }
}
