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

package com.andrew.apollo.appwidgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.andrew.apollo.Config;
import com.frostwire.android.R;
import com.andrew.apollo.cache.ImageCache;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.provider.RecentStore.RecentStoreColumns;

/**
 * This class is used to build the recently listened list for the
 * {@link RecentWidgetProvicer}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@TargetApi(11)
public class RecentWidgetService extends RemoteViewsService {

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteViewsFactory onGetViewFactory(final Intent intent) {
        return new WidgetRemoteViewsFactory(getApplicationContext());
    }

    /**
     * This is the factory that will provide data to the collection widget.
     */
    private static final class WidgetRemoteViewsFactory implements
            RemoteViewsService.RemoteViewsFactory {
        /**
         * Number of views (ImageView and TextView)
         */
        private static final int VIEW_TYPE_COUNT = 2;

        /**
         * The context to use
         */
        private final Context mContext;

        /**
         * Image cache
         */
        private final ImageFetcher mFetcher;

        /**
         * Recents db
         */
        private final RecentStore mRecentsStore;

        /**
         * Cursor to use
         */
        private Cursor mCursor;

        /**
         * Remove views
         */
        private RemoteViews mViews;

        /**
         * Constructor of <code>WidgetRemoteViewsFactory</code>
         * 
         * @param context The {@link Context} to use.
         */
        public WidgetRemoteViewsFactory(final Context context) {
            // Get the context
            mContext = context;
            // Initialze the image cache
            mFetcher = ImageFetcher.getInstance(context);
            mFetcher.setImageCache(ImageCache.getInstance(context));
            // Initialze the recents store
            mRecentsStore = RecentStore.getInstance(context);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount() {
            // Check for errors
            if (mCursor == null || mCursor.isClosed() || mCursor.getCount() <= 0) {
                return 0;
            }
            return mCursor.getCount();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(final int position) {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RemoteViews getViewAt(final int position) {
            mCursor.moveToPosition(position);

            // Create the remote views
            mViews = new RemoteViews(mContext.getPackageName(), R.layout.app_widget_recents_items);

            // Copy the album id
            final long id = mCursor.getLong(mCursor
                    .getColumnIndexOrThrow(RecentStoreColumns.ID));

            // Copy the album name
            final String albumName = mCursor.getString(mCursor
                    .getColumnIndexOrThrow(RecentStoreColumns.ALBUMNAME));

            // Copy the artist name
            final String artist = mCursor.getString(mCursor
                    .getColumnIndexOrThrow(RecentStoreColumns.ARTISTNAME));

            // Set the album names
            mViews.setTextViewText(R.id.app_widget_recents_line_one, albumName);
            // Set the artist names
            mViews.setTextViewText(R.id.app_widget_recents_line_two, artist);
            // Set the album art
            Bitmap bitmap = mFetcher.getCachedArtwork(albumName, artist, id);
            if (bitmap != null) {
                mViews.setImageViewBitmap(R.id.app_widget_recents_base_image, bitmap);
            } else {
                mViews.setImageViewResource(R.id.app_widget_recents_base_image,
                        R.drawable.default_artwork);
            }

            // Open the profile of the touched album
            final Intent profileIntent = new Intent();
            final Bundle profileExtras = new Bundle();
            profileExtras.putLong(Config.ID, id);
            profileExtras.putString(Config.NAME, albumName);
            profileExtras.putString(Config.ARTIST_NAME, artist);
            profileExtras.putString(RecentWidgetProvider.SET_ACTION,
                    RecentWidgetProvider.OPEN_PROFILE);
            profileIntent.putExtras(profileExtras);
            mViews.setOnClickFillInIntent(R.id.app_widget_recents_items, profileIntent);

            // Play the album when the artwork is touched
            final Intent playAlbum = new Intent();
            final Bundle playAlbumExtras = new Bundle();
            playAlbumExtras.putLong(Config.ID, id);
            playAlbumExtras.putString(RecentWidgetProvider.SET_ACTION,
                    RecentWidgetProvider.PLAY_ALBUM);
            playAlbum.putExtras(playAlbumExtras);
            mViews.setOnClickFillInIntent(R.id.app_widget_recents_base_image, playAlbum);
            return mViews;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasStableIds() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDataSetChanged() {
            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
                mCursor = null;
            }
            mCursor = mRecentsStore.getReadableDatabase().query(
                    RecentStoreColumns.NAME,
                    new String[] {
                            RecentStoreColumns.ID + " as id", RecentStoreColumns.ID,
                            RecentStoreColumns.ALBUMNAME, RecentStoreColumns.ARTISTNAME,
                            RecentStoreColumns.ALBUMSONGCOUNT, RecentStoreColumns.ALBUMYEAR,
                            RecentStoreColumns.TIMEPLAYED
                    }, null, null, null, null, RecentStoreColumns.TIMEPLAYED + " DESC");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDestroy() {
            closeCursor();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RemoteViews getLoadingView() {
            // Nothing to do
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreate() {
            // Nothing to do
        }

        private void closeCursor() {
            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
                mCursor = null;
            }
        }
    }
}
