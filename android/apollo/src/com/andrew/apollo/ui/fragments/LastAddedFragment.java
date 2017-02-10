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

import android.os.Bundle;
import android.app.Fragment;
import android.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import com.andrew.apollo.adapters.SongAdapter;
import com.andrew.apollo.loaders.LastAddedLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public class LastAddedFragment extends ApolloFragment<SongAdapter, Song> {
    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public LastAddedFragment() {
        super(Fragments.LAST_ADDED_FRAGMENT_GROUP_ID, Fragments.LAST_ADDED_FRAGMENT_LOADER_ID);
    }

    @Override
    protected SongAdapter createAdapter() {
        return new SongAdapter(getActivity(), R.layout.list_item_simple);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        if (mAdapter != null) {
            MusicUtils.playAllFromUserItemClick(mAdapter, position);
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new LastAddedLoader(getActivity());
    }

    @Override
    protected boolean isSimpleLayout() {
        return true;
    }

    @Override
    public void onMetaChanged() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }
}
