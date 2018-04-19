/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrew.apollo.cache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;

import com.frostwire.android.R;
import com.frostwire.android.util.ImageLoader;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a {@link Bitmap} to an {@link ImageView}. It handles things like using a
 * memory and disk cache, running the work in a background thread and setting a
 * placeholder image.
 */
public abstract class ImageWorker {

    /**
     * Default album art
     */
    private final Bitmap mDefault;

    /**
     * The Context to use
     */
    Context mContext;

    /**
     * Disk and memory caches
     */
    ImageCache mImageCache;

    /**
     * Constructor of <code>ImageWorker</code>
     *
     * @param context The {@link Context} to use
     */
    ImageWorker(final Context context) {
        mContext = context.getApplicationContext();
        Resources mResources = mContext.getResources();
        BitmapDrawable mDefaultArtwork;
        if (mResources != null) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) mResources.getDrawable(R.drawable.default_artwork);
            if (bitmapDrawable != null) {
                mDefault = bitmapDrawable.getBitmap();
                mDefaultArtwork = new BitmapDrawable(mResources, mDefault);
                // No filter and no dither makes things much quicker
                mDefaultArtwork.setFilterBitmap(false);
                mDefaultArtwork.setDither(false);
            } else {
                mDefault = null;
            }
        } else {
            mDefault = null;
        }
    }

    /**
     * Set the {@link ImageCache} object to use with this ImageWorker.
     *
     * @param cacheCallback new {@link ImageCache} object.
     */
    public void setImageCache(final ImageCache cacheCallback) {
        mImageCache = cacheCallback;
    }

    /**
     * flush() is called to synchronize up other methods that are accessing the
     * cache first
     */
    public void flush() {
        if (mImageCache != null) {
            mImageCache.flush();
        }
    }

    /**
     * @return The default artwork
     */
    public Bitmap getDefaultArtwork() {
        return mDefault;
    }

    /**
     * Called to fetch the artist or album art.
     *
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    void loadImage(final String key, final String artistName,
            final long albumId, final ImageView imageView, final ImageType imageType) {
        if (key == null || mImageCache == null || imageView == null) {
            return;
        }

        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        if (ImageType.ALBUM.equals(imageType)) {
            final Uri albumArtUri = ImageLoader.getAlbumArtUri(albumId);
            loader.load(albumArtUri, imageView, R.drawable.default_artwork);
        } else if (ImageType.ARTIST.equals(imageType)) {
            final Uri artistArtUri = ImageLoader.getArtistArtUri(artistName);
            loader.load(artistArtUri, imageView, R.drawable.default_artwork);
        }
    }

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM
    }
}
