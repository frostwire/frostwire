/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.cache;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentCallbacks2;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This class holds the memory and disk bitmap caches.
 */
public final class ImageCache {

    public static Logger LOG = Logger.getLogger(ImageCache.class);

    private static final String TAG = ImageCache.class.getSimpleName();

    /**
     * The {@link Uri} used to retrieve album art
     */
    private static final Uri mArtworkUri;

    /**
     * Default memory cache size as a percent of device memory class
     */
    private static final float MEM_CACHE_DIVIDER = 0.25f;

    /**
     * Default disk cache size 10MB
     */
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10;

    /**
     * Disk cache index to read from
     */
    private static final int DISK_CACHE_INDEX = 0;

    /**
     * Target dimensions for album art (reasonable size for display)
     */
    private static final int TARGET_IMAGE_WIDTH = 512;
    private static final int TARGET_IMAGE_HEIGHT = 512;

    /**
     * LRU cache
     */
    private MemoryCache mLruCache;

    /**
     * Disk LRU cache
     */
    private DiskLruCache mDiskCache;

    private static ImageCache sInstance;

    /**
     * Used to temporarily pause the disk cache while scrolling
     */
    private boolean mPauseDiskAccess = false;
    private final Object mPauseLock = new Object();

    static {
        mArtworkUri = Uri.parse("content://media/external/audio/albumart");
    }

    /**
     * Constructor of <code>ImageCache</code>
     *
     * @param context The {@link Context} to use
     */
    private ImageCache(final Context context) {
        init(context);
    }

    /**
     * Used to create a singleton of {@link ImageCache}
     *
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static ImageCache getInstance(final Context context) {
        LOG.info("ImageCache.getInstance()...", false);
        if (sInstance == null) {
            sInstance = new ImageCache(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Initialize the cache, providing all parameters.
     *
     * @param context The {@link Context} to use
     */
    private void init(final Context context) {
        InitDiskCacheAsyncTask initDiskCacheAsyncTask = new InitDiskCacheAsyncTask(this, context);
        Engine.instance().getThreadPool().execute(initDiskCacheAsyncTask::doInBackground);

        // Set up the memory cache
        initLruCache(context);
    }

    private final static class InitDiskCacheAsyncTask {
        private ImageCache imageCache;
        private WeakReference<Context> contextRef;

        InitDiskCacheAsyncTask(ImageCache imageCache, Context context) {
            this.imageCache = imageCache;
            contextRef = Ref.weak(context);
        }

        void doInBackground() {
            // Initialize the disk cache in a background thread
            if (Ref.alive(contextRef)) {
                imageCache.initDiskCache(contextRef.get());
            }
            Ref.free(contextRef);
        }
    }

