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

package com.andrew.apollo.menu;

/**
 * Several of the context menu items used in Apollo are reused. This class helps
 * keep things tidy.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class FragmentMenuItems {

    /* Removes a single album from the recents pages */
    public static final int REMOVE_FROM_RECENT = 0;

    /* Used to play the selected artist, album, song, playlist, or genre */
    public static final int PLAY_SELECTION = 1;

    /* Used to add to the qeueue */
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

    /* Used to fetch an artist image */
    public static final int FETCH_ARTIST_IMAGE = 10;

    /* Used to fetch album art */
    public static final int FETCH_ALBUM_ART = 11;

    /* Used to set a track as a ringtone */
    public static final int USE_AS_RINGTONE = 12;

    /* Used to remove a track from the favorites cache */
    public static final int REMOVE_FROM_FAVORITES = 13;

    /* Used to remove a track from a playlist */
    public static final int REMOVE_FROM_PLAYLIST = 14;

    /* Used to remove a track from the queue */
    public static final int REMOVE_FROM_QUEUE = 15;

    /* Used to queue a track to be played next */
    public static final int PLAY_NEXT = 16;

}
