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

package com.andrew.apollo.ui.activities;

import static com.andrew.apollo.utils.MusicUtils.mService;

import android.app.Activity;
import android.app.ActionBar;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.CursorLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView.ScaleType;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.andrew.apollo.IApolloService;
import com.frostwire.android.R;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.format.PrefixHighlighter;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.MusicHolder;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.MusicUtils.ServiceToken;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.ThemeUtils;

import java.util.Locale;

/**
 * Provides the search interface for Apollo.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SearchActivity extends Activity implements LoaderCallbacks<Cursor>,
        OnScrollListener, OnQueryTextListener, OnItemClickListener, ServiceConnection {
    /**
     * Grid view column count. ONE - list, TWO - normal grid
     */
    private static final int ONE = 1, TWO = 2;

    /**
     * The service token
     */
    private ServiceToken mToken;

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

    /**
     * Theme resources
     */
    private ThemeUtils mResources;

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialze the theme resources
        mResources = new ThemeUtils(this);
        // Set the overflow style
        mResources.setOverflowStyle(this);

        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Theme the action bar
        final ActionBar actionBar = getActionBar();
        mResources.themeActionBar(actionBar, getString(R.string.app_name), getWindow());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(R.color.transparent);

        // Set the layout
        setContentView(R.layout.grid_base);

        // Give the background a little UI
        final FrameLayout background = (FrameLayout)findViewById(R.id.grid_base_container);
        background.setBackgroundDrawable(getResources().getDrawable(R.drawable.pager_background));

        // Get the query
        final String query = getIntent().getStringExtra(SearchManager.QUERY);
        mFilterString = !TextUtils.isEmpty(query) ? query : null;

        // Action bar subtitle
        mResources.setSubtitle("\"" + mFilterString + "\"");

        // Initialize the adapter
        mAdapter = new SearchAdapter(this);
        // Set the prefix
        mAdapter.setPrefix(mFilterString);
        // Initialze the list
        mGridView = (GridView)findViewById(R.id.grid_base);
        // Bind the data
        mGridView.setAdapter(mAdapter);
        // Recycle the data
        mGridView.setRecyclerListener(new RecycleHolder());
        // Seepd up scrolling
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        final String query = intent.getStringExtra(SearchManager.QUERY);
        mFilterString = !TextUtils.isEmpty(query) ? query : null;
        // Set the prefix
        mAdapter.setPrefix(mFilterString);
        getLoaderManager().restartLoader(0, null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        getMenuInflater().inflate(R.menu.search, menu);
        // Theme the search icon
        mResources.setSearchIcon(menu);

        // Filter the list the user is looking it via SearchView
        mSearchView = (SearchView)menu.findItem(R.id.menu_search).getActionView();
        mSearchView.setOnQueryTextListener(this);

        // Add voice search
        final SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
        mSearchView.setSearchableInfo(searchableInfo);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        final Uri uri = Uri.parse("content://media/external/audio/search/fancy/"
                + Uri.encode(mFilterString));
        final String[] projection = new String[] {
                BaseColumns._ID, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Media.TITLE, "data1", "data2"
        };
        return new CursorLoader(this, uri, projection, null, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        if (data == null || data.isClosed() || data.getCount() <= 0) {
            // Set the empty text
            final TextView empty = (TextView)findViewById(R.id.empty);
            empty.setText(getString(R.string.empty_search));
            mGridView.setEmptyView(empty);
            return;
        }
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onQueryTextSubmit(final String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        // When the search is "committed" by the user, then hide the keyboard so
        // the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            final InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
        }
        // Action bar subtitle
        mResources.setSubtitle("\"" + mFilterString + "\"");
        return true;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)));
        } else if ("album".equals(mimeType)) {
            // If it's an album, open the album profile
            NavUtils.openAlbumProfile(this,
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)),
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)));
        } else if (position >= 0 && id >= 0) {
            // If it's a song, play it and leave
            final long[] list = new long[] {
                id
            };
            MusicUtils.playAll(this, list, 0, false);
        }

        // Close it up
        cursor.close();
        cursor = null;
        // All done
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = IApolloService.Stub.asInterface(service);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
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
        public SearchAdapter(final Activity context) {
            super(context, null, false);
            // Initialize the cache & image fetcher
            mImageFetcher = ApolloUtils.getImageFetcher(context);
            // Create the prefix highlighter
            mHighlighter = new PrefixHighlighter(context);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void bindView(final View convertView, final Context context, final Cursor cursor) {
            /* Recycle ViewHolder's items */
            MusicHolder holder = (MusicHolder)convertView.getTag();
            if (holder == null) {
                holder = new MusicHolder(convertView);
                convertView.setTag(holder);
            }

            // Get the MIME type
            final String mimetype = cursor.getString(cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));

            if (mimetype.equals("artist")) {
                holder.mImage.get().setScaleType(ScaleType.CENTER_CROP);

                // Get the artist name
                final String artist = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST));
                holder.mLineOne.get().setText(artist);

                // Get the album count
                final int albumCount = cursor.getInt(cursor.getColumnIndexOrThrow("data1"));
                holder.mLineTwo.get().setText(
                        MusicUtils.makeLabel(context, R.plurals.Nalbums, albumCount));

                // Get the song count
                final int songCount = cursor.getInt(cursor.getColumnIndexOrThrow("data2"));
                holder.mLineThree.get().setText(
                        MusicUtils.makeLabel(context, R.plurals.Nsongs, songCount));

                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(artist, holder.mImage.get());

                // Highlght the query
                mHighlighter.setText(holder.mLineOne.get(), artist, mPrefix);
            } else if (mimetype.equals("album")) {
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
                // Asynchronously load the artist image into the adapter
                mImageFetcher.loadArtistImage(artist, holder.mBackground.get());

                // Highlght the query
                mHighlighter.setText(holder.mLineOne.get(), album, mPrefix);

            } else if (mimetype.startsWith("audio/") || mimetype.equals("application/ogg")
                    || mimetype.equals("application/x-ogg")) {
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
                mImageFetcher.loadArtistImage(artist, holder.mBackground.get());
                holder.mLineThree.get().setText(artist);

                // Highlght the query
                mHighlighter.setText(holder.mLineOne.get(), track, mPrefix);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            return ((Activity)context).getLayoutInflater().inflate(
                    R.layout.list_item_detailed, parent, false);
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
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        /**
         * @param pause True to temporarily pause the disk cache, false
         *            otherwise.
         */
        public void setPauseDiskCache(final boolean pause) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

}
