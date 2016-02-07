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

package com.andrew.apollo.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.Ref;
import com.frostwire.android.R;

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
    private static final int VIEW_TYPE_COUNT = 1;

    /**
     * Constructor of <code>PlaylistAdapter</code>
     * 
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public PlaylistAdapter(final Context context, final int layoutId) {
        super(context, layoutId, 0);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).mPlaylistId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
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
                    getContext().getResources().getDimension(R.dimen.text_size_large));
            convertView.setTag(holder);
        } else {
            holder = (MusicViewHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Set each playlist name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        return convertView;
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

            // Build the data holder
            mData[i] = new DataHolder();
            // Playlist Id
            mData[i].mItemId = playlist.mPlaylistId;
            // Playlist names (line one)
            mData[i].mLineOne = playlist.mPlaylistName;
        }
    }
}
