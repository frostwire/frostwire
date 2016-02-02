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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import com.andrew.apollo.adapters.ArtistAdapter;
import com.andrew.apollo.loaders.ArtistLoader;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.frostwire.android.R;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.List;

import static com.andrew.apollo.utils.PreferenceUtils.ARTIST_LAYOUT;

/**
 * This class is used to display all of the artists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistFragment extends ApolloFragment<ArtistAdapter, Artist> {

    //private static Logger LOGGER = Logger.getLogger(ArtistFragment.class);

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ArtistFragment() {
        super(Fragments.ARTIST_FRAGMENT_GROUP_ID, Fragments.ARTIST_FRAGMENT_LOADER_ID);
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
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        getLoaderManager().initLoader(Fragments.ARTIST_FRAGMENT_LOADER_ID, null, this);
    }

    @Override
    protected ArtistAdapter createAdapter() {
        int layout;
        if (isSimpleLayout()) {
            layout = R.layout.list_item_simple;
        } else if (isDetailedLayout()) {
            layout = R.layout.list_item_detailed;
        } else {
            layout = R.layout.grid_items_normal;
        }
        return new ArtistAdapter(getActivity(), layout);
    }

    @Override
    protected String getLayoutTypeName() {
        return ARTIST_LAYOUT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        mItem = mAdapter.getItem(position);
        NavUtils.openArtistProfile(getActivity(), mItem.mArtistName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<List<Artist>> onCreateLoader(final int id, final Bundle args) {
        return new ArtistLoader(getActivity());
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
}
