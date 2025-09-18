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

import android.view.View;
import android.widget.AdapterView;

import com.andrew.apollo.adapters.AlbumAdapter;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class BaseAlbumFragment extends ApolloFragment<AlbumAdapter, Album> {

    public BaseAlbumFragment(int groupId, int loaderId) {
        super(groupId, loaderId);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        mItem = mAdapter.getItem(position);
        NavUtils.openAlbumProfile(getActivity(),
                mItem.mAlbumName,
                mItem.mArtistName,
                mItem.mAlbumId,
                MusicUtils.getSongListForAlbum(getActivity(), mItem.mAlbumId));
    }

    protected boolean isSimpleLayout() {
        return PreferenceUtils.getInstance().isSimpleLayout(getLayoutTypeName());
    }
}
