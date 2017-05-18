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
import com.squareup.picasso.Callback;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Picasso.Builder;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.RequestHandler;
import com.squareup.picasso.Target;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.IOException;
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

    public static final Uri ARTIST_THUMBNAILS_URI = Uri.parse(SCHEME_IMAGE_SLASH + ARTIST_AUTHORITY);

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
     * it is here to be used only (so far) on the notification window view and the RC Interface (things like Lock Screen, Android Wear),
     * which run on another process space. If you try to use a cached image there, you will get some
     * nasty exceptions, therefore you will need this.
     * <p/>
     * For loading album art inside the application Activities/Views/Fragments, take a look at FileListAdapter and how it uses the ImageLoader.
     *
     * @param context
     * @param albumId
     * @return
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

    public static Uri getAlbumArtUri(final long albumId) {
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

    /**
     *
     * @param resizedWidth // if 0 is passed, proportions with respect to height will be calculated. if -1 is passed image will be fitted to view's dimensions
     * @param resizedHeight // if 0 is passed, proportions with respect to height will be calculated. if -1 is passed image will be fitted to view's dimensions
     * @param imageUri
     * @param placeHolderId
     * @param view
     * @param callback
     */
    public void loadBitmapAsync(final int resizedWidth, final int resizedHeight, final Uri imageUri, int placeHolderId, boolean useDiskCache, ImageView view, final Callback callback) {
        if (imageUri == null) {
            throw new IllegalArgumentException("Uri can't be null");
        }
        final RequestCreator requestCreator = picasso.load(imageUri);

        if (!useDiskCache) {
            requestCreator.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE);
        }
        requestCreator.placeholder(placeHolderId);
        if (resizedWidth == -1 && resizedWidth == -1) {
            requestCreator.fit();
            requestCreator.centerInside();
        } else {
            requestCreator.resize(resizedWidth, resizedHeight);
        }

        if (callback != null) {
            requestCreator.into(view, callback);
        } else {
            requestCreator.into(view);
        }
    }

    public void load(final Uri primaryUri, final Uri secondaryUri, final Filter filter, final ImageView imageView, final boolean cache) {
        Transformation transformation = new Transformation() {
            @Override
            public Bitmap transform(Bitmap source) {
                Bitmap transformed = filter.filter(source);
                source.recycle();
                return transformed;
            }

            @Override
            public String key() {
                return filter.params();
            }
        };
        Callback.EmptyCallback callback = new Callback.EmptyCallback() {
            @Override
            public void onError() {
                if (secondaryUri != null) {
                    load(secondaryUri, filter, imageView, cache);
                }
            }
        };
        if (cache) {
            picasso.load(primaryUri).fit().transform(transformation).into(imageView, callback);
        } else {
            picasso.load(primaryUri).fit().transform(transformation)
                    .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .networkPolicy(NetworkPolicy.NO_CACHE, NetworkPolicy.NO_STORE).into(imageView, callback);
        }
    }

    public void load(Uri uri, Filter filter, ImageView imageView, boolean cache) {
        load(uri, null, filter, imageView, cache);
    }

    public void load(Uri uri, ImageView target) {
        picasso.load(uri).noFade().into(target);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight) {
        if (!shutdown) {
            picasso.load(uri).noFade().resize(targetWidth, targetHeight).into(target);
        }
    }

    public void load(final Uri uri, final Uri uriRetry, final ImageView target, final int targetWidth, final int targetHeight) {
        if (!shutdown) {
            picasso.load(uri).noFade().resize(targetWidth, targetHeight).into(target, new Callback.EmptyCallback() {
                @Override
                public void onError() {
                    load(uriRetry, target, targetWidth, targetHeight);
                }
            });
        }
    }

    public void load(Uri uri, ImageView target, int placeholderResId) {
        if (!shutdown) {
            picasso.load(uri).noFade().placeholder(placeholderResId).into(target);
        }
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight, int placeholderResId) {
        if (!shutdown) {
            picasso.load(uri).noFade().resize(targetWidth, targetHeight).placeholder(placeholderResId).into(target);
        }
    }

    public void load(Uri uri, Target target) {
        if (!shutdown) {
            picasso.load(uri).into(target);
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

    public interface Filter {
        Bitmap filter(Bitmap source);

        String params();
    }

    private static final class ImageRequestHandler extends RequestHandler {

        private final Context context;
        private final HashSet<String> failed;

        public ImageRequestHandler(Context context) {
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
            return (bitmap != null) ? new Result(bitmap, Picasso.LoadedFrom.DISK) : null;
        }

        /**
         * Returns the ID for the first album given an artist name.
         *
         * @param context    The {@link Context} to use.
         * @param artistName The name of the artist
         * @return The ID for an album.
         */
        private static long getFirstAlbumIdForArtist(final Context context, final String artistName) {
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
