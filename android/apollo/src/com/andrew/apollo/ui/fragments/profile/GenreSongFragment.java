/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrew.apollo.ui.fragments.profile;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.content.Loader;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.loaders.GenreSongLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.Fragments;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;
import java.util.Objects;

/**
 * This class is used to display all of the songs from a particular playlist.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class GenreSongFragment extends ApolloFragment<ProfileSongAdapter, Song> {

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public GenreSongFragment() {
        super(Fragments.GENRE_SONG_PROFILE_FRAGMENT_GROUP_ID, Fragments.GENRE_SONG_PROFILE_FRAGMENT_LOADER_ID);
    }

    @Override
    protected ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(getActivity(), R.layout.list_item_simple);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        onSongItemClick(position);
    }

    @NonNull
    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new GenreSongLoader(getActivity(), Objects.requireNonNull(args).getLong(Config.ID));
    }
}
