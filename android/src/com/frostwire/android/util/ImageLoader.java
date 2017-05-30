/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
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

/**
 * @author gubatron
 * @author aldenml
 */
public final class ImageLoader {

    private static final Logger LOG = Logger.getLogger(ImageLoader.class);

    private static final int MIN_DISK_CACHE_SIZE = 8 * 1024 * 1024; // 8MB
    private static final int MAX_DISK_CACHE_SIZE = 64 * 1024 * 1024; // 64MB

    private static final String SCHEME_IMAGE = "image";

    private static final String SCHEME_IMAGE_SLASH = SCHEME_IMAGE + "://";

    private static final String APPLICATION_AUTHORITY = "application";

    private static final String ALBUM_AUTHORITY = "album";

    private static final String ARTIST_AUTHORITY = "artist";

    private static final String METADATA_AUTHORITY = "metadata";

    public static final Uri APPLICATION_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + APPLICATION_AUTHORITY);

    public static final Uri ALBUM_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + ALBUM_AUTHORITY);

    private static final Uri ARTIST_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + ARTIST_AUTHORITY);

    private static final Uri METADATA_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + METADATA_AUTHORITY);

    private static final boolean DEBUG_ERRORS = false;

    private final ImageCache cache;
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

    public static Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(ImageLoader.ALBUM_THUMBNAILS_URI, albumId);
    }

    public static Uri getArtistArtUri(String artistName) {
        return Uri.withAppendedPath(ImageLoader.ARTIST_THUMBNAILS_URI, artistName);
    }

    public static Uri getMetadataArtUri(Uri uri) {
        return Uri.withAppendedPath(ImageLoader.METADATA_THUMBNAILS_URI, Uri.encode(uri.toString()));
    }

    private ImageLoader(Context context) {
        File directory = SystemUtils.getCacheDir(context, "picasso");
        long diskSize = SystemUtils.calculateDiskCacheSize(directory, MIN_DISK_CACHE_SIZE, MAX_DISK_CACHE_SIZE);
        int memSize = SystemUtils.calculateMemoryCacheSize(context);
        this.cache = new ImageCache(directory, diskSize, memSize);
        Builder picassoBuilder = new Builder(context).
                addRequestHandler(new ImageRequestHandler(context.getApplicationContext())).
                memoryCache(cache).
                executor(Engine.instance().getThreadPool());
        if (DEBUG_ERRORS) {
            picassoBuilder.listener(new Picasso.Listener() {
                @Override
                public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                    LOG.error("ImageLoader::onImageLoadFailed(" + uri + ")", exception);
                }
            });
        }
        this.picasso = picassoBuilder.build();
        this.picasso.setIndicatorsEnabled(DEBUG_ERRORS);
    }

    public void loadBitmapAsync(Uri uri, ImageView target, int targetWidth, int targetHeight,
                                int placeholderResId, boolean useDiskCache, boolean noFade,
                                Callback callback) {
        if (shutdown) {
            return;
        }

        if (uri == null) {
            throw new IllegalArgumentException("Uri can't be null");
        }
        final RequestCreator requestCreator = picasso.load(uri);

        if (!useDiskCache) {
            requestCreator.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE);
        }
        if (noFade) {
            requestCreator.noFade();
        }
        requestCreator.placeholder(placeholderResId);
        requestCreator.resize(targetWidth, targetHeight);
        requestCreator.centerInside();

        if (callback != null) {
            if (Debug.hasContext(callback)) {
                throw new RuntimeException("Possible context leak");
            }
            requestCreator.into(target, new CallbackWrapper(callback));
        } else {
            requestCreator.into(target);
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

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(Uri uri, Uri uriRetry, ImageView target,
                     int targetWidth, int targetHeight) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.noFade = true;

        if (uriRetry != null) {
            p.callback = new RetryCallback(this, uriRetry, target, p);
        }

        load(uri, target, p);
    }

    public void load(Uri uri, ImageView target, int placeholderResId) {
        Params p = new Params();
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(uri, target, p);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight, int placeholderResId) {
        Params p = new Params();
        p.targetWidth = targetWidth;
        p.targetHeight = targetHeight;
        p.placeholderResId = placeholderResId;
        p.noFade = true;
        load(uri, target, p);
    }

    private void load(Uri uri, ImageView target, Params p) {
        if (shutdown) {
            return;
        }

        if (uri == null) {
            throw new IllegalArgumentException("Uri can't be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target image view can't be null");
        }
        if (p == null) {
            throw new IllegalArgumentException("Params to load image can't be null");
        }

        RequestCreator rc = picasso.load(uri);

        if (p.targetWidth != 0 || p.targetHeight != 0) rc.resize(p.targetWidth, p.targetHeight);
        if (p.placeholderResId != 0) rc.placeholder(p.placeholderResId);
        if (p.fit) rc.fit();
        if (p.noFade) rc.noFade();

        if (p.noCache) {
            rc.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE);
            rc.networkPolicy(NetworkPolicy.NO_CACHE, NetworkPolicy.NO_STORE);
        }

        if (p.filter != null) {
            rc.transform(new FilterWrapper(p.filter));
        }

        if (p.callback != null) {
            rc.into(target, new CallbackWrapper(p.callback));
        } else {
            rc.into(target);
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
        cache.clear();
    }

    public void shutdown() {
        shutdown = true;
        picasso.shutdown();
    }

    public static final class Params {
        public int targetWidth = 0;
        public int targetHeight = 0;
        public int placeholderResId = 0;
        public boolean fit = false;
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
        private final ImageLoader loader;
        private final Uri uri;
        private final WeakReference<ImageView> target;
        private final Params params;

        RetryCallback(ImageLoader loader, Uri uri, ImageView target, Params params) {
            this.loader = loader;
            this.uri = uri;
            this.target = Ref.weak(target);
            this.params = params;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(Exception e) {
            if (Ref.alive(target)) {
                params.callback = null; // avoid recursion
                loader.load(uri, target.get(), params);
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
        public Result load(Request data, int networkPolicy) throws IOException {
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

        private Result loadApplication(Uri uri) throws IOException {
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

        private Result loadAlbum(Uri uri) throws IOException {
            String albumId = uri.getLastPathSegment();
            if (albumId == null || albumId.equals("-1")) {
                return null;
            }
            Bitmap bitmap = getAlbumArt(context, albumId);
            return (bitmap != null) ? new Result(bitmap, Picasso.LoadedFrom.DISK) : null;
        }

        private Result loadFirstArtistAlbum(Uri uri) throws IOException {
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

        private Result extractMetadata(Uri uri) throws IOException {
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
}
