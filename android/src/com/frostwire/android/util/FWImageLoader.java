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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.StatFs;
import android.widget.ImageView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.OkHttpClientWrapper;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import coil3.ImageLoader;
import coil3.disk.DiskCache;
import coil3.memory.MemoryCache;
import coil3.request.CachePolicy;
import coil3.request.Disposable;
import coil3.request.ErrorResult;
import coil3.request.ImageRequest;
import coil3.request.SuccessResult;
import coil3.target.ImageViewTarget;
import coil3.util.DebugLogger;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import okhttp3.OkHttpClient;
import okio.Path;
import static okio.FileSystem.SYSTEM;

/**
 * FrostWire Image Loader - Wrapper around Coil image loading library.
 * Renamed from ImageLoader to FWImageLoader to avoid confusion with coil3.ImageLoader.
 * 
 * @author gubatron
 * @author aldenml
 */
public final class FWImageLoader {

    private static final HandlerThread imageLoaderThread = new HandlerThread("FWImageLoader-Thread", Process.THREAD_PRIORITY_DISPLAY);

    private static Handler handler;

    private static final Logger LOG = Logger.getLogger(FWImageLoader.class);

    private static final Uri ALBUM_THUMBNAILS_URI = Uri.parse("content://media/external/audio/albumart");

    private static final Uri ARTIST_THUMBNAILS_URI = Uri.parse("content//:media/external/audio/artists");

    private static final boolean DEBUG_ERRORS = false;

    private coil3.ImageLoader coilImageLoader;

    private boolean shutdown;

    private static FWImageLoader instance;

