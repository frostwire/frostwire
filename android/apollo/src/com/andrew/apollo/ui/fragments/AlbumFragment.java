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

import androidx.loader.content.Loader;
import android.os.Bundle;

import com.andrew.apollo.adapters.AlbumAdapter;
import com.andrew.apollo.loaders.AlbumLoader;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the albums on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 */
public final class AlbumFragment extends BaseAlbumFragment {

    public AlbumFragment() {
        super(Fragments.ALBUM_FRAGMENT_GROUP_ID, Fragments.ALBUM_FRAGMENT_LOADER_ID);
    }

    @Override
    protected AlbumAdapter createAdapter() {
        int layout;
        if (isSimpleLayout()) {
            layout = R.layout.list_item_normal;
        } else if (isDetailedLayout()) {
            layout = R.layout.list_item_detailed_no_background;
        } else {
            layout = R.layout.grid_items_normal;
        }
        return new AlbumAdapter(getActivity(), layout);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.ALBUM_LAYOUT;
    }

    @Override
    public Loader<List<Album>> onCreateLoader(final int id, final Bundle args) {
        return new AlbumLoader(getActivity());
    }
}
