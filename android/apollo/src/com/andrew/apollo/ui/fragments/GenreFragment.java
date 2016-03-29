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

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.GenreAdapter;
import com.andrew.apollo.loaders.GenreLoader;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the genres on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public class GenreFragment extends ApolloFragment<GenreAdapter, Genre> {
    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public GenreFragment() {
        super(Fragments.GENRE_FRAGMENT_GROUP_ID, Fragments.GENRE_FRAGMENT_LOADER_ID);
    }

    @Override
    protected GenreAdapter createAdapter() {
        return new GenreAdapter(getActivity(), R.layout.list_item_simple);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        mItem = mAdapter.getItem(position);
        if (mItem != null) {
            // Create a new bundle to transfer the artist info
            final Bundle bundle = new Bundle();
            bundle.putLong(Config.ID, mItem.mGenreId);
            bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Genres.CONTENT_TYPE);
            bundle.putString(Config.NAME, mItem.mGenreName);

            // Create the intent to launch the profile activity
            final Intent intent = new Intent(getActivity(), ProfileActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);
        }
    }

    @Override
    public Loader<List<Genre>> onCreateLoader(final int id, final Bundle args) {
        return new GenreLoader(getActivity());
    }
}
