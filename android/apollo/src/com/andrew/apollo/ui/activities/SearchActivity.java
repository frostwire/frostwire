/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2020, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.appcompat.widget.Toolbar;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.format.PrefixHighlighter;
import com.andrew.apollo.loaders.SearchLoader;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.MusicViewHolder;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;

import java.util.Locale;

/**
 * Provides the search interface for Apollo.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class SearchActivity extends AbstractActivity implements LoaderCallbacks<Cursor>,
        OnScrollListener, OnQueryTextListener, OnItemClickListener {
    /**
     * Grid view column count. ONE - list, TWO - normal grid
     */
    private static final int ONE = 1, TWO = 2;

    /**
     * The query
     */
    private String mFilterString;

    /**
     * Grid view
     */
    private GridView mGridView;

    /**
     * List view adapter
     */
    private SearchAdapter mAdapter;

    // Used the filter the user's music
    private SearchView mSearchView;

    public SearchActivity() {
        super(R.layout.apollo_activity_search);
    }

    @Override
    protected void initToolbar(Toolbar toolbar) {
        View v = LayoutInflater.from(this).
                inflate(R.layout.view_toolbar_title_subtitle_header, toolbar, false);
        setToolbarView(v);

        TextView title = findView(R.id.view_toolbar_header_title);
        title.setText(R.string.my_music);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        //mToken = MusicUtils.bindToService(this, this);

        // Get the query
        final String query = getIntent().getStringExtra(SearchManager.QUERY);
        mFilterString = !TextUtils.isEmpty(query) ? query : null;

        // Action bar subtitle
        setSubtitle(mFilterString);

        // Initialize the adapter
        mAdapter = new SearchAdapter(this);
        // Set the prefix
        mAdapter.setPrefix(mFilterString);
        // Initialize the list
        mGridView = findView(R.id.apollo_activity_search_grid_base);
        // Bind the data
        mGridView.setAdapter(mAdapter);
        // Recycle the data
        mGridView.setRecyclerListener(new RecycleHolder());
        // Speed up scrolling
        mGridView.setOnScrollListener(this);
        mGridView.setOnItemClickListener(this);
        if (ApolloUtils.isLandscape(this)) {
            mGridView.setNumColumns(TWO);
        } else {
            mGridView.setNumColumns(ONE);
        }
        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        final String query = intent.getStringExtra(SearchManager.QUERY);
        mFilterString = !TextUtils.isEmpty(query) ? query : null;
        // Set the prefix
        mAdapter.setPrefix(mFilterString);
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        getMenuInflater().inflate(R.menu.player_search, menu);

        // Filter the list the user is looking it via SearchView
        mSearchView = (SearchView) menu.findItem(R.id.menu_player_search).getActionView();
        mSearchView.setOnQueryTextListener(this);

        // Add voice search
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
        mSearchView.setSearchableInfo(searchableInfo);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        SearchLoader.SearchCursorParameters searchCursorParameters =
                SearchLoader.SearchCursorParameters.buildSearchCursorParameters(mFilterString);
        return new CursorLoader(this,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                searchCursorParameters.projection,
                searchCursorParameters.selection,
                searchCursorParameters.selectionArgs,
                null);
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        if (data == null || data.isClosed() || data.getCount() <= 0) {
            // Set the empty text
            TextView empty = findView(R.id.apollo_activity_search_text_empty);
            empty.setText(R.string.empty_search);
            mGridView.setEmptyView(empty);
            return;
        }
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        // When the search is "committed" by the user, then hide the keyboard so
        // the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
        }
        // Action bar subtitle
        setSubtitle(mFilterString);
        return true;
    }

    @Override
    public boolean onQueryTextChange(final String newText) {
        if (TextUtils.isEmpty(newText)) {
            return false;
        }
        // Called when the action bar search text has changed. Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        mFilterString = !TextUtils.isEmpty(newText) ? newText : null;
        // Set the prefix
        mAdapter.setPrefix(mFilterString);
        getLoaderManager().restartLoader(0, null, this);
        return true;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        Cursor cursor = mAdapter.getCursor();
        cursor.moveToPosition(position);
        if (cursor.isBeforeFirst() || cursor.isAfterLast()) {
            return;
        }
        // Get the MIME type
        final String mimeType = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));

        // If it's an artist, open the artist profile
        if ("artist".equals(mimeType)) {
            NavUtils.openArtistProfile(this,
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)), null);
        } else if ("album".equals(mimeType)) {
            // If it's an album, open the album profile
            int albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID);
            NavUtils.openAlbumProfile(this,
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)),
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)),
                    cursor.getLong(albumId),
                    MusicUtils.getSongListForAlbum(this, albumId));
        } else if (position >= 0 && id >= 0) {
            // If it's a song, play it and leave
            final long[] list = new long[]{
                    id
            };
            if (MusicPlaybackService.getInstance() == null) {
                Context context = parent.getContext();
                MusicUtils.startMusicPlaybackService(
                        context,
                        MusicUtils.buildStartMusicPlaybackServiceIntent(context),
                        () -> MusicUtils.playAll(list, 0, MusicUtils.isShuffleEnabled()));
            } else {
                MusicUtils.playAll(list, 0, MusicUtils.isShuffleEnabled());
            }
        }

        // Close it up
        cursor.close();
        // All done
        finish();
    }

    /**
     * Used to populate the list view with the search results.
     */
    private static final class SearchAdapter extends CursorAdapter {

        /**
         * Number of views (ImageView and TextView)
         */
        private static final int VIEW_TYPE_COUNT = 2;

        /**
         * Image cache and image fetcher
         */
        private final ImageFetcher mImageFetcher;

        /**
         * Highlights the query
         */
        private final PrefixHighlighter mHighlighter;

        /**
         * The prefix that's highlighted
         */
        private char[] mPrefix;

        /**
         * Constructor for <code>SearchAdapter</code>
         *
         * @param context The {@link Context} to use.
         */
        SearchAdapter(final Activity context) {
            super(context, null, false);
            // Initialize the cache & image fetcher
            mImageFetcher = ApolloUtils.getImageFetcher(context);
            // Create the prefix highlighter
            mHighlighter = new PrefixHighlighter(context);
        }

        @Override
        public void bindView(final View convertView, final Context context, final Cursor cursor) {
            /* Recycle ViewHolder's items */
            MusicViewHolder holder = (MusicViewHolder) convertView.getTag();
            if (holder == null) {
                holder = new MusicViewHolder(convertView);
                convertView.setTag(holder);
            }

            // Get the MIME type
            final String mimeType = cursor.getString(cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));

            if ("artist".equals(mimeType)) {
                holder.mImage.get().setScaleType(ScaleType.CENTER_CROP);

                // Get the artist name
                final String artist = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST));
                holder.mLineOne.get().setText(artist);

                // Get the album count
                //final int albumCount = cursor.getInt(cursor.getColumnIndexOrThrow("data1"));
                holder.mLineTwo.get().setVisibility(View.INVISIBLE);//setText(
                //MusicUtils.makeLabel(context, R.plurals.Nalbums, albumCount));

                // Get the song count
                //final int songCount = cursor.getInt(cursor.getColumnIndexOrThrow("data2"));
                holder.mLineThree.get().setVisibility(View.INVISIBLE); //setText(
                //MusicUtils.makeLabel(context, R.plurals.Nsongs, songCount));

                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(artist, holder.mImage.get());

                // Highlight the query
                mHighlighter.setText(holder.mLineOne.get(), artist, mPrefix);
            } else if ("album".equals(mimeType)) {
                holder.mImage.get().setScaleType(ScaleType.FIT_XY);

                // Get the Id of the album
                final long id = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));

                // Get the album name
                final String album = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
                holder.mLineOne.get().setText(album);

                // Get the artist name
                final String artist = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST));
                holder.mLineTwo.get().setText(artist);

                // Asynchronously load the album images into the adapter
                mImageFetcher.loadAlbumImage(artist, album, id, holder.mImage.get());

                // Highlight the query
                mHighlighter.setText(holder.mLineOne.get(), album, mPrefix);

            } else if (mimeType != null && (mimeType.startsWith("audio/") || "application/ogg".equals(mimeType)
                    || "application/x-ogg".equals(mimeType))) {
                holder.mImage.get().setScaleType(ScaleType.FIT_XY);
                holder.mImage.get().setImageResource(R.drawable.header_temp);

                // Get the track name
                final String track = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                holder.mLineOne.get().setText(track);

                // Get the album name
                final String album = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                holder.mLineTwo.get().setText(album);

                final String artist = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(artist, holder.mImage.get());
                holder.mLineThree.get().setText(artist);

                // Highlight the query
                mHighlighter.setText(holder.mLineOne.get(), track, mPrefix);
            }
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            return ((Activity) context).getLayoutInflater().inflate(
                    R.layout.list_item_detailed_no_background, parent, false);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        /**
         * @param pause True to temporarily pause the disk cache, false
         *              otherwise.
         */
        void setPauseDiskCache(final boolean pause) {
            if (mImageFetcher != null) {
                mImageFetcher.setPauseDiskCache(pause);
            }
        }

        /**
         * @param prefix The query to filter.
         */
        public void setPrefix(final CharSequence prefix) {
            if (!TextUtils.isEmpty(prefix)) {
                mPrefix = prefix.toString().toUpperCase(Locale.getDefault()).toCharArray();
            } else {
                mPrefix = null;
            }
        }
    }

    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
                         final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

    private void setSubtitle(String s) {
        if (!TextUtils.isEmpty(s)) {
            TextView subtitle = findView(R.id.view_toolbar_header_subtitle);
            if (subtitle != null) {
                subtitle.setVisibility(View.VISIBLE);
                subtitle.setText("\"" + s + "\"");
            }
        }
    }
}
