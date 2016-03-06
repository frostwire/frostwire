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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.QueueFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.Ref;

/**
 * This {@link ArrayAdapter} is used to display all of the songs on a user's
 * device for {@link SongFragment}. It is also used to show the queue in
 * {@link QueueFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SongAdapter extends ApolloFragmentAdapter<Song> implements ApolloFragmentAdapter.Cacheable {

    /**
     * Number of views (TextView)
     */
    private static final int VIEW_TYPE_COUNT = 1;

    public SongAdapter(Context context, int mLayoutId) {
        super(context, mLayoutId, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        convertView = prepareMusicViewHolder(mLayoutId, getContext(), convertView, parent);
        MusicViewHolder holder = (MusicViewHolder) convertView.getTag();
        if (holder != null && Ref.alive(holder.mLineThree)) {
            holder.mLineThree.get().setVisibility(View.GONE);
        }
        final DataHolder dataHolder = mData[position];
        // Set each song name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        // Set the song duration (line one, right)
        holder.mLineOneRight.get().setText(dataHolder.mLineOneRight);
        // Set the artist name (line two)
        holder.mLineTwo.get().setText(dataHolder.mLineTwo);
        if (MusicUtils.getCurrentAudioId() == dataHolder.mItemId) {
            holder.mLineOne.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
            holder.mLineOneRight.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
            holder.mLineTwo.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
        } else {
            holder.mLineOne.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
            holder.mLineOneRight.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
            holder.mLineTwo.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
        }
        return convertView;
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
            // Build the song
            final Song song = getItem(i);

            if (song == null) {
                continue;
            }

            // Build the data holder
            mData[i] = new DataHolder();
            // Song Id
            mData[i].mItemId = song.mSongId;
            // Song names (line one)
            mData[i].mLineOne = song.mSongName;
            // Song duration (line one, right)
            mData[i].mLineOneRight = MusicUtils.makeTimeString(getContext(), song.mDuration);
            // Artist name (line two)
            mData[i].mLineTwo = song.mArtistName;
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).mSongId;
    }

}
