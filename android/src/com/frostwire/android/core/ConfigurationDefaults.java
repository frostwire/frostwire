/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
        defaultValues.put(Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER, (int) Constants.FILE_TYPE_AUDIO);
        defaultValues.put(Constants.PREF_KEY_GUI_TOS_ACCEPTED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET, false);
        defaultValues.put(Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER, 10);
        defaultValues.put(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE, false);
        defaultValues.put(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION, true);
        defaultValues.put(Constants.PREF_KEY_GUI_SEARCH_KEYWORDFILTERDRAWER_TIP_TOUCHTAGS_DISMISSED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED, false);
        defaultValues.put(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START, true);
        defaultValues.put(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG, true);
        defaultValues.put(Constants.PREF_KEY_GUI_USE_APPLOVIN, false);
        defaultValues.put(Constants.PREF_KEY_GUI_USE_REMOVEADS, true);
        defaultValues.put(Constants.PREF_KEY_GUI_USE_MOPUB, true);
        defaultValues.put(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_THRESHOLD, 80);
        defaultValues.put(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_DISMISS_INTERVAL_IN_MS, 60000); // 1 min
        defaultValues.put(Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD, 50);
        defaultValues.put(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS, 3);
        defaultValues.put(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES, 10);
        defaultValues.put(Constants.PREF_KEY_GUI_INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MINUTES, 3);
        defaultValues.put(Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP, -1L);
        defaultValues.put(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY, -1L);
        defaultValues.put(Constants.PREF_KEY_GUI_REWARD_AD_FREE_MINUTES, Constants.MIN_REWARD_AD_FREE_MINUTES);
        defaultValues.put(Constants.PREF_KEY_GUI_OFFERS_WATERFALL,
                new String[]{
                        Constants.AD_NETWORK_SHORTCODE_MOPUB,
                        Constants.AD_NETWORK_SHORTCODE_APPLOVIN,
                        Constants.AD_NETWORK_SHORTCODE_REMOVEADS
                });

        defaultValues.put(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON, true);
        defaultValues.put(Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH, false);
        defaultValues.put(Constants.PREF_KEY_ADNETWORK_ASK_FOR_LOCATION_PERMISSION, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN, 20);
        defaultValues.put(Constants.PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN, 10);
        defaultValues.put(Constants.PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN, 2000);
        defaultValues.put(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN, 20); // this number must be bigger than PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT to become relevant 
        defaultValues.put(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT, 20);
        defaultValues.put(Constants.PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX, 100); // no ultra big torrents here
        defaultValues.put(Constants.PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT, 256);

        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_VERTOR, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_ZOOQLE, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_YOUTUBE, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_FROSTCLICK, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORLOCK, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTDOWNLOADS, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_LIMETORRENTS, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_NYAA, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_EZTV, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_APPIA, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TPB, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_YIFY, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTSFM, true);
        defaultValues.put(Constants.PREF_KEY_SEARCH_USE_TORRENTZ2, true);

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
