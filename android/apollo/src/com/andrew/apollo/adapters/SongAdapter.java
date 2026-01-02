/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.ui.MusicViewHolder.DataHolder;
import com.andrew.apollo.ui.fragments.QueueFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Ref;

import java.util.List;

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

    @NonNull
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
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,() ->{
                    updateDataHolderAlbumId(getContext(), dataHolder, musicViewHolder, mImageFetcher);
                    SystemUtils.postToUIThread(() -> updateAlbumImage(getContext(), dataHolder, musicViewHolder, mImageFetcher));
                });
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
                    int highlightColor = ContextCompat.getColor(getContext(), com.frostwire.android.R.color.app_text_highlight);
                    musicViewHolder.mLineOne.get().setTextColor(highlightColor);
                    musicViewHolder.mLineOneRight.get().setTextColor(highlightColor);
                    musicViewHolder.mLineTwo.get().setTextColor(highlightColor);
                } else {
                    int primaryColor = ContextCompat.getColor(getContext(), com.frostwire.android.R.color.app_text_primary);
                    musicViewHolder.mLineOne.get().setTextColor(primaryColor);
                    musicViewHolder.mLineOneRight.get().setTextColor(primaryColor);
                    musicViewHolder.mLineTwo.get().setTextColor(primaryColor);
                }
            }
        }
        return convertView;
    }

    @SuppressWarnings("unused")
    private static void updateDataHolderAlbumId(Context context,
                                                DataHolder dataHolder,
                                                MusicViewHolder musicViewHolder,
                                                ImageFetcher imageFetcher) {
        if (dataHolder.mParentId == -1) {
            dataHolder.mParentId = MusicUtils.getAlbumIdForSong(context, dataHolder.mItemId);
        }
    }

    @SuppressWarnings("unused")
    private static void updateAlbumImage(Context context,
                                         DataHolder dataHolder,
                                         MusicViewHolder musicViewHolder,
                                         ImageFetcher imageFetcher) {
        if (dataHolder != null && dataHolder.mParentId != -1 && Ref.alive(musicViewHolder.mImage)) {
            ImageView imageView = musicViewHolder.mImage.get();
            imageFetcher.loadAlbumImage(dataHolder.mLineTwo, dataHolder.mLineOne, dataHolder.mParentId, imageView);
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
