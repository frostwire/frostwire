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

package com.frostwire.android.util;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.StatFs;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.OkHttpClientWrapper;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.Builder;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestCreator;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashSet;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ImageLoader {

    private static final HandlerThread imageLoaderThread = new HandlerThread("ImageLoader-Thread", Process.THREAD_PRIORITY_DISPLAY);

    private static Handler handler;

    private static final Logger LOG = Logger.getLogger(ImageLoader.class);

    private static final String SCHEME_IMAGE = "image";

    private static final String APPLICATION_AUTHORITY = "application";

    private static final String ALBUM_AUTHORITY = "album";

    private static final String ARTIST_AUTHORITY = "artist";

    private static final String METADATA_AUTHORITY = "metadata";

    private static final Uri ALBUM_THUMBNAILS_URI = Uri.parse("content://media/external/audio/albumart");

    private static final Uri ARTIST_THUMBNAILS_URI = Uri.parse("content//:media/external/audio/artists");

    private static final boolean DEBUG_ERRORS = false;

    //private final ImageCache cache;
    private final Picasso picasso;

    private boolean shutdown;

    private static ImageLoader instance;

    public static ImageLoader getInstance(Context context) {
        if (instance == null) {
            instance = new ImageLoader(context);
        }
        return instance;
    }

    /**
     * WARNING: this method does not make use of the cache.
     * it is here to be used only (so far) on the notification window view and the RC Interface
     * (things like Lock Screen, Android Wear), which run on another process space. If you try
     * to use a cached image there, you will get some nasty exceptions, therefore you will need
     * this.
     * <p>
     * For loading album art inside the application Activities/Views/Fragments, take a look at
     * FileListAdapter and how it uses the ImageLoader.
     */
    private static Bitmap getAlbumArt(Context context, String albumId) {
        Bitmap bitmap = null;
        try {
            Uri albumUri = Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId);
            try (Cursor cursor = context.getContentResolver().query(albumUri, new String[]{MediaStore.Audio.AlbumColumns.ALBUM_ART}, null, null, null)) {
                LOG.info("getAlbumArt(albumId=" + albumId + ") Using album_art path for uri: " + albumUri, true);
                if (cursor != null && cursor.moveToFirst()) {
                    String albumArt = cursor.getString(0);
                    if (albumArt != null) {
                        bitmap = BitmapFactory.decodeFile(albumArt);
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("getAlbumArt(albumId=" + albumId + ")Error getting album art", e, true);
        }
        return bitmap;
    }

    public static Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(ALBUM_THUMBNAILS_URI, albumId);
        //return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),albumId);
    }

    public static Uri getArtistArtUri(String artistName) {
        return Uri.withAppendedPath(ARTIST_THUMBNAILS_URI, artistName);
    }

    private ImageLoader(Context context) {
        Builder picassoBuilder = new Builder(context)
                .callFactory(createHttpClient(context.getApplicationContext()));
        if (DEBUG_ERRORS) {
            picassoBuilder.listener((picasso, uri, exception) -> LOG.error("ImageLoader::onImageLoadFailed(" + uri + ")", exception));
        }
        this.picasso = picassoBuilder.build();
        try {
            this.picasso.setIndicatorsEnabled(DEBUG_ERRORS);
        } catch (Throwable ignored) {
        }
    }

    public void load(Uri primaryUri, Uri secondaryUri, Filter filter, ImageView target, boolean noCache) {
        if (Debug.hasContext(filter)) {
            throw new RuntimeException("Possible context leak");
        }

        Params p = new Params();
        p.noCache = noCache;
        p.filter = filter;

        if (secondaryUri != null) {
            p.callback = new RetryCallback(this, secondaryUri, target, p);
        }

        load(primaryUri, target, p);
    }

    public void load(Uri uri, ImageView target) {
        Params p = new Params();
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(int resourceId, ImageView target) {
        Params p = new Params();
        p.noFade = true;
        load(resourceId, target, p);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(int resourceId, ImageView target, int targetWidth, int targetHeight) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.noFade = true;
        load(resourceId, target, p);
    }

    public void load(Uri uri, ImageView target, int placeholderResId) {
        Params p = new Params();
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(int resourceId, ImageView target, int placeholderResId) {
        Params p = new Params();
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(resourceId, target, p);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight, int placeholderResId) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(int resourceId, ImageView target, int targetWidth, int targetHeight, int placeholderResId) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(resourceId, target, p);
    }

    public void load(Uri uri, Uri retryUri, ImageView target, int targetWidth, int targetHeight) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.noFade = true;

        if (retryUri != null) {
            p.callback = new RetryCallback(this, retryUri, target, p);
        }

        load(uri, target, p);
    }

    public void load(int resourceId, ImageView target, Params p) {
        load(resourceId, null, target, p);
    }

    public void load(Uri uri, ImageView target, Params p) {
        load(-1, uri, target, p);
    }

    private void load(int resourceId, Uri uri, ImageView target, Params p) {
        AsyncLoader asyncLoader = new AsyncLoader(
                resourceId,
                uri,
                Ref.weak(target),
                p,
                shutdown,
                Ref.weak(picasso));
        handler.post(asyncLoader);
    }

    private static class AsyncLoader implements Runnable {

        private final int resourceId;
        private final Uri uri;
        private final WeakReference<ImageView> targetRef;
        private final Params p;
        private final boolean shutdown;
        private final WeakReference<Picasso> picasso;

        AsyncLoader(int resourceId, Uri uri, WeakReference<ImageView> targetRef, Params p, boolean shutdown, WeakReference<Picasso> picassoRef) {
            this.resourceId = resourceId;
            this.uri = uri;
            this.targetRef = targetRef;
            this.p = p;
            this.shutdown = shutdown;
            this.picasso = picassoRef;
        }

        @Override
        public void run() {
            if (shutdown) {
                return;
            }
            if (!Ref.alive(picasso)) {
                LOG.info("AsyncLoader.run() main thread update cancelled, picasso target reference lost.");
                return;
            }
            if (targetRef.get() == null) {
                LOG.warn("AsyncLoader.run() aborted: Target image view can't be null");
                return;
            }
            if (p == null) {
                throw new IllegalArgumentException("Params to load image can't be null");
            }
            if (p.callback != null && !(p.callback instanceof RetryCallback) && // don't ask this recursively
                    Debug.hasContext(p.callback)) {
                throw new RuntimeException("Possible context leak");
            }
            if (p.filter != null && Debug.hasContext(p.filter)) {
                throw new RuntimeException("Possible context leak");
            }
            RequestCreator rc;
            if (uri != null) {
                rc = picasso.get().load(uri);
            } else if (resourceId != -1) {
                rc = picasso.get().load(resourceId);
            } else {
                throw new IllegalArgumentException("resourceId == -1 and uri == null, check your logic");
            }
            if (p.targetWidth != 0 || p.targetHeight != 0) rc.resize(p.targetWidth, p.targetHeight);
            if (p.placeholderResId != 0) rc.placeholder(p.placeholderResId);
            if (p.fit) rc.fit();
            if (p.centerInside) rc.centerInside();
            if (p.noFade) rc.noFade();
            if (p.noCache) {
                rc.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE);
                rc.networkPolicy(NetworkPolicy.NO_CACHE, NetworkPolicy.NO_STORE);
            }
            SystemUtils.postToUIThread(
                    () -> {
                        try {
                            if (!Ref.alive(targetRef)) {
                                LOG.info("ImageLoader.load() main thread update cancelled, ImageView target reference lost.");
                                return;
                            }
                            if (p.callback != null) {
                                rc.into(targetRef.get(), new CallbackWrapper(p.callback));
                            } else {
                                rc.into(targetRef.get());
                            }
                        } catch (Throwable t) {
                            if (BuildConfig.DEBUG) {
                                throw t;
                            }
                            LOG.error("ImageLoader::AsyncLoader::run() error posted caught posting to main looper: " + t.getMessage(), t);
                        }
                    });
        }
    }

    public Bitmap get(Uri uri) {
        try {
            return picasso.load(uri).get();
        } catch (IOException e) {
            return null;
        }
    }

    public void clear() {
        //cache.clear();
        picasso.evictAll();
    }

    public void shutdown() {
        shutdown = true;
        try {
            picasso.getClass().getMethod("shutdown").invoke(picasso);
        } catch (Throwable ignored) {
        }
    }

    public static void start(final MainApplication mainApplication) {
        if (!imageLoaderThread.isAlive()) {
            imageLoaderThread.start();
            handler = new Handler(imageLoaderThread.getLooper());
        }
        handler.postAtFrontOfQueue(() -> startImageLoaderBackground(mainApplication));
    }

    private static void startImageLoaderBackground(MainApplication mainApplication) {
        if (instance == null) {
            ImageLoader.getInstance(mainApplication);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static final class Params {
        public int targetWidth = 0;
        public int targetHeight = 0;
        public int placeholderResId = 0;
        public final boolean fit = false;
        public boolean centerInside = false;
        public boolean noFade = false;
        public boolean noCache = false;
        public Filter filter = null;
        public Callback callback = null;
    }

    public interface Callback {

        void onSuccess();

        void onError(Throwable e);
    }

    public interface Filter {

        Bitmap filter(Bitmap source);

        String params();
    }

    private static final class CallbackWrapper implements
            com.squareup.picasso3.Callback {

        private final Callback cb;

        CallbackWrapper(Callback cb) {
            this.cb = cb;
        }

        @Override
        public void onSuccess() {
            cb.onSuccess();
        }

        @Override
        public void onError(Throwable e) {
            cb.onError(e);
        }
    }

    /**
     * This class is necessary, because passing an anonymous inline
     * class pin the ImageView target to memory with a hard reference
     * in the background thread pool, creating a potential memory leak.
     * Picasso already creates a weak reference to the target while
     * creating and submitting the callable to the background.
     */
    private static final class RetryCallback implements Callback {

        // ImageLoader is a singleton already
        private final WeakReference<ImageLoader> loader;
        private final Uri uri;
        private final WeakReference<ImageView> target;
        private final Params params;

        RetryCallback(ImageLoader loader, Uri uri, ImageView target, Params params) {
            this.loader = Ref.weak(loader);
            this.uri = uri;
            this.target = Ref.weak(target);
            this.params = params;
            this.params.callback = null; // avoid infinite recursion
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(Throwable e) {
            if (Ref.alive(target) && Ref.alive(loader)) {
                loader.get().load(uri, target.get(), params);
            }
        }
    }
    // Transformation wrapper removed for Picasso 3 migration

    // Removed custom RequestHandler; not used by current call sites and Picasso 3 API changed.


    private static OkHttpClient createHttpClient(Context context) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);

        Cache cache = new Cache(cacheDir, maxSize);

        OkHttpClient.Builder b = new OkHttpClient.Builder();
        b = b.cache(cache);
        b = OkHttpClientWrapper.configNullSsl(b);
        return b.build();
    }

    // ------- below code copied from com.squareup.picasso3.Utils -------
    // copied here to keep code independence

    private static final String PICASSO_CACHE = "picasso-cache";
    private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    private static File createDefaultCacheDir(Context context) {
        File cache = SystemUtils.getCacheDir(context, PICASSO_CACHE);
        if (!cache.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
        }
        return cache;
    }

    private static long calculateDiskCacheSize(File dir) {
        long size = MIN_DISK_CACHE_SIZE;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long blockCount = statFs.getBlockCountLong();
            long blockSize = statFs.getBlockSizeLong();
            long available = blockCount * blockSize;
            // Target 2% of the total space.
            size = available / 50;
        } catch (IllegalArgumentException | NoSuchMethodError ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
    }
}
