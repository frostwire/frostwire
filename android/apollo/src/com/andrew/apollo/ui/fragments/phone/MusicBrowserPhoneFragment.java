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

package com.andrew.apollo.ui.fragments.phone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.andrew.apollo.adapters.PagerAdapter;
import com.andrew.apollo.adapters.PagerAdapter.MusicFragments;
import com.andrew.apollo.ui.fragments.AlbumFragment;
import com.andrew.apollo.ui.fragments.ArtistFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.ui.fragments.TabFragmentOrder;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.utils.SortOrder;
import com.frostwire.android.R;

import java.util.Objects;

/**
 * This class is used to hold the {@link ViewPager} used for swiping between the
 * playlists, recent, artists, albums, songs, and genre {@link Fragment}
 * s for phones.
 * <p>
 * NOTE: The reason the sort orders are taken care of in this fragment rather
 * than the individual fragments is to keep from showing all of the menu
 * items on tablet interfaces. That being said, I have a tablet interface
 * worked out, but I'm going to keep it in the Play Store version of
 * Apollo for a couple of weeks or so before merging it with CM.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class MusicBrowserPhoneFragment extends Fragment {

    /**
     * Pager
     */
    private ViewPager mViewPager;

    /**
     * VP's adapter
     */
    private PagerAdapter mPagerAdapter;

    private PreferenceUtils mPreferences;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public MusicBrowserPhoneFragment() {
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get the preferences
        mPreferences = PreferenceUtils.getInstance();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_music_browser_phone, container, false);

        // Initialize the adapter
        mPagerAdapter = new PagerAdapter((AppCompatActivity) requireActivity());
        final MusicFragments[] mFragments = MusicFragments.values();
        for (final MusicFragments mFragment : mFragments) {
            mPagerAdapter.add(mFragment.getFragmentClass(), null);
        }

        // Initialize the ViewPager
        mViewPager = rootView.findViewById(R.id.fragment_home_phone_pager);
        // Attach the adapter
        mViewPager.setAdapter(mPagerAdapter);
        // Offscreen pager loading limit
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount() - 1);
        // Start on the last page the user was on
        mViewPager.setCurrentItem(mPreferences.getStartPage());
        mViewPager.setClickable(true);


        return rootView;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save the last page the use was on
        mPreferences.setStartPage(mViewPager.getCurrentItem());
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateFavoriteMenuItemIcon(menu);
    }

    private void updateFavoriteMenuItemIcon(Menu menu) {
        MenuItem favMenuItem = menu.findItem(R.id.menu_player_favorite);
        if (favMenuItem != null) {
            favMenuItem.setIcon(MusicUtils.isFavorite() ?
                    R.drawable.ic_action_favorite_selected : R.drawable.ic_action_favorite);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (isPlaylistPage()) {
            inflater.inflate(R.menu.player_new_playlist, menu);
        }

        // Favorite action
        if (isSongPage()) {
            inflater.inflate(R.menu.player_favorite, menu);
        }
        // Shuffle all
        inflater.inflate(R.menu.player_shuffle, menu);
        // Sort orders
        if (isArtistPage()) {
            inflater.inflate(R.menu.player_artist_sort_by, menu);
            inflater.inflate(R.menu.player_view_as, menu);
        } else if (isAlbumPage()) {
            inflater.inflate(R.menu.player_album_sort_by, menu);
            inflater.inflate(R.menu.player_view_as, menu);
        } else if (isSongPage()) {
            inflater.inflate(R.menu.player_song_sort_by, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_player_shuffle:
                // Shuffle all the songs
                MusicUtils.shuffleAll(getActivity());
                return true;
            case R.id.menu_player_favorite:
                // Toggle the current track as a favorite and update the menu
                // item
                MusicUtils.toggleFavorite();
                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.menu_player_sort_by_az:
                if (isArtistPage()) {
                    mPreferences.setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_A_Z);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_A_Z);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_A_Z);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_za:
                if (isArtistPage()) {
                    mPreferences.setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_Z_A);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_Z_A);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_Z_A);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_artist:
                if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_ARTIST);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_ARTIST);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_album:
                if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_ALBUM);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_year:
                if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_YEAR);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_YEAR);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_duration:
                if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_DURATION);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_number_of_songs:
                if (isArtistPage()) {
                    mPreferences
                            .setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_SONGS);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_NUMBER_OF_SONGS);
                    getAlbumFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_number_of_albums:
                if (isArtistPage()) {
                    mPreferences
                            .setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_ALBUMS);
                    getArtistFragment().refresh();
                }
                return true;
            case R.id.menu_player_sort_by_filename:
                if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_FILENAME);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_player_view_as_simple:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("simple");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("simple");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("simple");
                }
                NavUtils.goHome(getActivity());
                return true;
            case R.id.menu_player_view_as_detailed:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("detailed");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("detailed");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("detailed");
                }
                NavUtils.goHome(getActivity());
                return true;
            case R.id.menu_player_view_as_grid:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("grid");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("grid");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("grid");
                }
                NavUtils.goHome(getActivity());
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isArtistPage() {
        return mViewPager.getCurrentItem() == TabFragmentOrder.ARTISTS_POSITION;
    }

    private ArtistFragment getArtistFragment() {
        return (ArtistFragment) mPagerAdapter.getFragment(TabFragmentOrder.ARTISTS_POSITION);
    }

    private boolean isAlbumPage() {
        return mViewPager.getCurrentItem() == TabFragmentOrder.ALBUMS_POSITION;
    }

    private AlbumFragment getAlbumFragment() {
        return (AlbumFragment) mPagerAdapter.getFragment(TabFragmentOrder.ALBUMS_POSITION);
    }

    private boolean isSongPage() {
        return mViewPager.getCurrentItem() == TabFragmentOrder.SONGS_POSITION;
    }

    private SongFragment getSongFragment() {
        return (SongFragment) mPagerAdapter.getFragment(TabFragmentOrder.SONGS_POSITION);
    }

    private boolean isRecentPage() {
        return mViewPager.getCurrentItem() == TabFragmentOrder.RECENT_POSITION;
    }

    private boolean isPlaylistPage() {
        return mViewPager.getCurrentItem() == TabFragmentOrder.PLAYLISTS_POSITION;
    }
}
