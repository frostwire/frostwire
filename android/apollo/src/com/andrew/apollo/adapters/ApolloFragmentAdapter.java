/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.util.Ref;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gubatron on 1/26/16 on a plane.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class ApolloFragmentAdapter<I> extends ArrayAdapter<I> {

    public interface Cacheable {
        void buildCache();
    }

    /**
     * The header view
     */
    static final int ITEM_VIEW_TYPE_HEADER = 0;

    /**
     * * The data in the list.
     */
    static final int ITEM_VIEW_TYPE_MUSIC = 1;


    /**
     * Used to set the size of the data in the adapter
     */
    List<I> mDataList = new ArrayList<>();

    /**
     * Image cache and image fetcher
     */
    final ImageFetcher mImageFetcher;

    /**
     * Used to cache the album info
     */
    MusicViewHolder.DataHolder[] mData;

    /**
     * Loads line three and the background image if the user decides to.
     */
    boolean mLoadExtraData = false;

    final int mLayoutId;

    ApolloFragmentAdapter(Context context, int mLayoutId, int textViewResourceId) {
        super(context, textViewResourceId);
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
        if (this instanceof Cacheable) {
            mData = null;
        }
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
        mDataList.clear();
        mDataList = data;
    }

    /**
     * Starts playing an album if the user touches the artwork in the list.
     *
     * @param album    The {@link ImageView} holding the album
     * @param position The position of the album to play.
     */
    protected void initAlbumPlayOnClick(final ImageView album, final int position) {
        if (album != null) {
            album.setOnClickListener(v -> {
                final long id = getItemId(position);
                final long[] list = MusicUtils.getSongListForAlbum(getContext(), id);
                MusicUtils.playFDs(list, 0, MusicUtils.isShuffleEnabled());
            });
        }
    }

    void updateFirstTwoArtistLines(MusicViewHolder holder, MusicViewHolder.DataHolder dataHolder) {
        if (holder != null && dataHolder != null) {
            // Set each artist name (line one)
            if (Ref.alive(holder.mLineOne)) {
                holder.mLineOne.get().setText(dataHolder.mLineOne);
            }

            // Set the number of albums (line two)
            if (Ref.alive(holder.mLineTwo)) {
                holder.mLineTwo.get().setText(dataHolder.mLineTwo);
            }
        }
    }

    static View prepareMusicViewHolder(int mLayoutId, Context context, View convertView, final ViewGroup parent) {
        MusicViewHolder holder = null;
        if (convertView != null) {
            holder = (MusicViewHolder) convertView.getTag();
        } else {
            try {
                convertView = LayoutInflater.from(context).inflate(mLayoutId, parent, false);
            } catch (Throwable ignored) {
            }
        }
        if (holder == null && convertView != null) {
            holder = new MusicViewHolder(convertView);
        }
        if (convertView != null) {
            convertView.setTag(holder);
        }
        return convertView;
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
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * Some views have multiple view types.
     * If the View type for the adapter is a ITEM_VIEW_TYPE_HEADER
     * Our elements are actually displayed starting with an offset of 1 on the listView, gridView.
     */
    public int getOffset() {
        int offset = 0;
        if (getItemViewType(0) == ITEM_VIEW_TYPE_HEADER) {
            offset = 1;
        }
        return offset;
    }

    /**
     * The adapter's element count
     */
    @Override
    public int getCount() {
        int count = super.getCount();
        if (mDataList != null) {
            int size = mDataList.size();
            count = size == 0 ? 0 : size + getOffset();
        }
        return count;
    }

    /**
     * @param position - The ACTUAL position in the model container.
     *                 If you're using an offset based on a list view that has a header element at 0,
     *                 you must subtract to the position you might have.
     * @return The element at the indexed position in the model container (not the view). null if position is out of bounds.
     */
    public I getItem(int position) {
        if (position < 0 || (mDataList != null && position >= mDataList.size())) {
            return null;
        }
        if (mDataList != null) {
            return mDataList.get(position);
        } else {
            try {
                return super.getItem(position);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /**
     * @param position The ACTUAL position in the model container.
     *                 If you have an extra header element, subtract the offset before invoking this method.
     * @return the object id. if out of bound, returns -1.
     */
    @Override
    public long getItemId(int position) {
        if (position < 0) {
            return -1;
        }

        if (this instanceof Cacheable && mData != null) {
            if (position < mData.length) {
                return mData[position].mItemId;
            } else {
                return -1;
            }
        }

        if (mDataList != null && !mDataList.isEmpty() && position < mDataList.size()) {
            I item = mDataList.get(position);
            long id = -1;
            if (item instanceof Song) {
                id = ((Song) item).mSongId;
            } else if (item instanceof Album) {
                id = ((Album) item).mAlbumId;
            } else if (item instanceof Playlist) {
                id = ((Playlist) item).mPlaylistId;
            } else if (item instanceof Genre) {
                id = ((Genre) item).mGenreId;
            } else if (item instanceof Artist) {
                id = ((Artist) item).mArtistId;
            }
            return id;
        }

        return -1;
    }

    /**
     * @param extra True to load line three and the background image, false
     *              otherwise.
     */
    public void setLoadExtraData(final boolean extra) {
        mLoadExtraData = extra;
    }
}
