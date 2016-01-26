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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.loaders.FavoritesLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs in {@link FavoritesStore}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class FavoriteFragment extends ProfileFragment<ProfileSongAdapter, Song> {

    /**
     * Used to keep context menu items from bleeding into other fragments
     */
    private static final int GROUP_ID = 6;

    /**
     * LoaderCallbacks identifier
     */
    private static final int LOADER = 0;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public FavoriteFragment() {
        super(6, 0);
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (!super.onContextItemSelected(item) && item.getGroupId() == GROUP_ID) {
            switch (item.getItemId()) {
                case FragmentMenuItems.REMOVE_FROM_FAVORITES:
                    onRemoveFromFavorites();
                    return true;
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(
                getActivity(),
                R.layout.list_item_simple,
                ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING
        );
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
        return new FavoritesLoader(getActivity());
    }

    private void onRemoveFromFavorites() {
        mAdapter.remove(mItem);
        mAdapter.notifyDataSetChanged();
        FavoritesStore.getInstance(getActivity()).removeItem(mSelectedId);
        getLoaderManager().restartLoader(LOADER, null, this);
    }
}
