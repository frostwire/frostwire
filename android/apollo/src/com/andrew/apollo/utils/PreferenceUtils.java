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

package com.andrew.apollo.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import com.andrew.apollo.ui.fragments.AlbumFragment;
import com.andrew.apollo.ui.fragments.ArtistFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.andrew.apollo.ui.fragments.profile.AlbumSongFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistAlbumFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistSongFragment;
import com.frostwire.android.R;

/**
 * A collection of helpers designed to get and set various preferences across
 * Apollo.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class PreferenceUtils {

    /* Default start page (Artist page) */
    public static final int DEFAULT_PAGE = 0;

    /* Saves the last page the pager was on in {@link MusicBrowserPhoneFragment} */
    public static final String START_PAGE = "start_page";

    // Sort order for the artist list
    public static final String ARTIST_SORT_ORDER = "artist_sort_order";

    // Sort order for the artist song list
    public static final String ARTIST_SONG_SORT_ORDER = "artist_song_sort_order";

    // Sort order for the artist album list
    public static final String ARTIST_ALBUM_SORT_ORDER = "artist_album_sort_order";

    // Sort order for the album list
    public static final String ALBUM_SORT_ORDER = "album_sort_order";

    // Sort order for the album song list
    public static final String ALBUM_SONG_SORT_ORDER = "album_song_sort_order";

    // Sort order for the song list
    public static final String SONG_SORT_ORDER = "song_sort_order";

    // Sets the type of layout to use for the artist list
    public static final String ARTIST_LAYOUT = "artist_layout";

    // Sets the type of layout to use for the album list
    public static final String ALBUM_LAYOUT = "album_layout";

    // Sets the type of layout to use for the recent list
    public static final String RECENT_LAYOUT = "recent_layout";

    public static final String SIMPLE_LAYOUT = "simple";

    // Key used to set the overall theme color
    public static final String DEFAULT_THEME_COLOR = "default_theme_color";

    private static PreferenceUtils sInstance;

    private final SharedPreferences mPreferences;

    /**
     * Constructor for <code>PreferenceUtils</code>
     *
     * @param context The {@link Context} to use.
     */
    public PreferenceUtils(final Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * @param context The {@link Context} to use.
     * @return A singleton of this class
     */
    public static final PreferenceUtils getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new PreferenceUtils(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Saves the current page the user is on when they close the app.
     *
     * @param value The last page the pager was on when the onDestroy is called
     *              in {@link MusicBrowserPhoneFragment}.
     */
    public void setStartPage(final int value) {
        ApolloUtils.execute(new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... unused) {
                final SharedPreferences.Editor editor = mPreferences.edit();
                editor.putInt(START_PAGE, value);
                editor.apply();

                return null;
            }
        }, (Void[]) null);
    }

    /**
     * Returns the last page the user was on when the app was exited.
     *
     * @return The page to start on when the app is opened.
     */
    public final int getStartPage() {
        return mPreferences.getInt(START_PAGE, DEFAULT_PAGE);
    }

    /**
     * Returns the current theme color.
     *
     * @param context The {@link Context} to use.
     * @return The default theme color.
     */
    public final int getDefaultThemeColor(final Context context) {
        return mPreferences.getInt(DEFAULT_THEME_COLOR,
                context.getResources().getColor(R.color.holo_blue_light));
    }

    /**
     * Sets the sort order for the artist list.
     *
     * @param value The new sort order
     */
    public void setArtistSortOrder(final String value) {
        setPreference(ARTIST_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the artist list in {@link ArtistFragment}
     */
    public final String getArtistSortOrder() {
        // This is only to prevent return an invalid field name caused by bug BUGDUMP-21136
        final String defaultSortKey = SortOrder.ArtistSortOrder.ARTIST_A_Z;
        String key = mPreferences.getString(ARTIST_SORT_ORDER, defaultSortKey);
        if (key.equals(SortOrder.ArtistSongSortOrder.SONG_FILENAME)) {
            key = defaultSortKey;
        }
        return key;
    }

    /**
     * Sets the sort order for the artist song list.
     *
     * @param value The new sort order
     */
    public void setArtistSongSortOrder(final String value) {
        setPreference(ARTIST_SONG_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the artist song list in
     * {@link ArtistSongFragment}
     */
    public final String getArtistSongSortOrder() {
        return mPreferences.getString(ARTIST_SONG_SORT_ORDER,
                SortOrder.ArtistSongSortOrder.SONG_A_Z);
    }

    /**
     * Sets the sort order for the artist album list.
     *
     * @param value The new sort order
     */
    public void setArtistAlbumSortOrder(final String value) {
        setPreference(ARTIST_ALBUM_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the artist album list in
     * {@link ArtistAlbumFragment}
     */
    public final String getArtistAlbumSortOrder() {
        return mPreferences.getString(ARTIST_ALBUM_SORT_ORDER,
                SortOrder.ArtistAlbumSortOrder.ALBUM_A_Z);
    }

    /**
     * Sets the sort order for the album list.
     *
     * @param value The new sort order
     */
    public void setAlbumSortOrder(final String value) {
        setPreference(ALBUM_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the album list in {@link AlbumFragment}
     */
    public final String getAlbumSortOrder() {
        return mPreferences.getString(ALBUM_SORT_ORDER, SortOrder.AlbumSortOrder.ALBUM_A_Z);
    }

    /**
     * Sets the sort order for the album song list.
     *
     * @param value The new sort order
     */
    public void setAlbumSongSortOrder(final String value) {
        setPreference(ALBUM_SONG_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the album song in
     * {@link AlbumSongFragment}
     */
    public final String getAlbumSongSortOrder() {
        return mPreferences.getString(ALBUM_SONG_SORT_ORDER,
                SortOrder.AlbumSongSortOrder.SONG_TRACK_LIST);
    }

    /**
     * Sets the sort order for the song list.
     *
     * @param value The new sort order
     */
    public void setSongSortOrder(final String value) {
        setPreference(SONG_SORT_ORDER, value);
    }

    /**
     * @return The sort order used for the song list in {@link SongFragment}
     */
    public final String getSongSortOrder() {
        return mPreferences.getString(SONG_SORT_ORDER, SortOrder.SongSortOrder.SONG_A_Z);
    }

    /**
     * Saves the layout type for a list
     *
     * @param key   Which layout to change
     * @param value The new layout type
     */
    private void setPreference(final String key, final String value) {
        ApolloUtils.execute(new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... unused) {
                final SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString(key, value);
                editor.apply();

                return null;
            }
        }, (Void[]) null);
    }

    /**
     * Sets the layout type for the artist list
     *
     * @param value The new layout type
     */
    public void setArtistLayout(final String value) {
        setPreference(ARTIST_LAYOUT, value);
    }

    /**
     * Sets the layout type for the album list
     *
     * @param value The new layout type
     */
    public void setAlbumLayout(final String value) {
        setPreference(ALBUM_LAYOUT, value);
    }

    /**
     * Sets the layout type for the recent list
     *
     * @param value The new layout type
     */
    public void setRecentLayout(final String value) {
        setPreference(RECENT_LAYOUT, value);
    }

    /**
     * @param which Which list to check.
     * @return True if the layout type is the simple layout, false otherwise.
     */
    public boolean isSimpleLayout(final String which) {
        final String simple = "simple";
        final String defaultValue = "grid";
        return mPreferences.getString(which, defaultValue).equals(simple);
    }

    /**
     * @param which Which list to check.
     * @return True if the layout type is the simple layout, false otherwise.
     */
    public boolean isDetailedLayout(final String which) {
        final String detailed = "detailed";
        final String defaultValue = "grid";
        return mPreferences.getString(which, defaultValue).equals(detailed);
    }
}
