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

package com.andrew.apollo.utils;

import com.andrew.apollo.ui.fragments.AlbumFragment;
import com.andrew.apollo.ui.fragments.ArtistFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.andrew.apollo.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.andrew.apollo.ui.fragments.profile.AlbumSongFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistAlbumFragment;
import com.andrew.apollo.ui.fragments.profile.ArtistSongFragment;
import com.frostwire.android.core.ConfigurationManager;

/**
 * A collection of helpers designed to get and set various preferences across
 * Apollo.
 *
 * These helpers are now a wrapper of more optimized FrostWire ConfigurationManager
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron)
 * @author Alden Torres (aldenml)
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

    private static PreferenceUtils sInstance;
    private final ConfigurationManager cm;

    private PreferenceUtils() {
        cm = ConfigurationManager.instance();
    }

    /**
     * @return A singleton of this class
     */
    public static PreferenceUtils getInstance() {
        if (sInstance == null) {
            sInstance = new PreferenceUtils();
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
        cm.setInt(START_PAGE, value);
    }

    /**
     * Returns the last page the user was on when the app was exited.
     *
     * @return The page to start on when the app is opened.
     */
    public final int getStartPage() {
        return cm.getInt(START_PAGE, DEFAULT_PAGE);
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
        String key = cm.getString(ARTIST_SORT_ORDER, defaultSortKey);
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
        return cm.getString(ARTIST_SONG_SORT_ORDER, SortOrder.ArtistSongSortOrder.SONG_A_Z);
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
        return cm.getString(ARTIST_ALBUM_SORT_ORDER, SortOrder.ArtistAlbumSortOrder.ALBUM_A_Z);
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
        return cm.getString(ALBUM_SORT_ORDER, SortOrder.AlbumSortOrder.ALBUM_A_Z);
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
        return cm.getString(ALBUM_SONG_SORT_ORDER, SortOrder.AlbumSongSortOrder.SONG_TRACK_LIST);
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
        return cm.getString(SONG_SORT_ORDER, SortOrder.SongSortOrder.SONG_A_Z);
    }

    /**
     * Saves the layout type for a list
     *
     * @param key   Which layout to change
     * @param value The new layout type
     */
    private void setPreference(final String key, final String value) {
        cm.setString(key, value);
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
        return cm.getString(which, defaultValue).equals(simple);
    }

    /**
     * @param which Which list to check.
     * @return True if the layout type is the simple layout, false otherwise.
     */
    public boolean isDetailedLayout(final String which) {
        final String detailed = "detailed";
        final String defaultValue = "grid";
        return cm.getString(which, defaultValue).equals(detailed);
    }
}
