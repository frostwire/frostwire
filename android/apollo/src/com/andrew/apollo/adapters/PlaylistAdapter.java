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

package com.andrew.apollo.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.menu.CreateNewPlaylistMenuAction;

/**
 * This {@link ArrayAdapter} is used to display all of the playlists on a user's
 * device for {@link PlaylistFragment}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlaylistAdapter extends ApolloFragmentAdapter<Playlist> implements ApolloFragmentAdapter.Cacheable {

    /**
     * Number of views (TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

    private static final int VIEW_TYPE_NEW_PLAYLIST = 0;
    private static final int VIEW_TYPE_PLAYLIST_ITEM = 1;

    /**
     * Constructor of <code>PlaylistAdapter</code>
     *
     * @param context  The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public PlaylistAdapter(final Context context, final int layoutId) {
        super(context, layoutId, 0);
    }

    @Override
    public long getItemId(int position) {
        final Playlist item = getItem(position);
        return item != null ? item.mPlaylistId : -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        if (position ==  0) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.new_playlist_list_item, null);
                convertView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        onNewPlaylistItemClick();
                    }
                });
            }
        } else if (position >= 1) {
            // Recycle ViewHolder's items
            MusicViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(mLayoutId, parent, false);
                holder = new MusicViewHolder(convertView);
                // Hide the second and third lines of text
                holder.mLineTwo.get().setVisibility(View.GONE);
                holder.mLineThree.get().setVisibility(View.GONE);
                // Make line one slightly larger
                holder.mLineOne.get().setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getContext().getResources().getDimension(R.dimen.text_size_medium));
                holder.mLineOne.get().setTypeface(Typeface.SANS_SERIF);
                convertView.setTag(holder);
            } else {
                holder = (MusicViewHolder) convertView.getTag();
            }

            // Retrieve the data holder
            final DataHolder dataHolder = mData[position];

            // Set each playlist name (line one)
            holder.mLineOne.get().setText(dataHolder.mLineOne);
        }
        return convertView;
    }

    private void onNewPlaylistItemClick() {
        CreateNewPlaylistMenuAction createPlaylistAction = new CreateNewPlaylistMenuAction(getContext(), null);
        createPlaylistAction.onClick();
        MusicUtils.refresh();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_NEW_PLAYLIST : VIEW_TYPE_PLAYLIST_ITEM;
    }

    /**
     * Method used to cache the data used to populate the list or grid. The idea
     * is to cache everything before {@code #getView(int, View, ViewGroup)} is
     * called.
     */
    public void buildCache() {
        mData = new DataHolder[getCount()];
        for (int i = 0; i < getCount(); i++) {
            // Build the artist
            final Playlist playlist = getItem(i);
            if (playlist == null) {
                continue;
            }

            // Build the data holder
            mData[i] = new DataHolder();
            // Playlist Id
            mData[i].mItemId = playlist.mPlaylistId;
            // Playlist names (line one)
            mData[i].mLineOne = playlist.mPlaylistName;
        }
    }

    @Override
    public int getOffset() {
        return 0;
    }
}
