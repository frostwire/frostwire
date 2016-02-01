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
import android.support.v4.content.Loader;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.ArtistAlbumAdapter;
import com.andrew.apollo.loaders.ArtistAlbumLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the albums from a particular artist.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class ArtistAlbumFragment extends ProfileFragment<ArtistAlbumAdapter, Album> {

    public ArtistAlbumFragment() {
        super(ARTIST_ALBUM_FRAGMENT_GROUP_ID,ARTIST_ALBUM_FRAGMENT_LOADER_ID);
    }

    /**
     * We override because the old implementation would put the VerticalScrollListener on column 1
     * this might go away after tests.
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        // To help make scrolling smooth
        mListView.setOnScrollListener(new VerticalScrollListener(mScrollableHeader,
                mProfileTabCarousel, 1));
        return rootView;
    }

    @Override
    ArtistAlbumAdapter createAdapter() {
        return new ArtistAlbumAdapter(getActivity(), R.layout.list_item_detailed_no_background);
    }

    @Override
    public Loader<List<Album>> onCreateLoader(final int id, final Bundle args) {
        return new ArtistAlbumLoader(getActivity(), args.getLong(Config.ID));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent,
                            final View view,
                            final int position,
                            final long id) {
        if (position == 0) {
            return;
        }
        mItem = mAdapter.getItem(position - 1);
        NavUtils.openAlbumProfile(getActivity(), mItem.mAlbumName, mItem.mArtistName, mItem.mAlbumId);
        getActivity().finish();
    }
}
