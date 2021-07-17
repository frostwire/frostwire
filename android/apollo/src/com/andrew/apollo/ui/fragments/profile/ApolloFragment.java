/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *            Jose Molina (@votaguz)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments.profile;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.adapters.ApolloFragmentAdapter;
import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.Album;
import com.andrew.apollo.model.Artist;
import com.andrew.apollo.model.Genre;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.devspark.appmsg.AppMsg;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.WriteSettingsPermissionActivityHelper;
import com.frostwire.android.util.SystemUtils;

import java.util.List;

/**
 * Created on 1/26/16 in a plane.
 *
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 * @author Jose Molina (@votaguz)
 */
public abstract class ApolloFragment<T extends ApolloFragmentAdapter<I>, I>
        extends Fragment implements
        LoaderManager.LoaderCallbacks<List<I>>,
        AdapterView.OnItemClickListener,
        MusicStateListener {

    private final int GROUP_ID;
    /**
     * LoaderCallbacks identifier
     */
    private final int LOADER_ID;
    /**
     * The list view
     */
    protected ListView mListView;

    protected TextView mEmptyTextView;

    /**
     * The grid view
     */
    protected GridView mGridView;

    /**
     * The adapter for the list
     */
    protected T mAdapter;
    /**
     * Represents a song/album/
     */
    protected I mItem;

    /**
     * Song list. The playlist's, the album's, the artist's discography available.
     */
    private long[] mSongList;

    /**
     * Id of a context menu item
     */
    private long mSelectedId;

    /**
     * The Id of the playlist the song belongs to
     */
    long mPlaylistId;

    /**
     * Song, album, and artist name used in the context menu
     */
    private String mSongName, mAlbumName, mArtistName;

    /**
     * Profile header
     */
    ProfileTabCarousel mProfileTabCarousel;

    protected ViewGroup mRootView;

    protected int mDefaultFragmentEmptyString;

    private volatile long lastRestartLoader;

    protected abstract T createAdapter();

    protected abstract String getLayoutTypeName();

    public abstract void onItemClick(final AdapterView<?> parent, final View view, final int position,
                                     final long id);

    protected void onSongItemClick(int position) {
        if (mAdapter != null) {
            MusicPlaybackService.safePost(() -> songClickTask(position));
        }
    }

    protected ApolloFragment(int groupId, int loaderId, int defaultEmptyString) {
        this.GROUP_ID = groupId;
        this.LOADER_ID = loaderId;
        this.mDefaultFragmentEmptyString = defaultEmptyString;
    }

    protected ApolloFragment(int groupId, int loaderId) {
        this(groupId, loaderId, R.string.empty_music);
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        mProfileTabCarousel = activity.findViewById(R.id.activity_profile_base_tab_carousel);
        if (activity instanceof BaseActivity) {
            // Register the music status listener
            ((BaseActivity) activity).setMusicStateListenerListener(this);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The View for the fragment's UI
        if (isSimpleLayout()) {
            mRootView = (ViewGroup) inflater.inflate(R.layout.list_base, null, false);
            initListView();
        } else {
            // this inflate here is crashing.
            mRootView = (ViewGroup) inflater.inflate(R.layout.grid_base, null, false);
            initGridView();
        }
        if (mEmptyTextView == null) {
            mEmptyTextView = mRootView.findViewById(R.id.empty);
        }
        return mRootView;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create the adapter
        mAdapter = createAdapter();
    }

    public T getAdapter() {
        return mAdapter;
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int mSelectedPosition = info.position - mAdapter.getOffset();
        // Create a new song
        mItem = mAdapter.getItem(mSelectedPosition);
        if (mItem instanceof Song) {
            Song mSong = (Song) mItem;
            mSelectedId = mSong.mSongId;
            mSongName = mSong.mSongName;
            mAlbumName = mSong.mAlbumName;
            mArtistName = mSong.mArtistName;
            mSongList = null;
        } else if (mItem instanceof Album) {
            Album mAlbum = (Album) mItem;
            mSelectedId = mAlbum.mAlbumId;
            mSongName = null;
            mAlbumName = mAlbum.mAlbumName;
            mArtistName = mAlbum.mArtistName;
            MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForAlbum(getActivity(), mAlbum.mAlbumId));
        } else if (mItem instanceof Artist) {
            Artist mArtist = (Artist) mItem;
            mSelectedId = mArtist.mArtistId;
            mSongName = null;
            mArtistName = mArtist.mArtistName;
            MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForArtist(getActivity(), mArtist.mArtistId));
        } else if (mItem instanceof Genre) {
            Genre mGenre = (Genre) mItem;
            mSelectedId = mGenre.mGenreId;
            MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForGenre(getActivity(), mGenre.mGenreId));
        } else if (mItem instanceof Playlist) {
            Playlist mPlaylist = (Playlist) mItem;
            mSelectedId = mPlaylist.mPlaylistId;
            if (mSelectedId == PlaylistLoader.FAVORITE_PLAYLIST_ID) {
                MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForFavorites(getActivity()));
            } else if (mSelectedId == PlaylistLoader.LAST_ADDED_PLAYLIST_ID) {
                MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForLastAdded(getActivity()));
            } else {
                MusicPlaybackService.safePost(() -> mSongList = MusicUtils.getSongListForPlaylist(getActivity(), mPlaylist.mPlaylistId));
            }
        }
        // Play the selected songs
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE, getString(R.string.context_menu_play_selection))
                .setIcon(R.drawable.contextmenu_icon_play);
        // Play the next song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE, getString(R.string.context_menu_play_next))
                .setIcon(R.drawable.contextmenu_icon_next);
        // Add the song/album to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, getString(R.string.add_to_queue))
                .setIcon(R.drawable.contextmenu_icon_queue_add);
        // Add the song to favorite's playlist
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE, R.string.add_to_favorites)
                .setIcon(R.drawable.contextmenu_icon_favorite);
        // Add the song/album to a playlist
        final SubMenu subMenu =
                menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST, Menu.NONE, R.string.add_to_playlist)
                        .setIcon(R.drawable.contextmenu_icon_add_to_existing_playlist_dark);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, true);
        if (mItem instanceof Song) {
            menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE, getString(R.string.context_menu_use_as_ringtone))
                    .setIcon(R.drawable.contextmenu_icon_ringtone);
        }
        // More by artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE, getString(R.string.context_menu_more_by_artist))
                .setIcon(R.drawable.contextmenu_icon_artist);
        // Delete the album
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, getString(R.string.context_menu_delete))
                .setIcon(R.drawable.contextmenu_icon_trash);
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == GROUP_ID) {
            final long[] songList = mSongList != null ?
                    mSongList :
                    new long[]{mSelectedId};
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicPlaybackService.safePost(() -> MusicUtils.playFDs(songList, 0, MusicUtils.isShuffleEnabled()));
                    return true;
                case FragmentMenuItems.PLAY_NEXT:
                    MusicPlaybackService.safePost(() -> MusicUtils.playNext(songList));
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicPlaybackService.safePost(() -> MusicUtils.addToQueue(getActivity(), songList));
                    return true;
                case FragmentMenuItems.ADD_TO_FAVORITES:
                    onAddToFavorites();
                    return true;
                case FragmentMenuItems.REMOVE_FROM_FAVORITES:
                    onRemoveFromFavorites();
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(songList).show(getFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long playlistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicPlaybackService.safePost(() -> MusicUtils.addToPlaylist(getActivity(), songList, playlistId));
                    refresh();
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    if (onUseAsRingtone()) {
                        return true;
                    }
                case FragmentMenuItems.DELETE:
                    return onDelete(songList);
                case FragmentMenuItems.MORE_BY_ARTIST:
                    MusicPlaybackService.safePost(() -> {
                        long artistId = MusicUtils.getIdForArtist(getActivity(), mArtistName);
                        long[] tracks = MusicUtils.getSongListForArtist(getActivity(), artistId);
                        NavUtils.openArtistProfile(getActivity(), mArtistName, tracks);
                    });
                    return true;
                case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                    return onRemoveFromPlaylist();
                case FragmentMenuItems.REMOVE_FROM_RECENT:
                    return onRemoveFromRecent();
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    private boolean onUseAsRingtone() {
        Activity activity = getActivity();
        if (activity == null) {
            return false;
        }
        if (mSelectedId == -1) {
            return false;
        }
        WriteSettingsPermissionActivityHelper helper = new WriteSettingsPermissionActivityHelper(getActivity());
        helper.onSetRingtoneOption(getActivity(), mSelectedId, Constants.FILE_TYPE_AUDIO);
        return true;
    }

    private boolean onRemoveFromRecent() {
        RecentStore recentStore = RecentStore.getInstance(getActivity());
        if (recentStore != null) {
            recentStore.removeItem(mSelectedId);
        }
        MusicUtils.refresh();
        restartLoader(true);
        return true;
    }

    private boolean onDelete(long[] songList) {
        if (songList == null || songList.length == 0) {
            return false;
        }
        String title = getResources().getString(R.string.unknown);
        if (mItem instanceof Song) {
            title = ((Song) mItem).mSongName;
        } else if (mItem instanceof Album) {
            title = ((Album) mItem).mAlbumName;
        } else if (mItem instanceof Artist) {
            title = ((Artist) mItem).mArtistName;
        }
        DeleteDialog.newInstance(title, songList, null).setOnDeleteCallback(id -> {
            restartLoader(true);
            refresh();
        }).show(getFragmentManager(), "DeleteDialog");
        return true;
    }

    private boolean onRemoveFromPlaylist() {
        mAdapter.remove(mItem);
        mAdapter.notifyDataSetChanged();
        if (mItem instanceof Song) {
            Song song = (Song) mItem;
            MusicPlaybackService.safePost(() ->
            {
                MusicUtils.removeFromPlaylist(getActivity(), song.mSongId, mPlaylistId);
                restartLoader(true);
            });
            return true;
        }
        return false;
    }

    private void onAddToFavorites() {
        FavoritesStore favoritesStore = FavoritesStore.getInstance(getActivity());
        if (mSongList != null) {
            int added = 0;
            for (Long songId : mSongList) {
                try {
                    final Song song = MusicUtils.getSong(getActivity(), songId);
                    if (song != null && favoritesStore != null) {
                        favoritesStore.addSongId(songId, song.mSongName, song.mAlbumName, song.mArtistName);
                        added++;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (added > 0) {
                final String message = getResources().getQuantityString(
                        R.plurals.NNNtrackstoplaylist, added, added);
                AppMsg.makeText(getActivity(), message, AppMsg.STYLE_CONFIRM).show();
            }
        } else if (mSelectedId != -1 && favoritesStore != null) {
            favoritesStore.addSongId(mSelectedId, mSongName, mAlbumName, mArtistName);
        }
    }

    private void onRemoveFromFavorites() {
        mAdapter.remove(mItem);
        mAdapter.notifyDataSetChanged();
        FavoritesStore favoritesStore = FavoritesStore.getInstance(getActivity());
        if (favoritesStore != null) {
            favoritesStore.removeItem(mSelectedId);
        }
        refresh();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        initLoader();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(getArguments() != null ? getArguments() : new Bundle());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<I>> loader, final List<I> data) {
        // Check for any errors
        if (data == null || data.isEmpty()) {
            mAdapter.unload();
            mAdapter.notifyDataSetChanged();
            // Set the empty text
            if (mEmptyTextView != null) {
                mEmptyTextView.setText(mDefaultFragmentEmptyString);
                if (isSimpleLayout()) {
                    mListView.setEmptyView(mEmptyTextView);
                } else {
                    mGridView.setEmptyView(mEmptyTextView);
                }
            }
            return;
        }
        if (mAdapter == null) {
            mAdapter = createAdapter();
            if (isSimpleLayout()) {
                mListView.setAdapter(mAdapter);
            } else {
                mGridView.setAdapter(mAdapter);
            }
        } else {
            mAdapter.unload();
        }
        // Start fresh
        if (mAdapter != null) {
            mAdapter.setDataList(data);
            mAdapter.setNotifyOnChange(false);
            for (final I item : data) {
                mAdapter.add(item);
            }
            mAdapter.setNotifyOnChange(true);
            if (mAdapter instanceof ApolloFragmentAdapter.Cacheable) {
                ((ApolloFragmentAdapter.Cacheable) mAdapter).buildCache();
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoaderReset(final Loader<List<I>> loader) {
        // Clear the data in the adapter
        if (mAdapter != null) {
            mAdapter.unload();
        }
    }

    /**
     * Restarts the loader.
     * (Don't do so until 10 seconds later if you refreshed already)
     */
    public void refresh() {
        // Scroll to the stop of the list before restarting the loader.
        // Otherwise, if the user has scrolled enough to move the header, it
        // becomes misplaced and needs to be reset.
        if (mListView != null) {
            mListView.setSelection(0);
        } else if (mGridView != null) {
            mGridView.setSelection(0);
        }
        if (mAdapter != null) {
            mAdapter.clear();
        }
        restartLoader(); // this won't be executed if it was recently called. no risk of endless loop.
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.flush();
        }
    }

    public void restartLoader() {
        restartLoader(false);
    }

    public void restartLoader(boolean force) {
        if (force || (System.currentTimeMillis() - lastRestartLoader) >= 5000 && isAdded()) {
            lastRestartLoader = System.currentTimeMillis();
            try {
                getLoaderManager().restartLoader(LOADER_ID, getArguments(), this);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void initLoader() {
        final Intent intent = getActivity().getIntent();
        if (intent != null && intent.getExtras() != null && isAdded()) {
            getLoaderManager().initLoader(LOADER_ID, intent.getExtras(), this);
        } else {
            restartLoader(true);
        }
    }

    public void onMetaChanged() {
        restartLoader();
    }

    protected boolean isSimpleLayout() {
        //boolean r = PreferenceUtils.getInstance(getActivity()).isSimpleLayout(getLayoutTypeName());
        return true;
    }

    protected boolean isDetailedLayout() {
        return PreferenceUtils.getInstance().isDetailedLayout(getLayoutTypeName());
    }

    /**
     * Sets up the grid view
     */
    private void initGridView() {
        if (mRootView == null) {
            throw new RuntimeException("initGridView(): mRootView == null");
        }
        // Initialize the grid
        mGridView = mRootView.findViewById(R.id.grid_base);
        if (mGridView != null && mAdapter != null) {
            // Set the data behind the grid
            mGridView.setAdapter(mAdapter);
            // Set up the helpers
            initAbsListView(mGridView);
        }
        if (ApolloUtils.isLandscape(getActivity())) {
            if (isDetailedLayout()) {
                if (mAdapter != null) {
                    mAdapter.setLoadExtraData(true);
                }
                mGridView.setNumColumns(2);
            } else {
                mGridView.setNumColumns(4);
            }
        } else {
            if (isDetailedLayout()) {
                if (mAdapter != null) {
                    mAdapter.setLoadExtraData(true);
                }
                mGridView.setNumColumns(1);
            } else {
                mGridView.setNumColumns(2);
            }
        }
    }

    /**
     * Sets up various helpers for both the list and grid
     *
     * @param list The list or grid
     */
    private void initAbsListView(final AbsListView list) {
        // Release any references to the recycled Views
        list.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        list.setOnCreateContextMenuListener(this);
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);
        // To help make scrolling smooth
        if (mProfileTabCarousel != null) {
            list.setOnScrollListener(new VerticalScrollListener(null, mProfileTabCarousel, 0));
            // Remove the scrollbars and padding for the fast scroll
            list.setVerticalScrollBarEnabled(false);
            list.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * Sets up the list view
     */
    protected void initListView() {
        if (mRootView == null) {
            throw new RuntimeException("initListView(): mRootView == null");
        }
        // Initialize the grid
        mListView = mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        if (mAdapter != null) {
            mListView.setAdapter(mAdapter);
        }
        // Set up the helpers
        initAbsListView(mListView);
    }

    /**
     * Pause disk cache access to ensure smoother scrolling
     */
    final VerticalScrollListener.ScrollableHeader mScrollableHeader = new VerticalScrollListener.ScrollableHeader() {
        @Override
        public void onScrollStateChanged(final AbsListView view, final int scrollState) {
            // Pause disk cache access to ensure smoother scrolling
            if (mAdapter == null) {
                return;
            }
            mAdapter.setPauseDiskCache(
                    scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING ||
                            scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);

        }
    };

    /**
     * Scrolls the list to the currently playing song when the user touches the
     * header in the PagerTitleStrip
     */
    public void scrollToCurrentSong() {
        final int currentSongPosition = getItemPositionBySong();
        if (currentSongPosition != 0) {
            mListView.setSelection(currentSongPosition);
        }
    }

    /**
     * @return The position of an item in the list based on the name of the
     * currently playing song.
     */
    private int getItemPositionBySong() {
        final long trackId = MusicUtils.getCurrentAudioId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            final Song item = (Song) mAdapter.getItem(i);
            if (item != null && item.mSongId == trackId) {
                return i;
            }
        }
        return 0;
    }

    private void songClickTask(int position) {
        try {
            ApolloFragmentAdapter adapter = getAdapter(); // in multiple lines for easier debugging
            MusicUtils.playAllFromUserItemClick(adapter, position);
            Activity activity = getActivity();
            activity.runOnUiThread(adapter::notifyDataSetChanged);
            NavUtils.openAudioPlayer(activity);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
