/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 *             Jose Molina (@votaguz)
 * Copyright (c) 2013-2018, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments.profile;

import android.content.Loader;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.loaders.LastAddedLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.Fragments;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs the user put on their device
 * within the last four weeks.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class LastAddedFragment extends ApolloFragment<ProfileSongAdapter, Song> {

    public LastAddedFragment() {
        super(Fragments.LAST_ADDED_PROFILE_FRAGMENT_GROUP_ID, Fragments.LAST_ADDED_PROFILE_FRAGMENT_LOADER_ID);
    }

    protected ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(
                getActivity(),
                R.layout.list_item_simple,
                ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING
        );
    }

    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new LastAddedLoader(getActivity());
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        onSongItemClick(position);
    }

    @Override
    public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
        super.onLoadFinished(loader, data);
        if (data.isEmpty()) {
            // Set the empty text
            if (mRootView != null) {
                final TextView empty = mRootView.findViewById(R.id.empty);
                empty.setText(getString(R.string.empty_last_added));
                mListView.setEmptyView(empty);
            }
        }
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }
}
