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

package com.andrew.apollo.ui.fragments.profile;

import android.app.Fragment;
import androidx.loader.content.Loader;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;

import com.andrew.apollo.adapters.ProfileSongAdapter;
import com.andrew.apollo.loaders.FavoritesLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.ui.fragments.Fragments;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the songs in {@link FavoritesStore}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class FavoriteFragment extends ApolloFragment<ProfileSongAdapter, Song> {

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public FavoriteFragment() {
        super(Fragments.FAVORITE_PROFILE_FRAGMENT_GROUP_ID, Fragments.FAVORITE_PROFILE_FRAGMENT_LOADER_ID);
    }

    @Override
    protected ProfileSongAdapter createAdapter() {
        return new ProfileSongAdapter(getActivity(), R.layout.list_item_simple, ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        onSongItemClick(position);
    }

    @Override
    public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
        return new FavoritesLoader(getActivity());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.removeItem(FragmentMenuItems.ADD_TO_FAVORITES);
        menu.add(Fragments.FAVORITE_PROFILE_FRAGMENT_GROUP_ID, FragmentMenuItems.REMOVE_FROM_FAVORITES, Menu.NONE, getString(R.string.remove_from_favorites)).setIcon(R.drawable.contextmenu_icon_remove_transfer);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    public void onMetaChanged() {
        // skip
    }
}
