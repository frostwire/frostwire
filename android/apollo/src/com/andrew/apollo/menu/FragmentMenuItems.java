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

package com.andrew.apollo.menu;

/**
 * Several of the context menu items used in Apollo are reused. This class helps
 * keep things tidy.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class FragmentMenuItems {

    /* Removes a single album from the recent pages */
    public static final int REMOVE_FROM_RECENT = 0;

    /* Used to play the selected artist, album, song, playlist, or genre */
    public static final int PLAY_SELECTION = 1;

    /* Used to add to the queue */
    public static final int ADD_TO_QUEUE = 2;

    /* Used to add to a playlist */
    public static final int ADD_TO_PLAYLIST = 3;

    /* Used to add to the favorites cache */
    public static final int ADD_TO_FAVORITES = 4;

    /* Used to create a new playlist */
    public static final int NEW_PLAYLIST = 5;

    /* Used to rename a playlist */
    public static final int RENAME_PLAYLIST = 6;

    /* Used to add to a current playlist */
    public static final int PLAYLIST_SELECTED = 7;

    /* Used to show more content by an artist */
    public static final int MORE_BY_ARTIST = 8;

    /* Used to delete track(s) */
    public static final int DELETE = 9;

    /* Used to remove a track from the favorites cache */
    public static final int REMOVE_FROM_FAVORITES = 10;

    /* Used to remove a track from a playlist */
    public static final int REMOVE_FROM_PLAYLIST = 11;

    /* Used to queue a track to be played next */
    public static final int PLAY_NEXT = 12;

}
