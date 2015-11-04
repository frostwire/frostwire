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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import com.frostwire.android.R;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.ui.MusicHolder;
import com.andrew.apollo.ui.MusicHolder.DataHolder;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;

/**
 * This {@link ArrayAdapter} is used to display all of the artists on a user's
 * device for {@link ArtistFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistAdapter extends ArrayAdapter<Artist> {

    /**
     * Number of views (ImageView and TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Image cache and image fetcher
     */
    private final ImageFetcher mImageFetcher;

    /**
     * Semi-transparent overlay
     */
    private final int mOverlay;

    /**
     * Used to cache the artist info
     */
    private DataHolder[] mData;

    /**
     * Loads line three and the background image if the user decides to.
     */
    private boolean mLoadExtraData = false;

    /**
     * Constructor of <code>ArtistAdapter</code>
     * 
     * @param context The {@link Context} to use.
     * @param layoutId The resource Id of the view to inflate.
     */
    public ArtistAdapter(final Activity context, final int layoutId) {
        super(context, 0);
        // Get the layout Id
        mLayoutId = layoutId;
        // Initialize the cache & image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(context);
        // Cache the transparent overlay
        mOverlay = context.getResources().getColor(R.color.list_item_background);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mLayoutId, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        // Set each artist name (line one)
        holder.mLineOne.get().setText(dataHolder.mLineOne);
        // Set the number of albums (line two)
        holder.mLineTwo.get().setText(dataHolder.mLineTwo);
        // Asynchronously load the artist image into the adapter
        mImageFetcher.loadArtistImage(dataHolder.mLineOne, holder.mImage.get());
        if (mLoadExtraData) {
            // Make sure the background layer gets set
            holder.mOverlay.get().setBackgroundColor(mOverlay);
            // Set the number of songs (line three)
            holder.mLineThree.get().setText(dataHolder.mLineThree);
            // Set the background image
            mImageFetcher.loadArtistImage(dataHolder.mLineOne, holder.mBackground.get());
            // Play the artist when the artwork is touched
            playArtist(holder.mImage.get(), position);
        }
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
            final Artist artist = getItem(i);

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

    /**
     * Starts playing an artist if the user touches the artist image in the
     * list.
     * 
     * @param artist The {@link ImageView} holding the aritst image
     * @param position The position of the artist to play.
     */
    private void playArtist(final ImageView artist, final int position) {
        artist.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                final long id = getItem(position).mArtistId;
                final long[] list = MusicUtils.getSongListForArtist(getContext(), id);
                MusicUtils.playAll(getContext(), list, 0, false);
            }
        });
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        clear();
        mData = null;
    }

    /**
     * @param pause True to temporarily pause the disk cache, false otherwise.
     */
    public void setPauseDiskCache(final boolean pause) {
        if (mImageFetcher != null) {
            mImageFetcher.setPauseDiskCache(pause);
        }
    }

    /**
     * @param artist The key used to find the cached artist to remove
     */
    public void removeFromCache(final Artist artist) {
        if (mImageFetcher != null) {
            mImageFetcher.removeFromCache(artist.mArtistName);
        }
    }

    /**
     * Flushes the disk cache.
     */
    public void flush() {
        mImageFetcher.flush();
    }

    /**
     * @param extra True to load line three and the background image, false
     *            otherwise.
     */
    public void setLoadExtraData(final boolean extra) {
        mLoadExtraData = extra;
    }
}