    /**
     * Initializes the disk cache. Note that this includes disk access so this
     * should not be executed on the main/UI thread. By default an ImageCache
     * does not initialize the disk cache when it is created, instead you should
     * call initDiskCache() to initialize it on a background thread.
     *
     * @param context The {@link Context} to use
     */
    private synchronized void initDiskCache(final Context context) {
        // Set up disk cache
        if (mDiskCache == null || mDiskCache.isClosed()) {
            File diskCacheDir = null;
            try {
                diskCacheDir = SystemUtils.getCacheDir(context, "apollo-image");
            } catch (NullPointerException e) {
                Log.e(TAG, "initDiskCache - " + e);
                // this will probably cause a fail at a later time, but
                // this give the opportunity to handle the error at another
                // level with a better user feedback
            }
            if (diskCacheDir != null) {
                if (!diskCacheDir.exists()) {
                    diskCacheDir.mkdirs();
                }
                if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
                    try {
                        mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Sets up the Lru cache
     *
     * @param context The {@link Context} to use
     */
    private void initLruCache(final Context context) {
        final ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int lruCacheSize = Math.round(MEM_CACHE_DIVIDER * activityManager.getMemoryClass()
                * 1024 * 1024);
        mLruCache = new MemoryCache(lruCacheSize);
        // Release some memory as needed
        context.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(final int level) {
                if (level >= TRIM_MEMORY_MODERATE) {
                    evictAll();
                } else if (level >= TRIM_MEMORY_BACKGROUND) {
                    mLruCache.trimToSize(mLruCache.size() / 2);
                }
            }

            @Override
            public void onLowMemory() {
                // Nothing to do
            }

            @Override
            public void onConfigurationChanged(@NonNull final Configuration newConfig) {
                // Nothing to do
            }
        });
    }

    /**
     * Find and return an existing ImageCache stored in a {@link RetainFragment}
     * , if not found a new one is created using the supplied params and saved
     * to a {@link RetainFragment}
     *
     * @param activity The calling {FragmentActivity}
     * @return An existing retained ImageCache object or a new one if one did
     * not exist
     */
    public static ImageCache findOrCreateCache(final Activity activity) {
        // Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment retainFragment = findOrCreateRetainFragment(
                activity.getFragmentManager());
        // See if we already have an ImageCache stored in RetainFragment
        ImageCache cache = (ImageCache) retainFragment.getObject();
        // No existing ImageCache, create one and store it in RetainFragment
        if (cache == null) {
            cache = getInstance(activity);
            retainFragment.setObject(cache);
        }
        return cache;
    }

    /**
     * Locate an existing instance of this {@link Fragment} or if not found,
     * create and add it using {@link FragmentManager}
     *
     * @param fm The {@link FragmentManager} to use
     * @return The existing instance of the {@link Fragment} or the new instance
     * if just created
     */
    private static RetainFragment findOrCreateRetainFragment(final FragmentManager fm) {
        // Check to see if we have retained the worker fragment
        RetainFragment retainFragment = (RetainFragment) fm.findFragmentByTag(TAG);
        // If not retained, we need to create and add it
        if (retainFragment == null) {
            retainFragment = new RetainFragment();
            fm.beginTransaction().add(retainFragment, TAG).commit();
        }
        return retainFragment;
    }

    /**
     * Called to add a new image to the memory cache
     *
     * @param data   The key identifier
     * @param bitmap The {@link Bitmap} to cache
     */
    private void addBitmapToMemCache(final String data, final Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }
        // Add to memory cache
        if (getBitmapFromMemCache(data) == null) {
            mLruCache.put(data, bitmap);
        }
    }

    /**
     * Fetches a cached image from the memory cache
     *
     * @param data Unique identifier for which item to get
     * @return The {@link Bitmap} if found in cache, null otherwise
     */
    private Bitmap getBitmapFromMemCache(final String data) {
        if (data == null) {
            return null;
        }
        if (mLruCache != null) {
            return mLruCache.get(data);
        }
        return null;
    }

