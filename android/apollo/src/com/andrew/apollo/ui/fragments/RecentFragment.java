/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

import android.content.Loader;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.View;

import com.andrew.apollo.adapters.AlbumAdapter;
import com.andrew.apollo.loaders.RecentLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.List;

/**
 * This class is used to display all of the recently listened to albums by the
 * user.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class RecentFragment extends BaseAlbumFragment {

    public RecentFragment() {
        super(Fragments.RECENT_FRAGMENT_GROUP_ID, Fragments.RECENT_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Remove the album from the list
        menu.add(Fragments.RECENT_FRAGMENT_GROUP_ID, FragmentMenuItems.REMOVE_FROM_RECENT, Menu.NONE,
                getString(R.string.context_menu_remove_from_recent)).setIcon(R.drawable.contextmenu_icon_remove_transfer);
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
        return PreferenceUtils.RECENT_LAYOUT;
    }

    @Override
    public Loader<List<Album>> onCreateLoader(final int id, final Bundle args) {
        return new RecentLoader(getActivity());
    }

    /**
     * Sets up the list view
     */
    protected void initListView() {
        super.initListView();
        if (mAdapter != null) {
            mAdapter.setTouchPlay(true);
        }
    }
}
