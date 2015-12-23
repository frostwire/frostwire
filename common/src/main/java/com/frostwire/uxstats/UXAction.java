/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 
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

package com.frostwire.uxstats;

import java.lang.reflect.Field;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class UXAction {
    public static final int CONFIGURATION_WIZARD_BASE = 0;
    public static final int CONFIGURATION_WIZARD_FIRST_TIME = CONFIGURATION_WIZARD_BASE + 1; // both
    public static final int CONFIGURATION_WIZARD_AFTER_UPDATE = CONFIGURATION_WIZARD_BASE + 2; // both

    public static final int SEARCH_BASE = 1000;
    public static final int SEARCH_STARTED_ENTER_KEY = SEARCH_BASE + 1; // both
    public static final int SEARCH_STARTED_SMALL_SEARCH_ICON_CLICK = SEARCH_BASE + 2; // desktop only
    public static final int SEARCH_STARTED_SEARCH_TAB_BUTTON = SEARCH_BASE + 3; // desktop only
    public static final int SEARCH_RESULT_CLICK_DOWNLOAD = SEARCH_BASE + 4; // desktop only
    public static final int SEARCH_RESULT_ENTER_KEY_DOWNLOAD = SEARCH_BASE + 5; // desktop only
    public static final int SEARCH_RESULT_BIG_BUTTON_DOWNLOAD = SEARCH_BASE + 6; // desktop only
    public static final int SEARCH_RESULT_ROW_BUTTON_DOWNLOAD = SEARCH_BASE + 7; // desktop only
    public static final int SEARCH_RESULT_CLICKED = SEARCH_BASE + 8; // android only
    public static final int SEARCH_RESULT_AUDIO_PREVIEW = SEARCH_BASE + 9; // desktop only
    public static final int SEARCH_RESULT_VIDEO_PREVIEW = SEARCH_BASE + 10; // desktop only
    public static final int SEARCH_RESULT_DETAIL_VIEW = SEARCH_BASE + 11; // desktop only
    public static final int SEARCH_RESULT_SOURCE_VIEW = SEARCH_BASE + 12; // both
    public static final int SEARCH_RESULT_FILE_TYPE_CLICK = SEARCH_BASE + 13; // both

    public static final int DOWNLOAD_BASE = 2000;
    public static final int DOWNLOAD_FULL_TORRENT_FILE = DOWNLOAD_BASE + 1; // both
    public static final int DOWNLOAD_PARTIAL_TORRENT_FILE = DOWNLOAD_BASE + 2; // both
    public static final int DOWNLOAD_CLOUD_FILE = DOWNLOAD_BASE + 3; // both
    public static final int DOWNLOAD_CLOUD_URL_FROM_FILE_ACTION = DOWNLOAD_BASE + 4; // desktop only
    public static final int DOWNLOAD_CLOUD_URL_FROM_SEARCH_FIELD = DOWNLOAD_BASE + 5; // desktop only
    public static final int DOWNLOAD_TORRENT_URL_FROM_FILE_ACTION = DOWNLOAD_BASE + 6; // desktop only
    public static final int DOWNLOAD_TORRENT_URL_FROM_SEARCH_FIELD = DOWNLOAD_BASE + 7; // desktop only
    public static final int DOWNLOAD_MAGNET_URL_FROM_FILE_ACTION = DOWNLOAD_BASE + 8; // desktop only
    public static final int DOWNLOAD_MAGNET_URL_FROM_SEARCH_FIELD = DOWNLOAD_BASE + 9; // desktop only
    public static final int DOWNLOAD_PAUSE = DOWNLOAD_BASE + 10;
    public static final int DOWNLOAD_RESUME = DOWNLOAD_BASE + 11;
    public static final int DOWNLOAD_REMOVE = DOWNLOAD_BASE + 12;
    public static final int DOWNLOAD_CLICK_BITCOIN_PAYMENT = DOWNLOAD_BASE + 13;
    public static final int DOWNLOAD_CLICK_PAYPAL_PAYMENT = DOWNLOAD_BASE + 16;
    public static final int DOWNLOAD_CLOUD_FILE_FROM_PREVIEW = DOWNLOAD_BASE + 17; // android only

    public static final int SHARING_BASE = 3000;
    public static final int SHARING_TORRENT_CREATED_FORMALLY = SHARING_BASE + 1; // desktop only
    public static final int SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_LIBRARY = SHARING_BASE + 2; // desktop only
    public static final int SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_MENU = SHARING_BASE + 3; // desktop only
    public static final int SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_PLAYER = SHARING_BASE + 4; // desktop only
    public static final int SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_DND = SHARING_BASE + 5; // desktop only
    public static final int SHARING_SEEDING_ENABLED = SHARING_BASE + 6; // both
    public static final int SHARING_SEEDING_DISABLED = SHARING_BASE + 7; // both
    public static final int SHARING_PARTIAL_SEEDING_ENABLED = SHARING_BASE + 8; // unused
    public static final int SHARING_PARTIAL_SEEDING_DISABLED = SHARING_BASE + 9; // unused
    
    public static final int LIBRARY_BASE = 4000;
    public static final int LIBRARY_PLAY_AUDIO_FROM_FILE = LIBRARY_BASE + 1;
    public static final int LIBRARY_PLAY_AUDIO_FROM_PLAYLIST = LIBRARY_BASE + 2; // desktop only
    public static final int LIBRARY_PLAY_AUDIO_FROM_STARRED_PLAYLIST = LIBRARY_BASE + 3; // desktop only
    public static final int LIBRARY_STARRED_AUDIO_FROM_PLAYLIST = LIBRARY_BASE + 5; // desktop only (unused)
    public static final int LIBRARY_PLAYLIST_CREATED = LIBRARY_BASE + 6; // desktop only
    public static final int LIBRARY_PLAYLIST_REMOVED = LIBRARY_BASE + 7; // desktop only
    public static final int LIBRARY_PLAYLIST_RENAMED = LIBRARY_BASE + 8; // desktop only
    public static final int LIBRARY_VIDEO_PLAY = LIBRARY_BASE + 9; // both
    public static final int LIBRARY_VIDEO_TOGGLE_FULLSCREEN = LIBRARY_BASE + 10; // desktop only
    public static final int LIBRARY_BROWSE_FILE_TYPE_AUDIO = LIBRARY_BASE + 11; // both
    public static final int LIBRARY_BROWSE_FILE_TYPE_RINGTONES = LIBRARY_BASE + 12; // android
    public static final int LIBRARY_BROWSE_FILE_TYPE_VIDEOS = LIBRARY_BASE + 13; // both
    public static final int LIBRARY_BROWSE_FILE_TYPE_PICTURES = LIBRARY_BASE + 14; // both
    public static final int LIBRARY_BROWSE_FILE_TYPE_APPLICATIONS = LIBRARY_BASE + 15; // both
    public static final int LIBRARY_BROWSE_FILE_TYPE_DOCUMENTS = LIBRARY_BASE + 16; // both
    public static final int LIBRARY_BROWSE_FILE_TYPE_TORRENTS = LIBRARY_BASE + 17; // both

    public static final int PLAYER_BASE = 6000;
    public static final int PLAYER_GESTURE_SWIPE_SONG = PLAYER_BASE + 1; // android only
    public static final int PLAYER_GESTURE_PAUSE_RESUME = PLAYER_BASE + 2; // android only
    public static final int PLAYER_MENU_SHARE = PLAYER_BASE + 3; // android only
    public static final int PLAYER_MENU_UNSHARE = PLAYER_BASE + 4; // android only
    public static final int PLAYER_MENU_STOP = PLAYER_BASE + 5; // android only
    public static final int PLAYER_MENU_DELETE_TRACK = PLAYER_BASE + 6; // android only
    public static final int PLAYER_STOP_ON_LONG_CLICK = PLAYER_BASE + 7; // android only
    public static final int PLAYER_TOGGLE_FAVORITE = PLAYER_BASE + 8; // android only
    public static final int PLAYER_SOCIAL_SHARE = PLAYER_BASE + 9; // android only (unused)
    
    public static final int MISC_BASE = 7000;
    public static final int MISC_CHAT_OPENED_IN_BROWSER = MISC_BASE + 1;
    public static final int MISC_PROMO_CLICK_ON_TIPS = MISC_BASE + 2; // desktop
    public static final int MISC_INTERSTITIAL_SHOW = MISC_BASE + 3; // android only
    public static final int MISC_NOTIFICATION_EXIT = MISC_BASE + 4; // android only
    
    public static final int SETTINGS_BASE = 8000;
    public static final int SETTINGS_SET_STORAGE_INTERNAL_MEMORY = SETTINGS_BASE + 1; // android only
    public static final int SETTINGS_SET_STORAGE_SD_CARD = SETTINGS_BASE + 2; // android only


    UXAction(int code, long time) {
        this.code = code;
        this.time = time;
    }

    // DO NOT DELETE THESE. They're not accessed but they're converted to JSON when sent over.
    private final int code;
    private final long time;
    
    public static String getActionName(int code) {
        Field[] declaredFields = UXAction.class.getDeclaredFields();
        for (Field f : declaredFields) {
            try {
                if (f!=null && f.getInt(null) == code) {
                    return f.getName();
                }
            } catch (Throwable e) {
                //e.printStackTrace();
            }
        }
        return "UNKNOWN_ACTION";
    }
}
