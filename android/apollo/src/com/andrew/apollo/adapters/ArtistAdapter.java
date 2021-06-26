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

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.util.Ref;

/**
 * This {@link ArrayAdapter} is used to display all of the artists on a user's
 * device for {@link ArtistFragment}.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistAdapter extends ApolloFragmentAdapter<Artist> implements ApolloFragmentAdapter.Cacheable {

    /**
     * Number of views (ImageView and TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * Semi-transparent overlay
     */
    private final int mOverlayColor;

    /**
     * Constructor of <code>ArtistAdapter</code>
     *
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public ArtistAdapter(final Activity context, final int layoutId) {
        super(context, layoutId, 0);
        // Cache the transparent overlay
        mOverlayColor = context.getResources().getColor(R.color.list_item_background);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        convertView = prepareMusicViewHolder(mLayoutId, getContext(), convertView, parent);
        MusicViewHolder holder = (MusicViewHolder) convertView.getTag();
        final DataHolder dataHolder = mData[position];
        updateFirstTwoArtistLines(holder, dataHolder);
        if (mImageFetcher != null && dataHolder != null && Ref.alive(holder.mImage)) {
            // Asynchronously load the artist image into the adapter
            mImageFetcher.loadArtistImage(dataHolder.mLineOne, holder.mImage.get());
        }
        if (mLoadExtraData && mImageFetcher != null && holder != null) {
            if (Ref.alive(holder.mOverlay)) {
                // Make sure the background layer gets set
                holder.mOverlay.get().setBackgroundColor(mOverlayColor);
            }
            if (Ref.alive(holder.mLineThree)) {
                // Set the number of songs (line three)
                holder.mLineThree.get().setText(dataHolder.mLineThree);
            }
            if (Ref.alive(holder.mBackground)) {
                if (mLayoutId == R.layout.list_item_detailed_no_background) {
                    holder.mBackground.get().setBackground(null);
                    holder.mBackground.get().setBackgroundColor(convertView.getResources().getColor(R.color.app_background_white));
                } else {
                    // Set the background image
                    mImageFetcher.loadArtistImage(dataHolder.mLineOne, holder.mBackground.get());
                }
            }

            if (Ref.alive(holder.mImage)) {
                // Play the artist when the artwork is touched
                initArtistPlayOnClick(holder.mImage.get(), position);
            }
        }
        return convertView;
    }

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
            final Artist artist = getItem(i);

            if (artist != null) {
                // Build the data holder
                mData[i] = new DataHolder();
                // Artist Id
                mData[i].mItemId = artist.mArtistId;
                // Artist names (line one)
                mData[i].mLineOne = artist.mArtistName;
                // Number of albums (line two)
                mData[i].mLineTwo = MusicUtils.makeLabel(getContext(),
                        R.plurals.Nalbums, artist.mAlbumNumber);
                // Number of songs (line three)
                mData[i].mLineThree = MusicUtils.makeLabel(getContext(),
                        R.plurals.Nsongs, artist.mSongNumber);
            }
        }
    }

    /**
     * Starts playing an artist if the user touches the artist image in the
     * list.
     *
     * @param artist The {@link ImageView} holding the aritst image
     * @param position The position of the artist to play.
     */
    private void initArtistPlayOnClick(final ImageView artist, final int position) {
        artist.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                final long id = getItem(position).mArtistId;
                final long[] list = MusicUtils.getSongListForArtist(getContext(), id);
                MusicUtils.playFDs(list, 0, MusicUtils.isShuffleEnabled());
            }
        });
    }

    @Override
    public long getItemId(int position) {
        try {
            return getItem(position).mArtistId;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public int getOffset() {
        return 0;
    }
}
