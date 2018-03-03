/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
