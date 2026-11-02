/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Jose Molina (@votaguz)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.adapters.ApolloFragmentAdapter;
import com.andrew.apollo.loaders.PlaylistLoader;
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
import com.frostwire.android.gui.adapters.menu.CreateNewPlaylistMenuAction;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import java.util.List;

/**
 * Created on 1/26/16 in a plane.
 *
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 * @author Jose Molina (@votaguz)
 */
public abstract class ApolloFragment<T extends ApolloFragmentAdapter<I>, I> extends Fragment
    implements LoaderManager.LoaderCallbacks<List<I>>,
        AdapterView.OnItemClickListener,
        MusicStateListener {

  private static final Logger LOG = Logger.getLogger(ApolloFragment.class);

  private final int GROUP_ID;

  /** LoaderCallbacks identifier */
  private final int LOADER_ID;

  /** The list view */
  protected ListView mListView;

  protected TextView mEmptyTextView;

  /** The grid view */
  protected GridView mGridView;

  /** The adapter for the list */
  protected T mAdapter;

  /** Represents a song/album/ */
  protected I mItem;

  /** Id of a context menu item */
  private long mSelectedId;

  /** The Id of the playlist the song belongs to */
  long mPlaylistId;

  /** Song, album, and artist name used in the context menu */
  private String mSongName, mAlbumName, mArtistName;

  /** Profile header */
  ProfileTabCarousel mProfileTabCarousel;

  protected ViewGroup mRootView;

  protected int mDefaultFragmentEmptyString;

  private volatile long lastRestartLoader;

  protected abstract T createAdapter();

  protected abstract String getLayoutTypeName();

  public abstract void onItemClick(
      final AdapterView<?> parent, final View view, final int position, final long id);

  protected void onSongItemClick(int position) {
    if (mAdapter != null) {
      songClickTask(position);
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
  public void onAttach(@NonNull final Context context) {
    super.onAttach(context);
    final Activity activity = (Activity) context;
    mProfileTabCarousel = activity.findViewById(R.id.activity_profile_base_tab_carousel);
    if (activity instanceof BaseActivity) {
      // Register the music status listener
      ((BaseActivity) activity).setMusicStateListenerListener(this);
    }
  }

  @Override
  public void onDetach() {
    // Registered in onAttach; left in place, the screen kept reloading this detached fragment.
    if (getActivity() instanceof BaseActivity) {
      ((BaseActivity) getActivity()).removeMusicStateListener(this);
    }
    super.onDetach();
  }

  @Override
  public View onCreateView(
      final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
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
  public void onCreateContextMenu(
      final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo) {
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
    } else if (mItem instanceof Album) {
      Album mAlbum = (Album) mItem;
      mSelectedId = mAlbum.mAlbumId;
      mSongName = null;
      mAlbumName = mAlbum.mAlbumName;
      mArtistName = mAlbum.mArtistName;
    } else if (mItem instanceof Artist) {
      Artist mArtist = (Artist) mItem;
      mSelectedId = mArtist.mArtistId;
      mSongName = null;
      mArtistName = mArtist.mArtistName;
    } else if (mItem instanceof Genre) {
      Genre mGenre = (Genre) mItem;
      mSelectedId = mGenre.mGenreId;
    } else if (mItem instanceof Playlist) {
      Playlist mPlaylist = (Playlist) mItem;
      mSelectedId = mPlaylist.mPlaylistId;
    }
    // Play the selected songs
    menu.add(
            GROUP_ID,
            FragmentMenuItems.PLAY_SELECTION,
            Menu.NONE,
            getString(R.string.context_menu_play_selection))
        .setIcon(R.drawable.contextmenu_icon_play);
    // Play the next song
    menu.add(
            GROUP_ID,
            FragmentMenuItems.PLAY_NEXT,
            Menu.NONE,
            getString(R.string.context_menu_play_next))
        .setIcon(R.drawable.contextmenu_icon_next);
    // Add the song/album to the queue
    menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, getString(R.string.add_to_queue))
        .setIcon(R.drawable.contextmenu_icon_queue_add);
    // Add the song to favorite's playlist
    menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE, R.string.add_to_favorites)
        .setIcon(R.drawable.contextmenu_icon_favorite);
    // Add the song/album to a playlist
    final SubMenu subMenu =
        menu.addSubMenu(
                GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST, Menu.NONE, R.string.add_to_playlist)
            .setIcon(R.drawable.contextmenu_icon_add_to_existing_playlist_dark);
    Activity activity = getLiveActivity();
    if (activity != null) {
      MusicUtils.makePlaylistMenu(activity, GROUP_ID, subMenu, true);
    }

    // More by artist
    menu.add(
            GROUP_ID,
            FragmentMenuItems.MORE_BY_ARTIST,
            Menu.NONE,
            getString(R.string.context_menu_more_by_artist))
        .setIcon(R.drawable.contextmenu_icon_artist);
    // Delete the album
    menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, getString(R.string.context_menu_delete))
        .setIcon(R.drawable.contextmenu_icon_trash);
  }

  @Override
  public boolean onContextItemSelected(final android.view.MenuItem item) {
    if (item.getGroupId() == GROUP_ID) {
      Activity activity = getLiveActivity();
      if (activity == null) {
        return true;
      }
      switch (item.getItemId()) {
        case FragmentMenuItems.PLAY_SELECTION:
        case FragmentMenuItems.PLAY_NEXT:
        case FragmentMenuItems.ADD_TO_QUEUE:
        case FragmentMenuItems.ADD_TO_FAVORITES:
        case FragmentMenuItems.NEW_PLAYLIST:
        case FragmentMenuItems.PLAYLIST_SELECTED:
        case FragmentMenuItems.DELETE:
          withSongListOfSelection(item, songList -> onSongListAction(item, songList));
          return true;
        case FragmentMenuItems.REMOVE_FROM_FAVORITES:
          onRemoveFromFavorites();
          return true;
        case FragmentMenuItems.MORE_BY_ARTIST:
          openMoreByArtist();
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

  /**
   * Loads the songs of the item the menu was opened for, off the main thread, then runs {@code
   * action} with them on the main thread. The list was prefetched into a field when the menu
   * opened, and an action picked before that finished used the previous selection's songs, or an
   * album or artist id as a song id: "Delete" could delete songs nobody chose (GitHub #1442). An
   * item with no songs does nothing.
   */
  private void withSongListOfSelection(
      android.view.MenuItem menuItem, java.util.function.Consumer<long[]> action) {
    final Context context = getStableAppContext();
    final Object selected = mItem;
    final long selectedId = mSelectedId;
    if (context == null || selected == null) {
      return;
    }
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.MISC,
        () -> {
          long[] songList = songListFor(selected, selectedId, new MediaStoreSongLists(context));
          if (songList.length == 0) {
            return;
          }
          SystemUtils.postToUIThread(
              () -> {
                if (isAdded() && getLiveActivity() != null) {
                  action.accept(songList);
                }
              });
        });
  }

  /** Where {@link #songListFor} gets the songs of an album, artist, genre or playlist. */
  interface SongLists {
    long[] album(long id);

    long[] artist(long id);

    long[] genre(long id);

    long[] playlist(long id);

    long[] favorites();

    long[] lastAdded();
  }

  /**
   * The songs of a selected item: a song is itself; an album, artist, genre or playlist is the
   * songs it holds, possibly none. Never treats a non-song id as a song id.
   */
  static long[] songListFor(Object selected, long selectedId, SongLists lists) {
    long[] songs = null;
    if (selected instanceof Song) {
      songs = new long[] {selectedId};
    } else if (selected instanceof Album) {
      songs = lists.album(selectedId);
    } else if (selected instanceof Artist) {
      songs = lists.artist(selectedId);
    } else if (selected instanceof Genre) {
      songs = lists.genre(selectedId);
    } else if (selected instanceof Playlist) {
      if (selectedId == PlaylistLoader.FAVORITE_PLAYLIST_ID) {
        songs = lists.favorites();
      } else if (selectedId == PlaylistLoader.LAST_ADDED_PLAYLIST_ID) {
        songs = lists.lastAdded();
      } else {
        songs = lists.playlist(selectedId);
      }
    }
    return songs != null ? songs : new long[0];
  }

  private static final class MediaStoreSongLists implements SongLists {
    private final Context context;

    MediaStoreSongLists(Context context) {
      this.context = context;
    }

    @Override
    public long[] album(long id) {
      return MusicUtils.getSongListForAlbum(context, id);
    }

    @Override
    public long[] artist(long id) {
      return MusicUtils.getSongListForArtist(context, id);
    }

    @Override
    public long[] genre(long id) {
      return MusicUtils.getSongListForGenre(context, id);
    }

    @Override
    public long[] playlist(long id) {
      return MusicUtils.getSongListForPlaylist(context, id);
    }

    @Override
    public long[] favorites() {
      return MusicUtils.getSongListForFavorites(context);
    }

    @Override
    public long[] lastAdded() {
      return MusicUtils.getSongListForLastAdded(context);
    }
  }

  /** Runs a song-list menu action, on the main thread, with the selection's songs. */
  private void onSongListAction(android.view.MenuItem item, final long[] songList) {
    Activity activity = getLiveActivity();
    if (activity == null) {
      return;
    }
    switch (item.getItemId()) {
      case FragmentMenuItems.PLAY_SELECTION:
        MusicUtils.executeWithMusicPlaybackService(
            activity, () -> MusicUtils.playFDs(songList, 0, MusicUtils.isShuffleEnabled()));
        break;
      case FragmentMenuItems.PLAY_NEXT:
        MusicUtils.executeWithMusicPlaybackService(activity, () -> MusicUtils.playNext(songList));
        break;
      case FragmentMenuItems.ADD_TO_QUEUE:
        MusicUtils.executeWithMusicPlaybackService(
            activity, () -> MusicUtils.addToQueue(activity, songList));
        break;
      case FragmentMenuItems.ADD_TO_FAVORITES:
        onAddToFavorites(songList);
        break;
      case FragmentMenuItems.NEW_PLAYLIST:
        if (!getParentFragmentManager().isStateSaved()) {
          CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.newInstance(songList)
              .show(getParentFragmentManager());
        }
        break;
      case FragmentMenuItems.PLAYLIST_SELECTED:
        final long playlistId = item.getIntent().getLongExtra("playlist", 0);
        Context playlistContext = getStableAppContext();
        if (playlistContext != null) {
          SystemUtils.postToHandler(
              SystemUtils.HandlerThreadName.MISC,
              () -> MusicUtils.addToPlaylist(playlistContext, songList, playlistId));
          refresh();
        }
        break;
      case FragmentMenuItems.DELETE:
        onDelete(songList);
        break;
      default:
        break;
    }
  }

  private boolean onRemoveFromRecent() {
    final Context context = getStableAppContext();
    if (context == null) {
      return false;
    }
    final long songId = mSelectedId;
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.MISC,
        () -> {
          RecentStore recentStore = RecentStore.getInstance(context);
          if (recentStore != null) {
            recentStore.removeItem(songId);
          }
          MusicUtils.refresh();
          SystemUtils.postToUIThread(
              () -> {
                if (isAdded()) {
                  restartLoader(true);
                }
              });
        });
    return true;
  }

  private boolean onDelete(long[] songList) {
    if (songList == null || songList.length == 0) {
      return false;
    }
    if (!isAdded() || getParentFragmentManager().isStateSaved()) {
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

    DeleteDialog.newInstance(title, songList, null)
        .setOnDeleteConfirmedCallback(
            id -> {
              if (!isAdded() || mAdapter == null) {
                return;
              }
              mAdapter.removeItemsById(id);
            })
        .setOnDeleteCallback(
            id -> {
              if (!isAdded()) {
                return;
              }
              restartLoader(true);
              refresh();
            })
        .show(getParentFragmentManager(), "DeleteDialog");
    return true;
  }

  private boolean onRemoveFromPlaylist() {
    mAdapter.remove(mItem);
    mAdapter.notifyDataSetChanged();
    if (mItem instanceof Song) {
      Song song = (Song) mItem;
      Context context = getStableAppContext();
      if (context == null) {
        return false;
      }
      SystemUtils.postToHandler(
          SystemUtils.HandlerThreadName.MISC,
          () -> {
            if (song.mPlaylistMemberId >= 0) {
              MusicUtils.removePlaylistMember(context, mPlaylistId, song.mPlaylistMemberId);
            } else {
              MusicUtils.removeFromPlaylist(context, song.mSongId, mPlaylistId);
            }
            SystemUtils.postToUIThread(
                () -> {
                  if (isAdded()) {
                    restartLoader(true);
                  }
                });
          });
      return true;
    }
    return false;
  }

  /** Adds {@code songList}, the selection's songs, to Favorites, off the main thread. */
  private void onAddToFavorites(final long[] songList) {
    if (SystemUtils.isUIThread()) {
      SystemUtils.postToHandler(
          SystemUtils.HandlerThreadName.MISC, () -> onAddToFavorites(songList));
      return;
    }
    final Activity activity = getActivity();
    if (activity == null || activity.isFinishing()) {
      return;
    }
    final FavoritesStore favoritesStore = FavoritesStore.getInstance(activity);
    if (favoritesStore == null) {
      return;
    }
    int added = 0;
    for (Song song : MusicUtils.getSongs(activity, songList)) {
      try {
        favoritesStore.addSongId(song.mSongId, song.mSongName, song.mAlbumName, song.mArtistName);
        added++;
      } catch (Throwable t) {
        LOG.error("onAddToFavorites: could not add song " + song.mSongId, t);
      }
    }
    if (added == 0) {
      return;
    }
    final String message =
        activity.getResources().getQuantityString(R.plurals.NNNtrackstoplaylist, added, added);
    SystemUtils.postToUIThread(
        () -> {
          if (!isAdded()) {
            return;
          }
          final Activity uiActivity = getActivity();
          if (uiActivity == null || uiActivity.isFinishing()) {
            return;
          }
          AppMsg.makeText(uiActivity, message, AppMsg.STYLE_CONFIRM).show();
        });
  }

  private void onRemoveFromFavorites() {
    mAdapter.remove(mItem);
    mAdapter.notifyDataSetChanged();
    final Context context = getStableAppContext();
    if (context == null) {
      return;
    }
    final long songId = mSelectedId;
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.MISC,
        () -> {
          FavoritesStore favoritesStore = FavoritesStore.getInstance(context);
          if (favoritesStore != null) {
            favoritesStore.removeItem(songId);
          }
          SystemUtils.postToUIThread(
              () -> {
                if (isAdded()) {
                  refresh();
                }
              });
        });
  }

  private void openMoreByArtist() {
    if (TextUtils.isEmpty(mArtistName)) {
      return;
    }
    final Context context = getStableAppContext();
    if (context == null) {
      return;
    }
    final String artistName = mArtistName;
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.MISC,
        () -> {
          long artistId = MusicUtils.getIdForArtist(context, artistName);
          long[] tracks = MusicUtils.getSongListForArtist(context, artistId);
          SystemUtils.postToUIThread(
              () -> {
                Activity activity = getLiveActivity();
                if (activity == null || !isAdded()) {
                  return;
                }
                NavUtils.openArtistProfile(activity, artistName, tracks);
              });
        });
  }

  private Activity getLiveActivity() {
    Activity activity = getActivity();
    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
      return null;
    }
    return activity;
  }

  protected Context getStableAppContext() {
    Activity activity = getLiveActivity();
    if (activity == null) {
      return null;
    }
    Context appContext = activity.getApplicationContext();
    return appContext != null ? appContext : activity;
  }

  /** {@inheritDoc} */
  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initLoader();
  }

  @Override
  public void onSaveInstanceState(final Bundle outState) {
    lastRestartLoader = 0;
    super.onSaveInstanceState(outState);
    outState.putAll(getArguments() != null ? getArguments() : new Bundle());
  }

  @Override
  public void onLoadFinished(
      @NonNull androidx.loader.content.Loader<List<I>> loader, List<I> data) {
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
      LOG.info("ApolloFragment::onLoaderReset: unloading adapter");
      mAdapter.unload();
    }
  }

  /** Restarts the loader. (Don't do so until 10 seconds later if you refreshed already) */
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
    // Forced: the list was just emptied, and the throttled restart skipped a second refresh within
    // 5 s (two sort changes, two removals), leaving it empty (GitHub #1461).
    restartLoader(true);
    if (mAdapter != null) {
      mAdapter.notifyDataSetChanged();
    }
  }

  public void restartLoader() {
    restartLoader(false);
  }

  public void restartLoader(boolean force) {
    if (force || (System.currentTimeMillis() - lastRestartLoader) >= 5000 && isAdded()) {
      lastRestartLoader = System.currentTimeMillis();
      try {
        Activity activity = getLiveActivity();
        if (activity == null || !isAdded()) {
          return;
        }
        activity.runOnUiThread(
            () -> {
              try {
                if (!isAdded()) {
                  return;
                }
                LoaderManager.getInstance(this).restartLoader(LOADER_ID, getArguments(), this);
                LOG.info("ApolloFragment::restartLoader: restarted (" + LOADER_ID + ")");
              } catch (Throwable t) {
                LOG.error("ApolloFragment::restartLoader: ", t);
              }
            });
      } catch (Throwable t) {
        LOG.error("ApolloFragment::restartLoader: ", t);
      }
    } else {
      LOG.info(
          "ApolloFragment::restartLoader: not restarting loader (" + LOADER_ID + "), too soon.");
    }
  }

  private void initLoader() {
    final Intent intent = getActivity().getIntent();
    if (intent != null && intent.getExtras() != null && isAdded()) {
      if (SystemUtils.isUIThread()) {
        LoaderManager.getInstance(this).initLoader(LOADER_ID, intent.getExtras(), this);
      } else {
        SystemUtils.postToUIThread(
            () -> {
              try {
                LoaderManager.getInstance(this).initLoader(LOADER_ID, intent.getExtras(), this);
              } catch (Throwable t) {
                t.printStackTrace();
              }
            });
      }
    } else {
      restartLoader(true);
    }
  }

  public void onMetaChanged() {
    restartLoader();
  }

  protected boolean isSimpleLayout() {
    // boolean r = PreferenceUtils.getInstance(getActivity()).isSimpleLayout(getLayoutTypeName());
    return true;
  }

  protected boolean isDetailedLayout() {
    return PreferenceUtils.getInstance().isDetailedLayout(getLayoutTypeName());
  }

  /** Sets up the grid view */
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

  /** Sets up the list view */
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
   * Scroll events for the header; images load with FWImageLoader, so there is no cache to pause.
   */
  final VerticalScrollListener.ScrollableHeader mScrollableHeader = (view, scrollState) -> {};

  /**
   * Scrolls the list to the currently playing song when the user touches the header in the
   * PagerTitleStrip
   */
  public void scrollToCurrentSong() {
    final int currentSongPosition = getItemPositionBySong();
    if (currentSongPosition != 0) {
      mListView.setSelection(currentSongPosition);
    }
  }

  /**
   * @return The position of an item in the list based on the name of the currently playing song.
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

  @SuppressWarnings("unchecked")
  private void songClickTask(int position) {
    try {
      // Only song lists play on click; their adapter holds Songs.
      final ApolloFragmentAdapter<Song> adapter = (ApolloFragmentAdapter<Song>) getAdapter();
      if (adapter == null) {
        return;
      }
      MusicUtils.playAllFromUserItemClick(
          adapter,
          position,
          () -> {
            if (!isAdded()) {
              return;
            }
            Activity activity = getActivity();
            if (activity == null) {
              return;
            }
            adapter.notifyDataSetChanged();
            NavUtils.openAudioPlayer(activity);
          });
    } catch (Throwable t) {
      LOG.error("songClickTask() error", t);
    }
  }
}
