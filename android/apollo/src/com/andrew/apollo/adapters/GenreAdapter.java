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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.GenreFragment;
import com.andrew.apollo.utils.Ref;
import com.frostwire.android.R;

/**
 * This {@link ArrayAdapter} is used to display all of the genres on a user's
 * device for {@link GenreFragment} .
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class GenreAdapter extends ApolloFragmentAdapter<Genre> implements ApolloFragmentAdapter.Cacheable {

    /**
     * Number of views (TextView)
     */
    private static final int VIEW_TYPE_COUNT = 1;

    public GenreAdapter(final Context context, final int layoutId) {
        super(context, layoutId);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).mGenreId;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicViewHolder holder = prepareMusicViewHolder(mLayoutId, getContext(), convertView, parent);
        if (holder != null) {
            if (Ref.alive(holder.mLineTwo)) {
                holder.mLineTwo.get().setVisibility(View.GONE);
            }
            if (Ref.alive(holder.mLineThree)) {
                holder.mLineThree.get().setVisibility(View.GONE);
            }
            if (Ref.alive(holder.mLineOne)) {
                // Make line one slightly larger
                holder.mLineOne.get().setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getContext().getResources().getDimension(R.dimen.text_size_large));
            }
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Set each genre name (line one)
        if (holder != null && Ref.alive(holder.mLineOne)) {
            holder.mLineOne.get().setText(dataHolder.mLineOne);
        }
        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

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
            final Genre genre = getItem(i);

            // Build the data holder
            mData[i] = new DataHolder();
            // Genre Id
            mData[i].mItemId = genre.mGenreId;
            // Genre names (line one)
            mData[i].mLineOne = genre.mGenreName;
        }
    }
}
