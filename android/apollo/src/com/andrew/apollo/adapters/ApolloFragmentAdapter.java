/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicHolder;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.Lists;
import com.andrew.apollo.utils.MusicUtils;

import java.util.List;

/**
 * Created by gubatron on 1/26/16 on a plane.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class ApolloFragmentAdapter<I> extends ArrayAdapter<I> {

    /**
     * The resource Id of the layout to inflate
     */
    protected final int mLayoutId;

    /**
     * Used to set the size of the data in the adapter
     */
    protected List<I> mDataList = Lists.newArrayList();

    /**
     * Image cache and image fetcher
     */
    protected final ImageFetcher mImageFetcher;

    /**
     * Used to cache the album info
     */
    protected MusicHolder.DataHolder[] mData;

    public ApolloFragmentAdapter(Context context, int mLayoutId) {
        super(context, mLayoutId);
        this.mLayoutId = mLayoutId;
        if (context instanceof Activity) {
            mImageFetcher = ApolloUtils.getImageFetcher((Activity) context);
        } else {
            mImageFetcher = null;
        }
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        mData = null;
        mDataList.clear();
        clear();
    }

    public void flush() {
        if (mImageFetcher != null) {
            mImageFetcher.flush();
        }
    }

    /**
     * @param data The {@link List} used to return the count for the adapter.
     */
    public void setDataList(final List<I> data) {
        mDataList = data;
    }

    /**
     * Starts playing an album if the user touches the artwork in the list.
     *
     * @param album The {@link ImageView} holding the album
     * @param position The position of the album to play.
     */
    protected void playAlbum(final ImageView album, final int position) {
        if (album != null) {
            album.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final long id = getItemId(position);
                    final long[] list = MusicUtils.getSongListForAlbum(getContext(), id);
                    MusicUtils.playAll(list, 0, false);
                }
            });
        }
    }

    public static MusicHolder prepareMusicHolder(int mLayoutId, Context context, View convertView, final ViewGroup parent) {
        MusicHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(mLayoutId, parent, false);
            holder = new MusicHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }
        return holder;
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
    public int getCount() {
        final int size = mDataList.size();
        return size == 0 ? 0 : size + 1;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }

        int realPosition = position-1;
        if (mData != null && realPosition < mData.length) {
            return mData[realPosition].mItemId;
        } else if (!mDataList.isEmpty() && position < mDataList.size()) {
            I item = mDataList.get(realPosition);
            long id=-1;
            if (item instanceof Song) {
                id = ((Song) item).mSongId;
            } else if (item instanceof Album) {
                id = ((Album) item).mAlbumId;
            }
            return id;
        }

        return - 1;
    }
}
