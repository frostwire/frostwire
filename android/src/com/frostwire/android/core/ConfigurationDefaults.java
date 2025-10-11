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

package com.frostwire.android.core;

import android.os.Environment;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.util.Hex;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author gubatron
 * @author aldenml
 */
final class ConfigurationDefaults {

    private final Map<String, Object> defaultValues;
    private final Map<String, Object> resetValues;

    ConfigurationDefaults() {
        defaultValues = new HashMap<>();
        resetValues = new HashMap<>();
        load();
    }

    Map<String, Object> getDefaultValues() {
        return Collections.unmodifiableMap(defaultValues);
    }

    Map<String, Object> getResetValues() {
        return Collections.unmodifiableMap(resetValues);
    }

    private void load() {
        defaultValues.put(Constants.PREF_KEY_CORE_UUID, uuidToString(UUID.randomUUID()));
        defaultValues.put(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD, "");//won't know until I see it.
        defaultValues.put(Constants.PREF_KEY_MAIN_APPLICATION_ON_CREATE_TIMESTAMP, System.currentTimeMillis());

        defaultValues.put(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD, true);
        defaultValues.put(Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER, (int) Constants.FILE_TYPE_TORRENTS);
        defaultValues.put(Constants.PREF_KEY_GUI_TOS_ACCEPTED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER, 10);
        defaultValues.put(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE, false);
        defaultValues.put(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION, true);
        defaultValues.put(Constants.PREF_KEY_GUI_SEARCH_KEYWORDFILTERDRAWER_TIP_TOUCHTAGS_DISMISSED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START, true);
        defaultValues.put(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG, true);
        defaultValues.put(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 50);
        defaultValues.put(Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP, -1L);
        defaultValues.put(Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH, false);
        defaultValues.put(Constants.PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN, 20);
        defaultValues.put(Constants.PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN, 10);
        defaultValues.put(Constants.PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN, 2000);
        defaultValues.put(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN, 20); // this number must be bigger than PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT to become relevant 
        defaultValues.put(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT, 20);
        defaultValues.put(Constants.PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX, 100); // no ultra big torrents here
        defaultValues.put(Constants.PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT, 256);

        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_ZOOQLE, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_FROSTCLICK, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTDOWNLOADS, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_LIMETORRENTS, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_NYAA, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TPB, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTZ2, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_MAGNETDL, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_ONE337X, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_IDOPE, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_GLOTORRENTS, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_BT_DIGG, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_YT, Constants.IS_BASIC_AND_DEBUG || !Constants.IS_GOOGLE_PLAY_DISTRIBUTION);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_KNABEN, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTSCSV, true);

        defaultValues.put(Constants.PREF_KEY_NETWORK_ENABLE_DHT, true);
        defaultValues.put(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY, false);

        defaultValues.put(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, false);
        defaultValues.put(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY, true);

        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, 0L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, 0L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, 4L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_UPLOADS, 4L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, 200L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_MAX_PEERS, 200L);
        defaultValues.put(Constants.PREF_KEY_TORRENT_DELETE_STARTED_TORRENT_FILES, false);
        defaultValues.put(Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX, 1); // pieces
        defaultValues.put(Constants.PREF_KEY_TORRENT_SEQUENTIAL_TRANSFERS_ENABLED, false);

        // Incoming connection port range settings
        defaultValues.put(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, 1024);
        defaultValues.put(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END, 57000);

        defaultValues.put(Constants.PREF_KEY_STORAGE_PATH, Environment.getExternalStorageDirectory().getAbsolutePath()); // /mnt/sdcard

        defaultValues.put(Constants.PREF_KEY_GUI_PLAYER_REPEAT_MODE, MusicPlaybackService.REPEAT_ALL);
        defaultValues.put(Constants.PREF_KEY_GUI_PLAYER_SHUFFLE_ENABLED, false);

        resetValue(Constants.PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN);
        resetValue(Constants.PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN);
        resetValue(Constants.PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN);
        resetValue(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN);
        resetValue(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT);
        resetValue(Constants.PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX);
        resetValue(Constants.PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT);
        resetValue(Constants.PREF_KEY_MAIN_APPLICATION_ON_CREATE_TIMESTAMP);
    }

    private void resetValue(String key) {
        resetValues.put(key, defaultValues.get(key));
    }

    private static String uuidToString(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];

        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }
        for (int i = 8; i < 16; i++) {
            buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }

        return Hex.encode(buffer);
    }
}
