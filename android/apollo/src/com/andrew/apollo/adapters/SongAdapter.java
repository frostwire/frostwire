/*
 * Copyright (C) 2012 Andrew Neal
 *
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2013-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0
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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.QueueFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.util.Ref;

import java.util.List;

import static com.frostwire.android.util.Asyncs.async;

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

    private final Object mDataListLock = new Object();

    private final int itemsOffset;

    public SongAdapter(Context context, int mLayoutId) {
        this(context, mLayoutId, 0);
    }

    public SongAdapter(Context context, int mLayoutId, int itemsOffset) {
        super(context, mLayoutId, 0);
        this.itemsOffset = itemsOffset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        convertView = prepareMusicViewHolder(mLayoutId, getContext(), convertView, parent);
        final MusicViewHolder musicViewHolder = (MusicViewHolder) convertView.getTag();
        final DataHolder dataHolder = mData[position];
        updateFirstTwoArtistLines(musicViewHolder, dataHolder);
        if (mImageFetcher == null) {
            Log.w("warning", "ArtistAdapter has null image fetcher");
        }
        if (mImageFetcher != null && dataHolder != null && Ref.alive(musicViewHolder.mImage)) {
            if (dataHolder.mParentId == -1) {
                mImageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, R.drawable.list_item_audio_icon, musicViewHolder.mImage.get());
                async(getContext(),
                      SongAdapter::updateDataHolderAlbumId,
                      dataHolder, musicViewHolder, mImageFetcher,
                      SongAdapter::updateAlbumImage);
            } else {
                mImageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, dataHolder.mParentId, musicViewHolder.mImage.get());
            }
        }
        if (musicViewHolder != null) {
            if (Ref.alive(musicViewHolder.mLineThree)) {
                musicViewHolder.mLineThree.get().setVisibility(View.GONE);
            }
            if (dataHolder != null) {
                // Set each song name (line one)
                if (Ref.alive(musicViewHolder.mLineOne)) {
                    musicViewHolder.mLineOne.get().setText(dataHolder.mLineOne);
                }
                // Set the song duration (line one, right)
                if (Ref.alive(musicViewHolder.mLineOneRight)) {
                    musicViewHolder.mLineOneRight.get().setText(dataHolder.mLineOneRight);
                }
                // Set the artist name (line two)
                if (Ref.alive(musicViewHolder.mLineTwo)) {
                    musicViewHolder.mLineTwo.get().setText(dataHolder.mLineTwo);
                }
                if (MusicUtils.getCurrentAudioId() == dataHolder.mItemId) {
                    musicViewHolder.mLineOne.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
                    musicViewHolder.mLineOneRight.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
                    musicViewHolder.mLineTwo.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_highlight));
                } else {
                    musicViewHolder.mLineOne.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
                    musicViewHolder.mLineOneRight.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
                    musicViewHolder.mLineTwo.get().setTextColor(getContext().getResources().getColor(com.frostwire.android.R.color.app_text_primary));
                }
            }
        }
        return convertView;
    }

    @SuppressWarnings("unused")
    private static void updateDataHolderAlbumId(Context context, DataHolder dataHolder,
        MusicViewHolder musicViewHolder, ImageFetcher imageFetcher) {
        if (dataHolder.mParentId == -1) {
            dataHolder.mParentId = MusicUtils.getAlbumIdForSong(context, dataHolder.mItemId);
        }
    }

    @SuppressWarnings("unused")
    private static void updateAlbumImage(Context context, DataHolder dataHolder,
        MusicViewHolder musicViewHolder, ImageFetcher imageFetcher) {
        if (dataHolder != null && dataHolder.mParentId != -1) {
            imageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, dataHolder.mParentId, musicViewHolder.mImage.get());
        }
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
        final Song song = getItem(position);
        if (song == null) {
            return -1;
        }
        return song.mSongId;
    }

    @Override
    public void insert(Song object, int index) {
        synchronized (mDataListLock) {
            super.insert(object, index);
            mDataList.add(index, object);
        }
    }

    @Override
    public void remove(Song object) {
        synchronized (mDataListLock) {
            super.remove(object);
            mDataList.remove(object);
        }
    }

    public void setDataList(final List<Song> data) {
        mDataList.clear();
        if (data != null) {
            for (Song song : data) {
                if (song != null && song.mDuration > 0) {
                    mDataList.add(song);
                }
            }
        }
    }

    @Override
    public int getOffset() {
        return itemsOffset;
    }
}