    /**
     * Fetches a cached image from the disk cache
     *
     * @param data Unique identifier for which item to get
     * @return The {@link Bitmap} if found in cache, null otherwise
     */
    final Bitmap getBitmapFromDiskCache(final String data) {
        if (data == null) {
            return null;
        }
        // Check in the memory cache here to avoid going to the disk cache less
        // often
        if (getBitmapFromMemCache(data) != null) {
            return getBitmapFromMemCache(data);
        }
        waitUntilUnpaused();
        final String key = hashKeyForDisk(data);
        if (mDiskCache != null) {
            DiskLruCache.Snapshot snapshot = null;
            InputStream inputStream = null;
            try {
                snapshot = mDiskCache.get(key);
                if (snapshot != null) {
                    // First pass: Get dimensions
                    inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                    if (inputStream != null) {
                        final BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(inputStream, null, options);

                        // Close first stream
                        try {
                            inputStream.close();
                        } catch (final IOException ignored) {
                        }

                        // Calculate inSampleSize
                        options.inSampleSize = calculateInSampleSize(options, TARGET_IMAGE_WIDTH, TARGET_IMAGE_HEIGHT);

                        // Second pass: Decode with sampling
                        // Need to get a fresh input stream since we can't rewind
                        inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            options.inJustDecodeBounds = false;
                            final Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                            if (bitmap != null) {
                                return bitmap;
                            }
                        }
                    }
                }
            } catch (final IOException e) {
                Log.e(TAG, "getBitmapFromDiskCache - " + e);
            } catch (final OutOfMemoryError e) {
                Log.e(TAG, "getBitmapFromDiskCache - OutOfMemoryError, evicting cache", e);
                evictAll();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (final IOException ignored) {
                }
                if (snapshot != null) {
                    snapshot.close();
                }
            }
        }
        return null;
    }

    /**
     * Tries to return a cached image from memory cache before fetching from the
     * disk cache
     *
     * @param data Unique identifier for which item to get
     * @return The {@link Bitmap} if found in cache, null otherwise
     */
    final Bitmap getCachedBitmap(final String data) {
        if (data == null) {
            return null;
        }
        Bitmap cachedImage = getBitmapFromMemCache(data);
        if (cachedImage == null) {
            cachedImage = getBitmapFromDiskCache(data);
        }
        if (cachedImage != null) {
            addBitmapToMemCache(data, cachedImage);
            return cachedImage;
        }
        return null;
    }

    /**
     * Used to fetch the artwork for an album locally from the user's device
     *
     * @param context The {@link Context} to use
     * @param albumId The ID of the album to find artwork for
     * @return The artwork for an album
     */
    final Bitmap getArtworkFromFile(final Context context, final long albumId) {
        if (albumId < 0) {
            return null;
        }
        Bitmap artwork = null;
        waitUntilUnpaused();
        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            final Uri uri = ContentUris.withAppendedId(mArtworkUri, albumId);
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (parcelFileDescriptor != null) {
                final FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                // Use sampled decoding to reduce memory usage
                artwork = decodeSampledBitmapFromFileDescriptor(fileDescriptor, TARGET_IMAGE_WIDTH, TARGET_IMAGE_HEIGHT);
            }
        } catch (final IllegalStateException e) {
            // Log.e(TAG, "IllegalStateExcetpion - getArtworkFromFile - ", e);
        } catch (final FileNotFoundException e) {
            // Log.e(TAG, "FileNotFoundException - getArtworkFromFile - ", e);
        } catch (final OutOfMemoryError evict) {
            // Log.e(TAG, "OutOfMemoryError - getArtworkFromFile - ", evict);
            evictAll();
        } catch (final NullPointerException e) {
            // Log.e(TAG, "NullPointerException - getArtworkFromFile - ", e);
        } catch (SecurityException ignored) {
        } finally {
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close();
                } catch (IOException ignored) {
                }
            }
        }
        return artwork;
    }

    /**
     * flush() is called to synchronize up other methods that are accessing the
     * cache first
     */
    public void flush() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, this::flushTask);
    }

    private void flushTask() {
        if (mDiskCache != null) {
            try {
                if (!mDiskCache.isClosed()) {
                    mDiskCache.flush();
                }
            } catch (final IOException e) {
                Log.e(TAG, "flushTask() - " + e);
            }
        }
    }

    /**
     * Evicts all of the items from the memory cache and lets the system know
     * now would be a good time to garbage collect
     */
    public void evictAll() {
        if (mLruCache != null) {
            mLruCache.evictAll();
        }
        System.gc();
    }

    /**
     * @param key The key used to identify which cache entries to delete.
     */
    void removeFromCache(final String key) {
        if (key == null) {
            return;
        }
        // Remove the Lru entry
        if (mLruCache != null) {
            mLruCache.remove(key);
        }
        try {
            // Remove the disk entry
            if (mDiskCache != null) {
                mDiskCache.remove(hashKeyForDisk(key));
            }
        } catch (final IOException e) {
            Log.e(TAG, "remove - " + e);
        }
        flush();
    }

    /**
     * Used to temporarily pause the disk cache while the user is scrolling to
     * improve scrolling.
     *
     * @param pause True to temporarily pause the disk cache, false otherwise.
     */
    void setPauseDiskCache(final boolean pause) {
        synchronized (mPauseLock) {
            if (mPauseDiskAccess != pause) {
                mPauseDiskAccess = pause;
                if (!pause) {
                    mPauseLock.notify();
                }
            }
        }
    }

    private void waitUntilUnpaused() {
        synchronized (mPauseLock) {
            if (!SystemUtils.isUIThread()) {
                while (mPauseDiskAccess) {
                    try {
                        mPauseLock.wait();
                    } catch (InterruptedException e) {
                        // ignored, we'll start waiting again
                    }
                }
            }
        }
    }

    /**
     * Check how much usable space is available at a given path.
     *
     * @param path The path to check
     * @return The space available in bytes
     */
    private static long getUsableSpace(final File path) {
        return path.getUsableSpace();
    }

    /**
     * Calculate the inSampleSize value for efficient bitmap loading.
     * This uses a power-of-2 value to reduce memory usage while maintaining quality.
     *
     * @param options   The BitmapFactory.Options containing image dimensions
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The inSampleSize value (always a power of 2)
     */
    private static int calculateInSampleSize(final BitmapFactory.Options options,
                                             final int reqWidth, final int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Decode a sampled bitmap from a FileDescriptor using inSampleSize to reduce memory usage.
     * This performs a two-pass decode: first to get dimensions, second to decode with sampling.
     *
     * @param fd        The FileDescriptor containing the image data
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The decoded and sampled Bitmap, or null if decoding fails
     */
    private static Bitmap decodeSampledBitmapFromFileDescriptor(final FileDescriptor fd,
                                                                final int reqWidth, final int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable
     * for using as a disk filename.
     *
     * @param key The key used to store the file
     */
    private static String hashKeyForDisk(final String key) {
        String cacheKey;
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(key.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    /**
     * http://stackoverflow.com/questions/332079
     *
     * @param bytes The bytes to convert.
     * @return A {@link String} converted from the bytes of a hashable key used
     * to store a filename on the disk, to hex digits.
     */
    private static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        for (final byte b : bytes) {
            final String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    /**
     * A simple non-UI Fragment that stores a single Object and is retained over
     * configuration changes. In this sample it will be used to retain an
     * {@link ImageCache} object.
     */
    public static final class RetainFragment extends Fragment {

        /**
         * The object to be stored
         */
        private Object mObject;

        /**
         * Empty constructor as per the {@link Fragment} documentation
         */
        public RetainFragment() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Make sure this Fragment is retained over a configuration change
            setRetainInstance(true);
        }

        /**
         * Store a single object in this {@link Fragment}
         *
         * @param object The object to store
         */
        public void setObject(final Object object) {
            mObject = object;
        }

        /**
         * Get the stored object
         *
         * @return The stored object
         */
        public Object getObject() {
            return mObject;
        }
    }

    /**
     * Used to cache images via {@link LruCache}.
     */
    public static final class MemoryCache extends LruCache<String, Bitmap> {

        /**
         * Constructor of <code>MemoryCache</code>
         *
         * @param maxSize The allowed size of the {@link LruCache}
         */
        MemoryCache(final int maxSize) {
            super(maxSize);
        }

        /**
         * Get the size in bytes of a bitmap.
         */
        static int getBitmapSize(final Bitmap bitmap) {
            return bitmap.getByteCount();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected int sizeOf(final String paramString, final Bitmap paramBitmap) {
            return getBitmapSize(paramBitmap);
        }

        /**
         * Called when a bitmap is evicted from the cache.
         * Recycles the bitmap to free native memory immediately rather than waiting for GC.
         *
         * Native bitmap memory is not tracked by the Java heap, so without explicit recycle()
         * calls, native memory can accumulate and cause native OOM errors even when Java heap
         * has plenty of space.
         *
         * @param evicted  Whether the entry was evicted or removed
         * @param key      The key for the entry
         * @param oldValue The bitmap being removed
         * @param newValue The new bitmap (if being replaced), or null
         */
        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);

            // Only recycle if the bitmap is actually being removed (newValue is null)
            // and the bitmap hasn't already been recycled
            if (newValue == null && oldValue != null && !oldValue.isRecycled()) {
                oldValue.recycle();
            }
        }
    }
}
