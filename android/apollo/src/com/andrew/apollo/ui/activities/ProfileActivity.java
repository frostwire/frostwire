/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;

import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.PagerAdapter;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.ui.fragments.TabFragmentOrder;
import com.andrew.apollo.ui.fragments.profile.AlbumSongFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistAlbumFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistSongFragment;
import com.andrew.apollo.ui.fragments.profile.FavoriteFragment;
import com.andrew.apollo.ui.fragments.profile.GenreSongFragment;
import com.andrew.apollo.ui.fragments.profile.LastAddedFragment;
import com.andrew.apollo.ui.fragments.profile.PlaylistSongFragment;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.utils.SortOrder;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.ProfileTabCarousel.Listener;
import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.offers.Offers;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The {@link Activity} is used to display the data for specific
 * artists, albums, playlists, and genres. This class is only used on phones.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class ProfileActivity extends BaseActivity implements OnPageChangeListener, Listener {

    /**
     * The Bundle to pass into the Fragments
     */
    private Bundle mArguments;

    /**
     * View pager
     */
    private ViewPager mViewPager;

    /**
     * Pager adapter
     */
    private PagerAdapter mPagerAdapter;

    /**
     * Profile header carousel
     */
    private ProfileTabCarousel mTabCarousel;

    /**
     * MIME type of the profile
     */
    private String mType;

    /**
     * Artist name passed into the class
     */
    private String mArtistName;

    /**
     * The main profile title
     */
    private String mProfileName;

    /**
     * Image cache
     */
    private ImageFetcher mImageFetcher;

    private PreferenceUtils mPreferences;

    public ProfileActivity() {
        super(R.layout.activity_profile_base);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the preferences
        mPreferences = PreferenceUtils.getInstance();

        // Initialize the image fetcher
        mImageFetcher = ApolloUtils.getImageFetcher(this);

        // Initialize the Bundle
        mArguments = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        // Get the MIME type
        mType = mArguments.getString(Config.MIME_TYPE);

        // Get the profile title
        mProfileName = mArguments.getString(Config.NAME);
        // Get the artist name
        if (isArtist() || isAlbum()) {
            mArtistName = mArguments.getString(Config.ARTIST_NAME);
        }
        // Initialize the pager adapter
        mPagerAdapter = new PagerAdapter(this);
        // Initialize the carousel
        mTabCarousel = findViewById(R.id.activity_profile_base_tab_carousel);
        mTabCarousel.reset();
        // Set up the action bar
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        /* Set up the artist profile */
        if (isArtist()) {
            // Add the carousel images
            mTabCarousel.setArtistProfileHeader(this, mArtistName);
            // Artist profile fragments
            mPagerAdapter.add(ArtistSongFragment.class, mArguments);
            mPagerAdapter.add(ArtistAlbumFragment.class, mArguments);
            // Action bar title
            setTitle(mArtistName);

        } else if (isAlbum()) { // Set up the album profile
            // Add the carousel images
            mTabCarousel.setAlbumProfileHeader(this, mProfileName, mArtistName);
            // Album profile fragments
            mPagerAdapter.add(AlbumSongFragment.class, mArguments);
            // Action bar title = album name
            setTitle(mProfileName);
            // Action bar subtitle = year released
            setSubtitle(mArguments.getString(Config.ALBUM_YEAR));
        } else if (isFavorites()) { // Set up the favorites profile
            // Add the carousel images
            mTabCarousel.setPlaylistOrGenreProfileHeader(this, mProfileName);
            // Favorite fragment
            mPagerAdapter.add(FavoriteFragment.class, null);
            // Action bar title = Favorites
            setTitle(mProfileName);
        } else if (isLastAdded()) { // Set up the last added profile
            // Add the carousel images
            mTabCarousel.setPlaylistOrGenreProfileHeader(this, mProfileName);
            // Last added fragment
            mPagerAdapter.add(LastAddedFragment.class, null);
            // Action bar title = Last added
            setTitle(mProfileName);
        } else if (isPlaylist()) { // Set up the user playlist profile
            // Add the carousel images
            mTabCarousel.setPlaylistOrGenreProfileHeader(this, mProfileName);
            // Playlist profile fragments
            mPagerAdapter.add(PlaylistSongFragment.class, mArguments);
            // Action bar title = playlist name
            setTitle(mProfileName);
        } else if (isGenre()) { // Set up the genre profile
            // Add the carousel images
            mTabCarousel.setPlaylistOrGenreProfileHeader(this, mProfileName);
            // Genre profile fragments
            mPagerAdapter.add(GenreSongFragment.class, mArguments);
            // Action bar title = playlist name
            setTitle(mProfileName);
        }

        // Initialize the ViewPager
        mViewPager = findViewById(R.id.activity_profile_base_pager);
        // Attach the adapter
        mViewPager.setAdapter(mPagerAdapter);
        // Offscreen limit
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount() - 1);
        // Attach the page change listener
        mViewPager.setOnPageChangeListener(this);
        // Attach the carousel listener
        mTabCarousel.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mImageFetcher.flush();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        if (isEmptyPlaylist()) {
            menu.removeItem(R.id.menu_player_shuffle);
        } else {
            // Set the shuffle all title to "play all" if a playlist.
            final MenuItem shuffle = menu.findItem(R.id.menu_player_shuffle);
            if (shuffle != null) {
                String title;
                if (isFavorites() || isLastAdded() || isPlaylist()) {
                    title = getString(R.string.menu_play_all);
                } else {
                    title = getString(R.string.menu_shuffle);
                }
                shuffle.setTitle(title);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This is the options menu that gets created when inside an artist/album
     * on the action bar "..." button.
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Pin to Home screen
        getMenuInflater().inflate(R.menu.player_add_to_homescreen, menu);
        // Shuffle
        getMenuInflater().inflate(R.menu.player_shuffle, menu);
        // Sort orders
        if (isArtistSongPage()) {
            getMenuInflater().inflate(R.menu.player_artist_song_sort_by, menu);
        } else if (isArtistAlbumPage()) {
            getMenuInflater().inflate(R.menu.player_artist_album_sort_by, menu);
        } else if (isAlbum()) {
            getMenuInflater().inflate(R.menu.player_album_song_sort_by, menu);
        }
        // Add to playlist
        if (isArtist() || isAlbum()) {
            final SubMenu subMenu = menu.addSubMenu(
                    TabFragmentOrder.ALBUMS_POSITION,
                    FragmentMenuItems.ADD_TO_PLAYLIST,
                    Menu.NONE, R.string.add_to_playlist);
            subMenu.setIcon(R.drawable.contextmenu_icon_playlist_add_dark);
            MusicUtils.makePlaylistMenu(this, TabFragmentOrder.ALBUMS_POSITION, subMenu, false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            // Create Empty New Playlist
            // Add to playlist
            case FragmentMenuItems.NEW_PLAYLIST:
            case R.id.menu_player_new_playlist:
                onOptionsItemNewPlaylistSelected();
                return true;

            case FragmentMenuItems.PLAYLIST_SELECTED: {
                // Add to existing playlist or to new playlist
                if (isAlbum() || isArtist()) {
                    long playlistId = -1;
                    // playlist id has been bundled with an intent extra along with the menu item.
                    if (item.getIntent() != null && item.getIntent().hasExtra("playlist")) {
                        playlistId = item.getIntent().getLongExtra("playlist", -1);
                    }

                    if (playlistId != -1) {
                        long[] tracks = mArguments.getLongArray(Config.TRACKS);
                        if (tracks != null && tracks.length > 0) {
                            MusicUtils.addToPlaylist(this, tracks, playlistId);
                        }
                    }
                }
                return true;
            }
            case android.R.id.home:
                goBack();
                return true;
            case R.id.menu_player_add_to_homescreen: {
                // Place the artist, album, genre, or playlist onto the Home
                // screen. Definitely one of my favorite features.
                final String name = isArtist() ? mArtistName : mProfileName;
                final Long id = mArguments.getLong(Config.ID);
                ApolloUtils.createShortcutIntentAsync(name, mArtistName, id, mType, Ref.weak(this));
                return true;
            }
            case R.id.menu_player_shuffle: {
                final long id = mArguments.getLong(Config.ID);
                long[] list = null;
                if (isArtist()) {
                    list = MusicUtils.getSongListForArtist(this, id);
                } else if (isAlbum()) {
                    list = MusicUtils.getSongListForAlbum(this, id);
                } else if (isGenre()) {
                    list = MusicUtils.getSongListForGenre(this, id);
                }
                if (isPlaylist()) {
                    MusicUtils.playPlaylist(this, id);
                } else if (isFavorites()) {
                    MusicUtils.playFavorites(this);
                } else if (isLastAdded()) {
                    MusicUtils.playLastAdded(this);
                } else {
                    if (list != null && list.length > 0) {
                        MusicUtils.playFDs(list, 0, true);
                    }
                }
                return true;
            }
            case R.id.menu_player_sort_by_az:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_A_Z);
                    getArtistSongFragment().refresh();
                } else if (isArtistAlbumPage()) {
                    mPreferences.setArtistAlbumSortOrder(SortOrder.ArtistAlbumSortOrder.ALBUM_A_Z);
                    getArtistAlbumFragment().refresh();
                } else {
                    mPreferences.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_A_Z);
                    getAlbumSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_za:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_Z_A);
                    getArtistSongFragment().refresh();
                } else if (isArtistAlbumPage()) {
                    mPreferences.setArtistAlbumSortOrder(SortOrder.ArtistAlbumSortOrder.ALBUM_Z_A);
                    getArtistAlbumFragment().refresh();
                } else {
                    mPreferences.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_Z_A);
                    getAlbumSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_album:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_ALBUM);
                    getArtistSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_year:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_YEAR);
                    getArtistSongFragment().refresh();
                } else if (isArtistAlbumPage()) {
                    mPreferences.setArtistAlbumSortOrder(SortOrder.ArtistAlbumSortOrder.ALBUM_YEAR);
                    getArtistAlbumFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_duration:
                if (isArtistSongPage()) {
                    mPreferences
                            .setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_DURATION);
                    getArtistSongFragment().refresh();
                } else {
                    mPreferences.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_DURATION);
                    getAlbumSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_date_added:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_DATE);
                    getArtistSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_track_list:
                mPreferences.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_TRACK_LIST);
                getAlbumSongFragment().refresh();
                return true;
            case R.id.menu_player_sort_by_filename:
                if (isArtistSongPage()) {
                    mPreferences.setArtistSongSortOrder(
                            SortOrder.ArtistSongSortOrder.SONG_FILENAME);
                    getArtistSongFragment().refresh();
                } else {
                    mPreferences.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_FILENAME);
                    getAlbumSongFragment().refresh();
                }
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(mArguments);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goBack();
    }

    @Override
    public void onPageScrolled(final int position, final float positionOffset,
                               final int positionOffsetPixels) {
        if (mViewPager.isFakeDragging()) {
            return;
        }

        final int scrollToX = (int) ((position + positionOffset) * mTabCarousel
                .getAllowedHorizontalScrollLength());
        mTabCarousel.scrollTo(scrollToX, 0);
    }

    @Override
    public void onPageSelected(final int position) {
        mTabCarousel.setCurrentTab(position);
    }

    @Override
    public void onPageScrollStateChanged(final int state) {
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            mTabCarousel.restoreYCoordinate(75, mViewPager.getCurrentItem());
        }
    }

    @Override
    public void onTouchDown() {
        mViewPager.beginFakeDrag();
    }

    @Override
    public void onTouchUp() {
        if (mViewPager.isFakeDragging()) {
            try {
                mViewPager.endFakeDrag();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onScrollChanged(final int l, final int t, final int oldl, final int oldt) {
        if (mViewPager.isFakeDragging()) {
            try {
                mViewPager.fakeDragBy(oldl - l);
            } catch (Throwable e) {
                // ignore possible NPE on .fakeDragBy near line 2452
            }
        }
    }

    @Override
    public void onTabSelected(final int position) {
        mViewPager.setCurrentItem(position);
    }


    private static class SongListHolder {
        long[] tracks;
    }

    /**
     * Finishes the activity and overrides the default animation.
     */
    private void goBack() {
        // If an album profile, go up to the artist profile
        if (isAlbum()) {
            final Long artistId = mArguments.getLong(Config.ID);
            WeakReference<ProfileActivity> profileActivityRef = Ref.weak(this);
            Future<SongListHolder> songListHolder = Engine.instance().getThreadPool().submit(() -> {
                if (Ref.alive(profileActivityRef)) {
                    long[] tracks = MusicUtils.getSongListForArtist(profileActivityRef.get(), artistId);
                    SongListHolder slh = new SongListHolder();
                    slh.tracks = tracks;
                    Ref.free(profileActivityRef);
                    return slh;
                }
                Ref.free(profileActivityRef);
                return null;
            });

            SongListHolder tracksHolder = null;
            try {
                // can't wait for long closing a screen
                tracksHolder = songListHolder.get(500, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            if (tracksHolder != null) {
                NavUtils.openArtistProfile(this, mArtistName, tracksHolder.tracks);
            }
            finish();
        }
        else if (!MusicUtils.isPlaying()) {
            Offers.showInterstitialOfferIfNecessary(
                    this,
                    Offers.PLACEMENT_INTERSTITIAL_MAIN,
                    false,
                    true
            );
        }
    }

    /**
     * @return True if the MIME type is vnd.android.cursor.dir/artists, false
     * otherwise.
     */
    private boolean isArtist() {
        return mType.equals(MediaStore.Audio.Artists.CONTENT_TYPE);
    }

    /**
     * @return True if the MIME type is vnd.android.cursor.dir/albums, false
     * otherwise.
     */
    private boolean isAlbum() {
        return mType.equals(MediaStore.Audio.Albums.CONTENT_TYPE);
    }

    /**
     * @return True if the MIME type is vnd.android.cursor.dir/gere, false
     * otherwise.
     */
    private boolean isGenre() {
        return mType.equals(MediaStore.Audio.Genres.CONTENT_TYPE);
    }

    /**
     * @return True if the MIME type is vnd.android.cursor.dir/playlist, false
     * otherwise.
     */
    private boolean isPlaylist() {
        return mType.equals(MediaStore.Audio.Playlists.CONTENT_TYPE);
    }

    /**
     * @return True if the MIME type is one of the playlist types and the playlist is empty, false
     * otherwise.
     */
    private boolean isEmptyPlaylist() {
        long[] list = null;
        if (isPlaylist()) {
            final long id = mArguments.getLong(Config.ID);
            list = MusicUtils.getSongListForPlaylist(this, id);
        } else if (isLastAdded()) {
            list = MusicUtils.getSongListForLastAdded(this);
        } else if (isFavorites()) {
            list = MusicUtils.getSongListForFavorites(this);
        }
        return list != null && list.length == 0;
    }

    /**
     * @return True if the MIME type is "Favorites", false otherwise.
     */
    private boolean isFavorites() {
        return mType.equals(getString(R.string.playlist_favorites));
    }

    /**
     * @return True if the MIME type is "LastAdded", false otherwise.
     */
    private boolean isLastAdded() {
        return mType.equals(getString(R.string.playlist_last_added));
    }

    private boolean isArtistSongPage() {
        return isArtist() && mViewPager.getCurrentItem() == 0;
    }

    private boolean isArtistAlbumPage() {
        return isArtist() && mViewPager.getCurrentItem() == 1;
    }

    private ArtistSongFragment getArtistSongFragment() {
        return (ArtistSongFragment) mPagerAdapter.getFragment(0);
    }

    private ArtistAlbumFragment getArtistAlbumFragment() {
        return (ArtistAlbumFragment) mPagerAdapter.getFragment(1);
    }

    private AlbumSongFragment getAlbumSongFragment() {
        return (AlbumSongFragment) mPagerAdapter.getFragment(0);
    }

    private void setTitle(String s) {
        if (!TextUtils.isEmpty(s)) {
            TextView title = findView(R.id.view_toolbar_header_title);
            if (title != null) {
                title.setText(s);
            }
        }
    }

    private void setSubtitle(String s) {
        if (!TextUtils.isEmpty(s)) {
            TextView subtitle = findView(R.id.view_toolbar_header_subtitle);
            if (subtitle != null) {
                subtitle.setVisibility(View.VISIBLE);
                subtitle.setText(s);
            }
        }
    }
}
