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

package com.andrew.apollo.cache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.frostwire.android.R;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.ThemeUtils;
import com.frostwire.android.util.ImageLoader;

import java.lang.ref.WeakReference;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a {@link Bitmap} to an {@link ImageView}. It handles things like using a
 * memory and disk cache, running the work in a background thread and setting a
 * placeholder image.
 */
public abstract class ImageWorker {

    /**
     * Default transition drawable fade time
     */
    private static final int FADE_IN_TIME = 200;

    /**
     * Default artwork
     */
    private final BitmapDrawable mDefaultArtwork;

    /**
     * The resources to use
     */
    private final Resources mResources;

    /**
     * First layer of the transition drawable
     */
    private final ColorDrawable mCurrentDrawable;

    /**
     * Layer drawable used to cross fade the result from the worker
     */
    private final Drawable[] mArrayDrawable;

    /**
     * Default album art
     */
    private final Bitmap mDefault;

    /**
     * The Context to use
     */
    protected Context mContext;

    /**
     * Disk and memory caches
     */
    protected ImageCache mImageCache;

    /**
     * Constructor of <code>ImageWorker</code>
     *
     * @param context The {@link Context} to use
     */
    protected ImageWorker(final Context context) {
        mContext = context.getApplicationContext();
        mResources = mContext.getResources();
        // Create the default artwork
        final ThemeUtils theme = new ThemeUtils(context);
        mDefault = ((BitmapDrawable) theme.getDrawable("default_artwork")).getBitmap();
        mDefaultArtwork = new BitmapDrawable(mResources, mDefault);
        // No filter and no dither makes things much quicker
        mDefaultArtwork.setFilterBitmap(false);
        mDefaultArtwork.setDither(false);
        // Create the transparent layer for the transition drawable
        mCurrentDrawable = new ColorDrawable(mResources.getColor(R.color.transparent));
        // A transparent image (layer 0) and the new result (layer 1)
        mArrayDrawable = new Drawable[2];
        mArrayDrawable[0] = mCurrentDrawable;
        // XXX The second layer is set in the worker task.
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
     * Closes the disk cache associated with this ImageCache object. Note that
     * this includes disk access so this should not be executed on the main/UI
     * thread.
     */
    public void close() {
        if (mImageCache != null) {
            mImageCache.close();
        }
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
     * Adds a new image to the memory and disk caches
     *
     * @param data The key used to store the image
     * @param bitmap The {@link Bitmap} to cache
     */
    public void addBitmapToCache(final String key, final Bitmap bitmap) {
        if (mImageCache != null) {
            mImageCache.addBitmapToCache(key, bitmap);
        }
    }

    /**
     * @return The deafult artwork
     */
    public Bitmap getDefaultArtwork() {
        return mDefault;
    }

    /**
     * The actual {@link AsyncTask} that will process the image.
     */
    private final class BitmapWorkerTask extends AsyncTask<String, Void, TransitionDrawable> {

        /**
         * The {@link ImageView} used to set the result
         */
        private final WeakReference<ImageView> mImageReference;

        /**
         * Type of URL to download
         */
        private final ImageType mImageType;

        /**
         * The key used to store cached entries
         */
        private String mKey;

        /**
         * Artist name param
         */
        private String mArtistName;

        /**
         * Album name parm
         */
        private String mAlbumName;

        /**
         * The album ID used to find the corresponding artwork
         */
        private long mAlbumId;

        /**
         * The URL of an image to download
         */
        private String mUrl;

        /**
         * Constructor of <code>BitmapWorkerTask</code>
         *
         * @param imageView The {@link ImageView} to use.
         * @param imageType The type of image URL to fetch for.
         */
        @SuppressWarnings("deprecation")
        public BitmapWorkerTask(final ImageView imageView, final ImageType imageType) {
            imageView.setBackgroundDrawable(mDefaultArtwork);
            mImageReference = new WeakReference<ImageView>(imageView);
            mImageType = imageType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected TransitionDrawable doInBackground(final String... params) {
            // Define the key
            mKey = params[0];

            // The result
            Bitmap bitmap = null;

            // First, check the disk cache for the image
            if (mKey != null && mImageCache != null && !isCancelled()
                    && getAttachedImageView() != null) {
                bitmap = mImageCache.getCachedBitmap(mKey);
            }

            // Define the album id now
            mAlbumId = Long.valueOf(params[3]);

            // Second, if we're fetching artwork, check the device for the image
            if (bitmap == null && mImageType.equals(ImageType.ALBUM) && mAlbumId >= 0
                    && mKey != null && !isCancelled() && getAttachedImageView() != null
                    && mImageCache != null) {
                bitmap = mImageCache.getCachedArtwork(mContext, mKey, mAlbumId);
            }

            // Third, by now we need to download the image
            if (bitmap == null && ApolloUtils.isOnline(mContext) && !isCancelled()
                    && getAttachedImageView() != null) {
                // Now define what the artist name, album name, and url are.
                mArtistName = params[1];
                mAlbumName = params[2];
                mUrl = processImageUrl(mArtistName, mAlbumName, mImageType);
                if (mUrl != null) {
                    bitmap = processBitmap(mUrl);
                }
            }

            // Fourth, add the new image to the cache
            if (bitmap != null && mKey != null && mImageCache != null) {
                addBitmapToCache(mKey, bitmap);
            }

            // Add the second layer to the transiation drawable
            if (bitmap != null) {
                final BitmapDrawable layerTwo = new BitmapDrawable(mResources, bitmap);
                layerTwo.setFilterBitmap(false);
                layerTwo.setDither(false);
                mArrayDrawable[1] = layerTwo;

                // Finally, return the image
                final TransitionDrawable result = new TransitionDrawable(mArrayDrawable);
                result.setCrossFadeEnabled(true);
                result.startTransition(FADE_IN_TIME);
                return result;
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void onPostExecute(TransitionDrawable result) {
            if (isCancelled()) {
                result = null;
            }
            final ImageView imageView = getAttachedImageView();
            if (result != null && imageView != null) {
                imageView.setImageDrawable(result);
            }
        }

        /**
         * @return The {@link ImageView} associated with this task as long as
         *         the ImageView's task still points to this task as well.
         *         Returns null otherwise.
         */
        private final ImageView getAttachedImageView() {
            final ImageView imageView = mImageReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask) {
                return imageView;
            }
            return null;
        }
    }

    /**
     * Calls {@code cancel()} in the worker task
     *
     * @param imageView the {@link ImageView} to use
     */
    public static final void cancelWork(final ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            bitmapWorkerTask.cancel(true);
        }
    }

    /**
     * Returns true if the current work has been canceled or if there was no
     * work in progress on this image view. Returns false if the work in
     * progress deals with the same data. The work is not stopped in that case.
     */
    public static final boolean executePotentialWork(final Object data, final ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mKey;
            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        return true;
    }

    /**
     * Used to determine if the current image drawable has an instance of
     * {@link BitmapWorkerTask}
     *
     * @param imageView Any {@link ImageView}.
     * @return Retrieve the currently active work task (if any) associated with
     *         this {@link ImageView}. null if there is no such task.
     */
    private static final BitmapWorkerTask getBitmapWorkerTask(final ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable)drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * A custom {@link BitmapDrawable} that will be attached to the
     * {@link ImageView} while the work is in progress. Contains a reference to
     * the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can
     * bind its result, independently of the finish order.
     */
    private static final class AsyncDrawable extends ColorDrawable {

        private final WeakReference<BitmapWorkerTask> mBitmapWorkerTaskReference;

        /**
         * Constructor of <code>AsyncDrawable</code>
         */
        public AsyncDrawable(final Resources res, final Bitmap bitmap,
                final BitmapWorkerTask mBitmapWorkerTask) {
            super(Color.TRANSPARENT);
            mBitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(mBitmapWorkerTask);
        }

        /**
         * @return The {@link BitmapWorkerTask} associated with this drawable
         */
        public BitmapWorkerTask getBitmapWorkerTask() {
            return mBitmapWorkerTaskReference.get();
        }
    }

    /**
     * Called to fetch the artist or ablum art.
     *
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    protected void loadImage(final String key, final String artistName, final String albumName,
            final long albumId, final ImageView imageView, final ImageType imageType) {
        if (key == null || mImageCache == null || imageView == null) {
            return;
        }

        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        if (ImageType.ALBUM.equals(imageType)) {
            final Uri albumArtUri = loader.getAlbumArtUri(albumId);
            loader.load(albumArtUri, imageView, R.drawable.default_artwork);
        } else if (ImageType.ARTIST.equals(imageType)) {
            final Uri artistArtUri = loader.getArtistArtUri(artistName);
            loader.load(artistArtUri, imageView, R.drawable.default_artwork);
        }

        // First, check the memory for the image
        /**
        final Bitmap lruBitmap = mImageCache.getBitmapFromMemCache(key);
        if (lruBitmap != null && imageView != null) {
            // Bitmap found in memory cache
            imageView.setImageBitmap(lruBitmap);
        } else if (executePotentialWork(key, imageView)
                && imageView != null && !mImageCache.isDiskCachePaused()) {
            // Otherwise run the worker task
            final BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(imageView, imageType);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mDefault,
                    bitmapWorkerTask);
            imageView.setImageDrawable(asyncDrawable);
            try {
                ApolloUtils.execute(false, bitmapWorkerTask, key,
                        artistName, albumName, String.valueOf(albumId));
            } catch (RejectedExecutionException e) {
                // Executor has exhausted queue space, show default artwork
                imageView.setImageBitmap(getDefaultArtwork());
            }
        }*/
    }

    /**
     * Subclasses should override this to define any processing or work that
     * must happen to produce the final {@link Bitmap}. This will be executed in
     * a background thread and be long running.
     *
     * @param key The key to identify which image to process, as provided by
     *            {@link ImageWorker#loadImage(mKey, ImageView)}
     * @return The processed {@link Bitmap}.
     */
    protected abstract Bitmap processBitmap(String key);

    /**
     * Subclasses should override this to define any processing or work that
     * must happen to produce the URL needed to fetch the final {@link Bitmap}.
     *
     * @param artistName The artist name param used in the Last.fm API.
     * @param albumName The album name param used in the Last.fm API.
     * @param imageType The type of image URL to fetch for.
     * @return The image URL for an artist image or album image.
     */
    protected abstract String processImageUrl(String artistName, String albumName,
            ImageType imageType);

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM;
    }

}
