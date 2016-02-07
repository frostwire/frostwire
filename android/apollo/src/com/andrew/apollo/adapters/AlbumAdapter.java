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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.Ref;
import com.frostwire.android.R;
import com.frostwire.logging.Logger;

/**
 * This {@link ArrayAdapter} is used to display all of the albums on a user's
 * device for RecentsFragment and AlbumsFragment.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class AlbumAdapter extends ApolloFragmentAdapter<Album> implements ApolloFragmentAdapter.Cacheable {

    static Logger LOGGER = Logger.getLogger(AlbumAdapter.class);

    /**
     * Number of views (ImageView and TextView)
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * Semi-transparent overlay
     */
    private final int mOverlay;

    /**
     * Sets the album art on click listener to start playing them album when
     * touched.
     */
    private boolean mTouchPlay = false;

    /**
     * Constructor of <code>AlbumAdapter</code>
     */
    public AlbumAdapter(final Activity context, final int layoutId) {
        super(context, layoutId, 0);
        // Cache the transparent overlay
        mOverlay = context.getResources().getColor(R.color.list_item_background);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Recycle ViewHolder's items
        MusicViewHolder holder = prepareMusicViewHolder(mLayoutId, getContext(), convertView, parent);
        // Retrieve the data holder
        final DataHolder dataHolder = mData[position];

        if (holder != null && dataHolder != null) {
            // Set each album name (line one)
            if (Ref.alive(holder.mLineOne)) {
                holder.mLineOne.get().setText(dataHolder.mLineOne);
            }
            // Set the artist name (line two)
            if (Ref.alive(holder.mLineTwo)) {
                holder.mLineTwo.get().setText(dataHolder.mLineTwo);
            }
        }

        if (mImageFetcher == null) {
            LOGGER.warn("ArtistAdapter has null image fetcher");
        }

        if (mImageFetcher != null && dataHolder != null && Ref.alive(holder.mImage)) {
            // Asynchronously load the album images into the adapter
            mImageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, dataHolder.mItemId, holder.mImage.get());
        }

        // List view only items
        if (mLoadExtraData && holder != null && dataHolder != null) {
            if (Ref.alive(holder.mOverlay)) {
                // Make sure the background layer gets set
                holder.mOverlay.get().setBackgroundColor(mOverlay);
            }

            if (Ref.alive(holder.mLineThree)) {
                // Set the number of songs (line three)
                holder.mLineThree.get().setText(dataHolder.mLineThree);
            }

            if (mImageFetcher != null && Ref.alive(holder.mBackground)) {
                // Asynchronously load the artist image on the background view
                mImageFetcher.loadArtistImage(dataHolder.mLineTwo, holder.mBackground.get());
            }
        }

        if (mTouchPlay && holder != null && Ref.alive(holder.mImage)) {
            // Play the album when the artwork is touched
            initAlbumPlayOnClick(holder.mImage.get(), position);
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
            // Build the album
            final Album album = getItem(i);

            // Build the data holder
            mData[i] = new DataHolder();
            // Album Id
            mData[i].mItemId = album.mAlbumId;
            // Album names (line one)
            mData[i].mLineOne = album.mAlbumName;
            // Album artist names (line two)
            mData[i].mLineTwo = album.mArtistName;
            // Number of songs for each album (line three)
            mData[i].mLineThree = MusicUtils.makeLabel(getContext(),
                    R.plurals.Nsongs, album.mSongNumber);
        }
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
     * @param album The key used to find the cached album to remove
     */
    public void removeFromCache(final Album album) {
        if (mImageFetcher != null) {
            mImageFetcher.removeFromCache(
                    ImageFetcher.generateAlbumCacheKey(album.mAlbumName, album.mArtistName));
        }
    }

    /**
     * @param extra True to load line three and the background image, false
     *            otherwise.
     */
    public void setLoadExtraData(final boolean extra) {
        mLoadExtraData = extra;
        setTouchPlay(true);
    }

    /**
     * @param play True to play the album when the artwork is touched, false
     *            otherwise.
     */
    public void setTouchPlay(final boolean play) {
        mTouchPlay = play;
    }

    @Override
    public long getItemId(int position) {
        try {
            return getItem(position).mAlbumId;
        } catch (Throwable t) {
            return -1;
        }
    }
}
