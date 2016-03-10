/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.core;

import com.frostwire.android.BuildConfig;

/**
 * Static class containing all constants in one place.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Constants {

    private Constants() {
    }

    public static final boolean IS_GOOGLE_PLAY_DISTRIBUTION = BuildConfig.FLAVOR.equals("basic") && !BuildConfig.DEBUG;

    public static final boolean IS_FREE_DISTRIBUTION = true;

    public static final String BUILD_PREFIX = !IS_GOOGLE_PLAY_DISTRIBUTION ? "1000" : "";

    /**
     * This occurs when developing on IDEA, but we want to treat it as a plus build.
     */
    public static final boolean IS_BASIC_DEBUG = BuildConfig.FLAVOR.equals("basic") && BuildConfig.DEBUG;

    /**
     * should manually match the manifest, here for convenience so we can ask for it from static contexts without
     * needing to pass the Android app context to obtain the PackageManager instance.
     */
    public static final String FROSTWIRE_BUILD = BUILD_PREFIX + (BuildConfig.VERSION_CODE % 1000);

    public static final String APP_PACKAGE_NAME = "com.frostwire.android";

    public static final String FROSTWIRE_VERSION_STRING = BuildConfig.VERSION_NAME;

    // preference keys
    public static final String PREF_KEY_CORE_UUID = "frostwire.prefs.core.uuid";
    public static final String PREF_KEY_CORE_LAST_SEEN_VERSION = "frostwire.prefs.core.last_seen_version";
    public static final String PREF_KEY_CORE_EXPERIMENTAL = "frostwire.prefs.core.enable_experimental";

    public static final String PREF_KEY_NETWORK_ENABLE_DHT = "froswire.prefs.network.enable_dht";
    public static final String PREF_KEY_NETWORK_USE_MOBILE_DATA = "frostwire.prefs.network.use_mobile_data";
    public static final String PREF_KEY_NETWORK_MAX_CONCURRENT_UPLOADS = "frostwire.prefs.network.max_concurrent_uploads";

    public static final String PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.count_download_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.count_rounds_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.interval_ms_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.min_seeds_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT = "frostwire.prefs.search.min_seeds_for_torrent_result";
    public static final String PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX = "frostwire.prefs.search.max_torrent_files_to_index";
    public static final String PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT = "frostwire.prefs.search.fulltext_search_results_limit";

    public static final String PREF_KEY_SEARCH_USE_EXTRATORRENT = "frostwire.prefs.search.use_extratorrent";
    public static final String PREF_KEY_SEARCH_USE_MININOVA = "frostwire.prefs.search.use_mininova";
    public static final String PREF_KEY_SEARCH_USE_VERTOR = "frostwire.prefs.search.use_vertor";
    public static final String PREF_KEY_SEARCH_USE_YOUTUBE = "frostwire.prefs.search.use_youtube";
    public static final String PREF_KEY_SEARCH_USE_SOUNDCLOUD = "frostwire.prefs.search.use_soundcloud";
    public static final String PREF_KEY_SEARCH_USE_ARCHIVEORG = "frostwire.prefs.search.use_archiveorg";
    public static final String PREF_KEY_SEARCH_USE_FROSTCLICK = "frostwire.prefs.search.use_frostclick";
    public static final String PREF_KEY_SEARCH_USE_BITSNOOP = "frostwire.prefs.search.use_bitsnoop";
    public static final String PREF_KEY_SEARCH_USE_TORLOCK = "frostwire.prefs.search.use_torlock";
    public static final String PREF_KEY_SEARCH_USE_EZTV = "frostwire.prefs.search.use_eztv";
    public static final String PREF_KEY_SEARCH_USE_APPIA = "frostwire.prefs.search.use_appia";
    public static final String PREF_KEY_SEARCH_USE_TPB = "frostwire.prefs.search.use_tpb";
    public static final String PREF_KEY_SEARCH_USE_MONOVA = "frostwire.prefs.search.use_monova";
    public static final String PREF_KEY_SEARCH_USE_YIFY = "frostwire.prefs.search.use_yify";
    public static final String PREF_KEY_SEARCH_USE_TORRENTSFM = "frostwire.prefs.search.use_torrentsfm";
    public static final String PREF_KEY_SEARCH_USE_BTJUNKIE = "frostwire.prefs.search.use_btjunkie";
    public static final String PREF_KEY_SEARCH_USE_KAT = "frostwire.prefs.search.use_kat";

    public static final String PREF_KEY_SEARCH_PREFERENCE_CATEGORY = "frostwire.prefs.search.preference_category";

    public static final String PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD = "frostwire.prefs.gui.vibrate_on_finished_download";
    public static final String PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER = "frostwire.prefs.gui.last_media_type_filter";
    public static final String PREF_KEY_GUI_TOS_ACCEPTED = "frostwire.prefs.gui.tos_accepted";
    public static final String PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET = "frostwire.prefs.gui.already_rated_in_market";
    public static final String PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER = "frostwire.prefs.gui.finished_downloads_between_ratings_reminder";
    public static final String PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE = "frostwire.prefs.gui.initial_settings_complete";
    public static final String PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION = "frostwire.prefs.gui.enable_permanent_status_notification";
    public static final String PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START = "frostwire.prefs.gui.show_transfers_on_download_start";
    public static final String PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG = "frostwire.prefs.gui.show_new_transfer_dialog";
    public static final String PREF_KEY_GUI_SUPPORT_FROSTWIRE = "frostwire.prefs.gui.support_frostwire";
    public static final String PREF_KEY_GUI_SUPPORT_FROSTWIRE_THRESHOLD = "frostwire.prefs.gui.support_frostwire_threshold";
    public static final String PREF_KEY_GUI_USE_MOBILE_CORE = "frostwire.prefs.gui.use_mobile_core";
    public static final String PREF_KEY_GUI_USE_APPLOVIN = "frostwire.prefs.gui.use_applovin";
    public static final String PREF_KEY_GUI_USE_INMOBI = "frostwire.prefs.gui.use_inmobi";
    public static final String PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS = "frostwire.prefs.gui.interstitial_offers_transfer_starts";
    public static final String PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = "frostwire.prefs.gui.interstitial_transfer_offers_timeout_in_minutes";

    public static final String PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED = "frostwire.prefs.torrent.max_download_speed";
    public static final String PREF_KEY_TORRENT_MAX_UPLOAD_SPEED = "frostwire.prefs.torrent.max_upload_speed";
    public static final String PREF_KEY_TORRENT_MAX_DOWNLOADS = "frostwire.prefs.torrent.max_downloads";
    public static final String PREF_KEY_TORRENT_MAX_UPLOADS = "frostwire.prefs.torrent.max_uploads";
    public static final String PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS = "frostwire.prefs.torrent.max_total_connections";
    public static final String PREF_KEY_TORRENT_MAX_PEERS = "frostwire.prefs.torrent.max_peers";
    public static final String PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS = "frostwire.prefs.torrent.seed_finished_torrents";
    public static final String PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY = "frostwire.prefs.torrent.seed_finished_torrents_wifi_only";

    public static final String PREF_KEY_STORAGE_PATH = "frostwire.prefs.storage.path";

    public static final String PREF_KEY_UXSTATS_ENABLED = "frostwire.prefs.uxstats.enabled";

    public static final String PREF_KEY_SHOW_ABOUT = "frostwire.prefs.show_about";

    public static final String ACTION_OPEN_TORRENT_URL = "android.intent.action.VIEW";
    public static final String ACTION_REQUEST_SHUTDOWN = "com.frostwire.android.ACTION_REQUEST_SHUTDOWN";
    public static final String ACTION_SHOW_TRANSFERS = "com.frostwire.android.ACTION_SHOW_TRANSFERS";
    public static final String ACTION_SHOW_VPN_STATUS_PROTECTED = "com.frostwire.android.ACTION_SHOW_VPN_STATUS_PROTECTED";
    public static final String ACTION_SHOW_VPN_STATUS_UNPROTECTED = "com.frostwire.android.ACTION_SHOW_VPN_STATUS_UNPROTECTED";
    public static final String ACTION_START_TRANSFER_FROM_PREVIEW = "com.frostwire.android.ACTION_START_TRANSFER_FROM_PREVIEW";
    public static final String ACTION_MEDIA_PLAYER_PLAY = "com.frostwire.android.ACTION_MEDIA_PLAYER_PLAY";
    public static final String ACTION_MEDIA_PLAYER_STOPPED = "com.frostwire.android.ACTION_MEDIA_PLAYER_STOPPED";
    public static final String ACTION_MEDIA_PLAYER_PAUSED = "com.frostwire.android.ACTION_MEDIA_PLAYER_PAUSED";
    public static final String ACTION_REFRESH_FINGER = "com.frostwire.android.ACTION_REFRESH_FINGER";
    public static final String ACTION_SETTINGS_SELECT_STORAGE = "com.frostwire.android.ACTION_SETTINGS_SELECT_STORAGE";
    public static final String ACTION_NOTIFY_SDCARD_MOUNTED = "com.frostwire.android.ACTION_NOTIFY_SDCARD_MOUNTED";
    public static final String EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION = "com.frostwire.android.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION";
    public static final String EXTRA_DOWNLOAD_COMPLETE_PATH = "com.frostwire.android.EXTRA_DOWNLOAD_COMPLETE_PATH";

    public static final String BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION = "com.frostwire.android.BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION.";

    public static final int NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED = 1001;

    // generic file types
    public static final byte FILE_TYPE_AUDIO = 0x00;
    public static final byte FILE_TYPE_PICTURES = 0x01;
    public static final byte FILE_TYPE_VIDEOS = 0x02;
    public static final byte FILE_TYPE_DOCUMENTS = 0x03;
    public static final byte FILE_TYPE_APPLICATIONS = 0x04;
    public static final byte FILE_TYPE_RINGTONES = 0x05;
    public static final byte FILE_TYPE_TORRENTS = 0x06;

    public static final String MIME_TYPE_ANDROID_PACKAGE_ARCHIVE = "application/vnd.android.package-archive";
    public static final String MIME_TYPE_BITTORRENT = "application/x-bittorrent";

    /**
     * URL where FrostWire checks for software updates
     */
    public static final String SERVER_UPDATE_URL = "http://update.frostwire.com/android";

    public static final String FROM_URL_PARAMETERS = "from=android&basic=" + (IS_GOOGLE_PLAY_DISTRIBUTION ? "1" : "0") + "&version=" + FROSTWIRE_VERSION_STRING + "&build=" + FROSTWIRE_BUILD;

    public static final String FROSTWIRE_PLUS_URL = "http://www.frostwire.com/android?" + FROM_URL_PARAMETERS;

    public static final String SERVER_PROMOTIONS_URL = "http://update.frostwire.com/o.php?" + FROM_URL_PARAMETERS;

    public static final String SUPPORT_URL = "http://support.frostwire.com/hc/en-us/categories/200014385-FrostWire-for-Android";

    public static final long LIBRARIAN_FILE_COUNT_CACHE_TIMEOUT = 2 * 60 * 1000; // 2 minutes

    public static final String MOBILE_CORE_DEVHASH = "6OJUVFECLGYH3JKYZB41VLQA4JXW5";
    public static final String INMOBI_INTERSTITIAL_PROPERTY_ID = "c1e6be702d614523b725af8b86f99e8f";

    public static final int NOTIFIED_BLOOM_FILTER_BITSET_SIZE = 320000; //40 kilobytes
    public static final int NOTIFIED_BLOOM_FILTER_EXPECTED_ELEMENTS = 10000;

    /**
     * Social Media official URLS
     */
    public static final String SOCIAL_URL_FACEBOOK_PAGE = "https://www.facebook.com/FrostwireOfficial";
    public static final String SOCIAL_URL_TWITTER_PAGE = "https://twitter.com/frostwire";
    public static final String SOCIAL_URL_REDDIT_PAGE = "https://reddit.com/r/frostwire";

    public static final String VPN_LEARN_MORE_URL = "http://www.frostwire.com/vpn.expressvpn.learnmore";
    public static final String PIA_URL = "http://www.frostwire.com/vpn.pia";
    public static final String EXPRESSVPN_URL_BASIC = "http://www.frostwire.com/vpn.expressvpn";
    public static final String EXPRESSVPN_URL_PLUS = "http://www.frostwire.com/vpn.expressvpn";
}
