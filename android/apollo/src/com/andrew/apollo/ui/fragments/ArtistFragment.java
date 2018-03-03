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

import android.app.Fragment;
import android.content.Loader;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.andrew.apollo.adapters.ArtistAdapter;
import com.andrew.apollo.loaders.ArtistLoader;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

import static com.andrew.apollo.utils.PreferenceUtils.ARTIST_LAYOUT;

/**
 * This class is used to display all of the artists on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class ArtistFragment extends ApolloFragment<ArtistAdapter, Artist> {

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ArtistFragment() {
        super(Fragments.ARTIST_FRAGMENT_GROUP_ID, Fragments.ARTIST_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        if (isAdded()) {
            getLoaderManager().initLoader(Fragments.ARTIST_FRAGMENT_LOADER_ID, savedInstanceState, this);
        }
    }

    @Override
    protected ArtistAdapter createAdapter() {
        int layout;
        if (isSimpleLayout()) {
            layout = R.layout.list_item_normal;
        } else if (isDetailedLayout()) {
            layout = R.layout.list_item_detailed_no_background;
        } else {
            layout = R.layout.grid_items_normal;
        }
        return new ArtistAdapter(getActivity(), layout);
    }

    @Override
    protected String getLayoutTypeName() {
        return ARTIST_LAYOUT;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        mItem = mAdapter.getItem(position);
        long[] tracks = MusicUtils.getSongListForArtist(getActivity(), mItem.mArtistId);
        NavUtils.openArtistProfile(getActivity(), mItem.mArtistName, tracks);
    }

    @Override
    public Loader<List<Artist>> onCreateLoader(final int id, final Bundle args) {
        return new ArtistLoader(getActivity());
    }

    /**
     * @return The position of an item in the list or grid based on the name of
     * the currently playing artist.
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

    @Override
    protected boolean isSimpleLayout() {
        return PreferenceUtils.getInstance().isSimpleLayout(getLayoutTypeName());
    }
}
