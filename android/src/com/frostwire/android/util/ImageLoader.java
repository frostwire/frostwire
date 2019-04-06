/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.os.Looper;
import android.os.StatFs;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.frostwire.android.gui.MainApplication;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.OKHTTPClient;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Picasso.Builder;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.RequestHandler;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashSet;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ImageLoader {

    private static final Logger LOG = Logger.getLogger(ImageLoader.class);

    private static final String SCHEME_IMAGE = "image";

    private static final String SCHEME_IMAGE_SLASH = SCHEME_IMAGE + "://";

    private static final String APPLICATION_AUTHORITY = "application";

    private static final String ALBUM_AUTHORITY = "album";

    private static final String ARTIST_AUTHORITY = "artist";

    private static final String METADATA_AUTHORITY = "metadata";

    private static final Uri APPLICATION_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + APPLICATION_AUTHORITY);

    private static final Uri ALBUM_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + ALBUM_AUTHORITY);

    private static final Uri ARTIST_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + ARTIST_AUTHORITY);

    private static final Uri METADATA_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + METADATA_AUTHORITY);

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
            Cursor cursor = context.getContentResolver().query(albumUri, new String[]{MediaStore.Audio.AlbumColumns.ALBUM_ART}, null, null, null);
            try {
                LOG.info("Using album_art path for uri: " + albumUri);
                if (cursor != null && cursor.moveToFirst()) {
                    String albumArt = cursor.getString(0);
                    if (albumArt != null) {
                        bitmap = BitmapFactory.decodeFile(albumArt);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

        } catch (Throwable e) {
            LOG.error("Error getting album art", e);
        }
        return bitmap;
    }

    public static Uri getApplicationArtUri(String packageName) {
        return Uri.withAppendedPath(APPLICATION_THUMBNAILS_URI, packageName);
    }

    public static Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(ALBUM_THUMBNAILS_URI, albumId);
    }

    public static Uri getArtistArtUri(String artistName) {
        return Uri.withAppendedPath(ARTIST_THUMBNAILS_URI, artistName);
    }

    public static Uri getMetadataArtUri(Uri uri) {
        return Uri.withAppendedPath(METADATA_THUMBNAILS_URI, Uri.encode(uri.toString()));
    }

    private ImageLoader(Context context) {
        Builder picassoBuilder = new Builder(context).
                addRequestHandler(new ImageRequestHandler(context.getApplicationContext())).
                downloader(new ImageLoaderDownloader(createHttpClient(context.getApplicationContext())));
        if (DEBUG_ERRORS) {
            picassoBuilder.listener((picasso, uri, exception) -> LOG.error("ImageLoader::onImageLoadFailed(" + uri + ")", exception));
        }
        this.picasso = picassoBuilder.build();
        this.picasso.setIndicatorsEnabled(DEBUG_ERRORS);
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
        Asyncs.async(asyncLoader::run);
    }

    private static class AsyncLoader implements Runnable {

        private final int resourceId;
        private final Uri uri;
        private final WeakReference<ImageView> targetRef;
        private final Params p;
        private final boolean shutdown;
        private final WeakReference<Picasso> picasso;

        public <T> AsyncLoader(int resourceId, Uri uri, WeakReference<ImageView> targetRef, Params p, boolean shutdown, WeakReference<Picasso> picassoRef) {
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
            if (!(p.callback instanceof RetryCallback) && // don't ask this recursively
                    Debug.hasContext(p.callback)) {
                throw new RuntimeException("Possible context leak");
            }
            if (Debug.hasContext(p.filter)) {
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
            if (p.filter != null) {
                rc.transform(new FilterWrapper(p.filter));
            }
            new Handler(Looper.getMainLooper()).post(
                    () -> {
                        if (!Ref.alive(targetRef)) {
                            LOG.info("ImageLoader.load() main thread update cancelled, ImageView target reference lost.");
                            return;
                        }
                        if (p.callback != null) {
                            rc.into(targetRef.get(), new CallbackWrapper(p.callback));
                        } else {
                            rc.into(targetRef.get());
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
        picasso.shutdown();
    }

    public static void start(MainApplication mainApplication) {
        async(mainApplication, ImageLoader::startImageLoaderBackground);
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

        void onError(Exception e);
    }

    public interface Filter {

        Bitmap filter(Bitmap source);

        String params();
    }

    private static final class CallbackWrapper implements
            com.squareup.picasso.Callback {

        private final Callback cb;

        CallbackWrapper(Callback cb) {
            this.cb = cb;
        }

        @Override
        public void onSuccess() {
            cb.onSuccess();
        }

        @Override
        public void onError(Exception e) {
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
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(Exception e) {
            if (Ref.alive(target) && Ref.alive(loader)) {
                params.callback = null; // avoid recursion
                loader.get().load(uri, target.get(), params);
            }
        }
    }

    private static final class FilterWrapper implements Transformation {

        private final Filter filter;

        FilterWrapper(Filter filter) {
            this.filter = filter;
        }

        @Override
        public Bitmap transform(Bitmap bitmap) {
            Bitmap transformed = filter.filter(bitmap);
            bitmap.recycle();
            return transformed;
        }

        @Override
        public String key() {
            return filter.params();
        }
    }

    private static final class ImageRequestHandler extends RequestHandler {

        private final Context context;
        private final HashSet<String> failed;

        ImageRequestHandler(Context context) {
            this.context = context;
            this.failed = new HashSet<>();
        }

        @Override
        public boolean canHandleRequest(Request data) {
            return !(data == null || data.uri == null) && SCHEME_IMAGE.equals(data.uri.getScheme());
        }

        @Override
        public Result load(Request data, int networkPolicy) {
            String authority = data.uri.getAuthority();
            if (APPLICATION_AUTHORITY.equals(authority)) {
                return loadApplication(data.uri);
            } else if (ALBUM_AUTHORITY.equals(authority)) {
                return loadAlbum(data.uri);
            } else if (ARTIST_AUTHORITY.equals(authority)) {
                return loadFirstArtistAlbum(data.uri);
            } else if (METADATA_AUTHORITY.equals(authority)) {
                return extractMetadata(data.uri);
            }
            return null;
        }

        private Result loadApplication(Uri uri) {
            Result result;
            String packageName = uri.getLastPathSegment();
            PackageManager pm = context.getPackageManager();
            try {
                BitmapDrawable icon = (BitmapDrawable) pm.getApplicationIcon(packageName);
                Bitmap bmp = icon.getBitmap();
                result = new Result(bmp, Picasso.LoadedFrom.DISK);
            } catch (NameNotFoundException e) {
                result = null;
            }
            return result;
        }

        private Result loadAlbum(Uri uri) {
            String albumId = uri.getLastPathSegment();
            if (albumId == null || albumId.equals("-1")) {
                return null;
            }
            Bitmap bitmap = getAlbumArt(context, albumId);
            return (bitmap != null) ? new Result(bitmap, Picasso.LoadedFrom.DISK) : null;
        }

        private Result loadFirstArtistAlbum(Uri uri) {
            String artistName = uri.getLastPathSegment();
            long albumId = getFirstAlbumIdForArtist(context, artistName);
            if (albumId == -1) {
                return null;
            }
            Bitmap bitmap = getAlbumArt(context, String.valueOf(albumId));
            return bitmap != null ? new Result(bitmap, Picasso.LoadedFrom.DISK) : null;
        }

        /**
         * Returns the ID for the first album given an artist name.
         *
         * @param context    The {@link Context} to use.
         * @param artistName The name of the artist
         * @return The ID for an album.
         */
        private static long getFirstAlbumIdForArtist(Context context, String artistName) {
            int id = -1;
            try {
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[]{
                                BaseColumns._ID
                        }, MediaStore.Audio.AlbumColumns.ARTIST + "=?", new String[]{
                                artistName
                        }, BaseColumns._ID);
                if (cursor != null) {
                    cursor.moveToFirst();
                    if (!cursor.isAfterLast()) {
                        id = cursor.getInt(0);
                    }
                    cursor.close();
                }
            } catch (Throwable e) {
                LOG.error("Error getting first album id for artist: " + artistName, e);
            }
            return id;
        }

        private Result extractMetadata(Uri uri) {
            String seg = Uri.decode(uri.getLastPathSegment());
            if (failed.contains(seg)) {
                return null;
            }
            uri = Uri.parse(seg);
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = null;
            try {
                LOG.info("Using MediaMetadataRetriever (costly operation) for uri: " + uri);
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(context, uri);
                byte[] picture = retriever.getEmbeddedPicture();
                if (picture != null) {
                    bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                }
            } catch (Throwable e) {
                LOG.error("Error extracting album art", e);
            } finally {
                if (retriever != null) {
                    retriever.release();
                }
            }
            if (bitmap != null) {
                return new Result(bitmap, Picasso.LoadedFrom.DISK);
            } else {
                failed.add(seg);
                return null;
            }
        }
    }

    private static OkHttpClient createHttpClient(Context context) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);

        Cache cache = new Cache(cacheDir, maxSize);

        OkHttpClient.Builder b = new OkHttpClient.Builder();
        b = b.cache(cache);
        b = OKHTTPClient.configNullSsl(b);
        return b.build();
    }

    // ------- below code copied from com.squareup.picasso.Utils -------
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
        } catch (IllegalArgumentException ignored) {
        } catch (NoSuchMethodError ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
    }
}
