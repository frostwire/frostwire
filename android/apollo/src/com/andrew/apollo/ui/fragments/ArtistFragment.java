/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.ui.fragments;

import androidx.fragment.app.Fragment;
import androidx.loader.content.Loader;
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