    public static FWImageLoader getInstance(Context context) {
        if (instance == null || instance.shutdown) {
            synchronized (FWImageLoader.class) {
                if (instance == null || instance.shutdown) {
                    LOG.info("Creating new FWImageLoader instance" + (instance != null && instance.shutdown ? " (previous was shutdown)" : ""));
                    
                    // If there's an old instance being replaced, properly shut down its Coil ImageLoader
                    if (instance != null && instance.coilImageLoader != null) {
                        try {
                            LOG.info("Shutting down old Coil ImageLoader instance");
                            instance.coilImageLoader.shutdown();
                        } catch (Throwable t) {
                            LOG.warn("Error shutting down old Coil ImageLoader instance", t);
                        }
                    }
                    
                    instance = new FWImageLoader(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Gets the current FWImageLoader instance without creating a new one.
     * Returns null if no instance exists or it has been shutdown.
     */
    public static synchronized FWImageLoader getInstanceIfExists() {
        if (instance != null && !instance.shutdown && instance.coilImageLoader != null) {
            return instance;
        }
        return null;
    }
    
    /**
     * Checks if the current FWImageLoader instance is healthy and recreates it if needed.
     * This helps recover from internal crashes.
     */
    public static synchronized void ensureHealthyInstance(Context context) {
        if (instance == null || instance.shutdown || instance.coilImageLoader == null) {
            LOG.info("Recreating FWImageLoader instance due to unhealthy state");
            
            // If there's an old instance being replaced, properly shut down its Coil ImageLoader
            if (instance != null && instance.coilImageLoader != null) {
                try {
                    LOG.info("Shutting down old Coil ImageLoader instance");
                    instance.coilImageLoader.shutdown();
                } catch (Throwable t) {
                    LOG.warn("Error shutting down old Coil ImageLoader instance", t);
                }
            }
            
            instance = new FWImageLoader(context);
        }
    }

    public static Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(ALBUM_THUMBNAILS_URI, albumId);
        //return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),albumId);
    }

    public static Uri getArtistArtUri(String artistName) {
        return Uri.withAppendedPath(ARTIST_THUMBNAILS_URI, artistName);
    }

    private FWImageLoader(Context context) {
        // Coil doesn't have the NetworkBroadcastReceiver issues that Picasso had,
        // so we can use the context directly without SafeContextWrapper
        Context appContext = context.getApplicationContext();

        // Build Coil ImageLoader with simplified configuration
        try {
            coil3.ImageLoader.Builder coilBuilder = new coil3.ImageLoader.Builder(appContext);
            
            // Note: OkHttp client configuration is built into Coil 3.x
            // Custom OkHttp client can be set via callFactory, but we'll use defaults
            
            // Configure memory cache
            MemoryCache memCache = new MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.25)
                    .build();
            coilBuilder.memoryCache(() -> memCache);
            
            // Configure disk cache using okio.Path
            File cacheDir = createDefaultCacheDir(appContext);
            long maxSize = calculateDiskCacheSize(cacheDir);
            Path cachePath = Path.get(cacheDir.getAbsolutePath());
            DiskCache diskCache = new DiskCache.Builder()
                    .directory(cachePath)
                    .maxSizeBytes(maxSize)
                    .build();
            coilBuilder.diskCache(() -> diskCache);
            
            if (DEBUG_ERRORS) {
                coilBuilder.logger(new DebugLogger());
            }
            
            this.coilImageLoader = coilBuilder.build();
            LOG.info("Coil ImageLoader instance created successfully");
        } catch (Throwable t) {
            LOG.error("Failed to build Coil ImageLoader instance", t);
            // Create a fallback builder with minimal configuration
            try {
                this.coilImageLoader = new coil3.ImageLoader.Builder(appContext).build();
            } catch (Throwable fallbackError) {
                LOG.error("Failed to create fallback Coil ImageLoader instance", fallbackError);
                throw new RuntimeException("Unable to initialize Coil ImageLoader", fallbackError);
            }
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
        if (shutdown) {
            LOG.info("FWImageLoader is shutdown, skipping load request");
            return;
        }
        if (coilImageLoader == null) {
            LOG.warn("Coil ImageLoader instance is null, cannot load image");
            return;
        }
        AsyncLoader asyncLoader = new AsyncLoader(
                resourceId,
                uri,
                Ref.weak(target),
                p,
                shutdown,
                Ref.weak(coilImageLoader));
        
        try {
            handler.post(asyncLoader);
        } catch (Throwable t) {
            LOG.error("Failed to post AsyncLoader to handler", t);
            // Try to run directly on current thread if handler is not available
            try {
                asyncLoader.run();
            } catch (Throwable fallbackError) {
                LOG.error("Failed to run AsyncLoader directly", fallbackError);
            }
        }
    }

    private static class AsyncLoader implements Runnable {

        private final int resourceId;
        private final Uri uri;
        private final WeakReference<ImageView> targetRef;
        private final Params p;
        private final boolean shutdown;
        private final WeakReference<coil3.ImageLoader> coilImageLoader;

        AsyncLoader(int resourceId, Uri uri, WeakReference<ImageView> targetRef, Params p, boolean shutdown, WeakReference<coil3.ImageLoader> coilImageLoaderRef) {
            this.resourceId = resourceId;
            this.uri = uri;
            this.targetRef = targetRef;
            this.p = p;
            this.shutdown = shutdown;
            this.coilImageLoader = coilImageLoaderRef;
        }

        @Override
        public void run() {
            if (shutdown) {
                return;
            }
            if (!Ref.alive(coilImageLoader)) {
                LOG.info("AsyncLoader.run() main thread update cancelled, coilImageLoader target reference lost.");
                return;
            }
            if (!Ref.alive(targetRef)) {
                LOG.warn("AsyncLoader.run() aborted: Target ImageView reference lost");
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
            
            SystemUtils.postToUIThread(
                    () -> {
                        try {
                            if (!Ref.alive(targetRef)) {
                                LOG.info("FWImageLoader.load() main thread update cancelled, ImageView target reference lost.");
                                return;
                            }
                            
                            ImageView target = targetRef.get();
                            Context context = target.getContext();
                            
                            // Build Coil request
                            ImageRequest.Builder requestBuilder = new ImageRequest.Builder(context);
                            
                            // Set data (URI or resource ID)
                            if (uri != null) {
                                requestBuilder.data(uri);
                            } else if (resourceId != -1) {
                                requestBuilder.data(resourceId);
                            } else {
                                throw new IllegalArgumentException("resourceId == -1 and uri == null, check your logic");
                            }
                            
                            // Set target ImageView using ImageViewTarget
                            requestBuilder.target(new ImageViewTarget(target));
                            
                            // Configure request based on params
                            if (p.targetWidth != 0 && p.targetHeight != 0) {
                                requestBuilder.size(p.targetWidth, p.targetHeight);
                            }
                            // Note: Coil 3.x placeholder/error API is complex in Java
                            // For now, we'll skip placeholders - they can be added later if needed
                            // The ImageView will just show empty/previous content until image loads
                            
                            if (!p.noFade) {
                                // Coil 3.x: Use crossfade with boolean
                                requestBuilder.crossfade(true);
                            }
                            if (p.noCache) {
                                requestBuilder.memoryCachePolicy(CachePolicy.DISABLED);
                                requestBuilder.diskCachePolicy(CachePolicy.DISABLED);
                            }
                            
                            // Add listener if callback is provided
                            if (p.callback != null) {
                                final Callback callback = p.callback;
                                requestBuilder.listener(
                                    new coil3.request.ImageRequest.Listener() {
                                        @Override
                                        public void onStart(@org.jetbrains.annotations.NotNull ImageRequest request) {
                                            // Request started
                                        }
                                        
                                        @Override
                                        public void onSuccess(@org.jetbrains.annotations.NotNull ImageRequest request, 
                                                             @org.jetbrains.annotations.NotNull SuccessResult result) {
                                            callback.onSuccess();
                                        }
                                        
                                        @Override
                                        public void onError(@org.jetbrains.annotations.NotNull ImageRequest request, 
                                                           @org.jetbrains.annotations.NotNull ErrorResult result) {
                                            callback.onError(result.getThrowable());
                                        }
                                        
                                        @Override
                                        public void onCancel(@org.jetbrains.annotations.NotNull ImageRequest request) {
                                            // Request cancelled
                                        }
                                    }
                                );
                            }
                            
                            // Execute request
                            coilImageLoader.get().enqueue(requestBuilder.build());
                            
                        } catch (Throwable t) {
                            if (BuildConfig.DEBUG) {
                                LOG.error("FWImageLoader::AsyncLoader::run() error posting to main looper in DEBUG mode", t);
                                throw t;
                            }
                            LOG.error("FWImageLoader::AsyncLoader::run() error posted caught posting to main looper: " + t.getMessage(), t);
                        }
                    });
        }
    }

    public Bitmap get(Uri uri) {
        if (shutdown || coilImageLoader == null) {
            LOG.info("FWImageLoader is shutdown or coilImageLoader is null, returning null for get() request");
            return null;
        }
        
        // NOTE: Coil 3.x execute() is a suspend function and cannot be called synchronously from Java.
        // This method is deprecated and should not be used. Use the async load() methods instead.
        // For now, we return null and log a warning.
        LOG.warn("Synchronous bitmap loading (get()) is not supported with Coil 3.x. Use async load() methods instead. URI: " + uri);
        return null;
    }

    public void clear() {
        if (coilImageLoader != null) {
            try {
                // Clear memory cache
                MemoryCache memoryCache = coilImageLoader.getMemoryCache();
                if (memoryCache != null) {
                    memoryCache.clear();
                }
                // Clear disk cache
                DiskCache diskCache = coilImageLoader.getDiskCache();
                if (diskCache != null) {
                    diskCache.clear();
                }
            } catch (Throwable t) {
                LOG.error("Error while clearing Coil cache", t);
            }
        }
    }

    public void shutdown() {
        synchronized (FWImageLoader.class) {
            if (shutdown) {
                LOG.info("FWImageLoader already shutdown, skipping");
                return;
            }
            LOG.info("FWImageLoader shutdown requested");
            shutdown = true;

            // Coil doesn't have the HandlerDispatcher race condition that Picasso had,
            // so we can safely shutdown and clear the cache
            if (coilImageLoader != null) {
                try {
                    LOG.info("Clearing Coil cache during shutdown");
                    clear();
                    LOG.info("Shutting down Coil ImageLoader");
                    coilImageLoader.shutdown();
                } catch (Throwable t) {
                    LOG.warn("Failed to clear/shutdown Coil ImageLoader during shutdown", t);
                }
            }

            // Null out the singleton instance reference
            instance = null;
        }
    }

    public static void start(final MainApplication mainApplication) {
        try {
            if (!imageLoaderThread.isAlive()) {
                imageLoaderThread.start();
                handler = new Handler(imageLoaderThread.getLooper());
            }
            if (handler != null) {
                handler.postAtFrontOfQueue(() -> startImageLoaderBackground(mainApplication));
            } else {
                LOG.error("Handler is null after thread start, running directly");
                startImageLoaderBackground(mainApplication);
            }
        } catch (Throwable t) {
            LOG.error("Error starting FWImageLoader", t);
            // Try to initialize directly if thread approach fails
            try {
                startImageLoaderBackground(mainApplication);
            } catch (Throwable fallbackError) {
                LOG.error("Failed to start FWImageLoader with fallback", fallbackError);
            }
        }
    }

    private static void startImageLoaderBackground(MainApplication mainApplication) {
        try {
            if (instance == null || instance.shutdown) {
                LOG.info("Creating FWImageLoader instance in background thread");
                FWImageLoader.getInstance(mainApplication);
            } else {
                LOG.info("FWImageLoader instance already exists and is healthy");
            }
        } catch (Throwable t) {
            LOG.error("Error creating FWImageLoader instance in background", t);
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



    /**
     * This class is necessary, because passing an anonymous inline
     * class pin the ImageView target to memory with a hard reference
     * in the background thread pool, creating a potential memory leak.
     * Coil already creates a weak reference to the target while
     * creating and submitting the callable to the background.
     */
    private static final class RetryCallback implements Callback {

        // FWImageLoader is a singleton already
        private final WeakReference<FWImageLoader> loader;
        private final Uri uri;
        private final WeakReference<ImageView> target;
        private final Params params;

        RetryCallback(FWImageLoader loader, Uri uri, ImageView target, Params params) {
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
            LOG.warn("RetryCallback.onError() attempting retry for URI: " + uri, e);
            if (Ref.alive(target) && Ref.alive(loader)) {
                try {
                    loader.get().load(uri, target.get(), params);
                } catch (Throwable retryError) {
                    LOG.error("Failed to retry image loading for URI: " + uri, retryError);
                }
            } else {
                LOG.info("RetryCallback.onError() skipping retry due to dead references");
            }
        }
    }
    // Transformation wrapper removed for Picasso 3 migration

    // Removed custom RequestHandler; not used by current call sites and Picasso 3 API changed.


    private static OkHttpClient createHttpClient(Context context) {
        // Coil manages its own disk cache, so we don't need to set up OkHttp cache here
        OkHttpClient.Builder b = new OkHttpClient.Builder();
        OkHttpClient.Builder nullSslBuilder = OkHttpClientWrapper.configNullSsl(b);
        return nullSslBuilder.build();
    }

    // Cache configuration for Coil image loading

    private static final String IMAGE_CACHE = "image-cache";
    private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    private static File createDefaultCacheDir(Context context) {
        File cache = SystemUtils.getCacheDir(context, IMAGE_CACHE);
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
