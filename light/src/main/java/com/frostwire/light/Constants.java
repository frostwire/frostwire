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

package com.frostwire.light;

public final class Constants {
    public static final int FROSTWIRE_BUILD = 1;
    public static final String FROSTWIRE_VERSION_STRING = "0.1";

    // preference keys
    public static final String PREF_KEY_CORE_UUID = "frostwire.prefs.core.uuid";
    public static final String PREF_KEY_CORE_LAST_SEEN_VERSION = "frostwire.prefs.core.last_seen_version";
    public static final String PREF_KEY_MAIN_APPLICATION_ON_CREATE_TIMESTAMP = "frostwire.prefs.core.main_application_on_create_timestamp";

    public static final String PREF_KEY_NETWORK_ENABLE_DHT = "froswire.prefs.network.enable_dht";

    public static final String PREF_KEY_NETWORK_USE_WIFI_ONLY = "frostwire.prefs.network.use_wifi_only";
    public static final String PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY = "frostwire.prefs.network.bittorrent_on_vpn_only";

    public static final String PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.count_download_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.count_rounds_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.interval_ms_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN = "frostwire.prefs.search.min_seeds_for_torrent_deep_scan";
    public static final String PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT = "frostwire.prefs.search.min_seeds_for_torrent_result";
    public static final String PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX = "frostwire.prefs.search.max_torrent_files_to_index";
    public static final String PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT = "frostwire.prefs.search.fulltext_search_results_limit";

    public static final String PREF_KEY_SEARCH_USE_ZOOQLE = "frostwire.prefs.search.use_zooqle";
    public static final String PREF_KEY_SEARCH_USE_VERTOR = "frostwire.prefs.search.use_vertor";
    public static final String PREF_KEY_SEARCH_USE_YOUTUBE = "frostwire.prefs.search.use_youtube";
    public static final String PREF_KEY_SEARCH_USE_SOUNDCLOUD = "frostwire.prefs.search.use_soundcloud";
    public static final String PREF_KEY_SEARCH_USE_ARCHIVEORG = "frostwire.prefs.search.use_archiveorg";
    public static final String PREF_KEY_SEARCH_USE_FROSTCLICK = "frostwire.prefs.search.use_frostclick";
    public static final String PREF_KEY_SEARCH_USE_TORLOCK = "frostwire.prefs.search.use_torlock";
    public static final String PREF_KEY_SEARCH_USE_TORRENTDOWNLOADS = "frostwire.prefs.search.use_torrentdownloads";
    public static final String PREF_KEY_SEARCH_USE_LIMETORRENTS = "frostwire.prefs.search.use_limetorrents";
    public static final String PREF_KEY_SEARCH_USE_EZTV = "frostwire.prefs.search.use_eztv";
    public static final String PREF_KEY_SEARCH_USE_APPIA = "frostwire.prefs.search.use_appia";
    public static final String PREF_KEY_SEARCH_USE_TPB = "frostwire.prefs.search.use_tpb";
    public static final String PREF_KEY_SEARCH_USE_YIFY = "frostwire.prefs.search.use_yify";
    public static final String PREF_KEY_SEARCH_USE_TORRENTSFM = "frostwire.prefs.search.use_torrentsfm";
    public static final String PREF_KEY_SEARCH_USE_PIXABAY = "frostwire.prefs.search.use_pixabay";

    public static final String PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER = "frostwire.prefs.gui.last_media_type_filter";
    public static final String PREF_KEY_GUI_TOS_ACCEPTED = "frostwire.prefs.gui.tos_accepted";
    public static final String PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE = "frostwire.prefs.gui.initial_settings_complete";
    public static final String PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION = "frostwire.prefs.gui.enable_permanent_status_notification";
    public static final String PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED = "frostwire.prefs.gui.search.search.filter_drawer_button_clicked";
    public static final String PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START = "frostwire.prefs.gui.show_transfers_on_download_start";
    public static final String PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG = "frostwire.prefs.gui.show_new_transfer_dialog";
    public static final String PREF_KEY_GUI_USE_REMOVEADS = "frostwire.prefs.gui.use_removeads";
    public static final String PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD = "frostwire.prefs.gui.removeads_back_to_back_threshold";

    public static final String PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS = "frostwire.prefs.gui.interstitial_offers_transfer_starts";
    public static final String PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = "frostwire.prefs.gui.interstitial_transfer_offers_timeout_in_minutes";
    public static final String PREF_KEY_GUI_INTERSTITIAL_ON_BACK_THRESHOLD = "frostwire.prefs.gui.interstitial_on_back_threshold";
    public static final String PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY = "frostwire.prefs.gui.interstitial_on_resume_last_display";
    public static final String PREF_KEY_GUI_INSTALLATION_TIMESTAMP = "frostwire.prefs.gui.installation_timestamp";

    public static final String PREF_KEY_GUI_OFFERS_WATERFALL = "frostwire.prefs.gui.offers_waterfall";

    public static final String PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED = "frostwire.prefs.torrent.max_download_speed";
    public static final String PREF_KEY_TORRENT_MAX_UPLOAD_SPEED = "frostwire.prefs.torrent.max_upload_speed";
    public static final String PREF_KEY_TORRENT_MAX_DOWNLOADS = "frostwire.prefs.torrent.max_downloads";
    public static final String PREF_KEY_TORRENT_MAX_UPLOADS = "frostwire.prefs.torrent.max_uploads";
    public static final String PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS = "frostwire.prefs.torrent.max_total_connections";
    public static final String PREF_KEY_TORRENT_MAX_PEERS = "frostwire.prefs.torrent.max_peers";
    public static final String PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS = "frostwire.prefs.torrent.seed_finished_torrents";
    public static final String PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY = "frostwire.prefs.torrent.seed_finished_torrents_wifi_only";
    public static final String PREF_KEY_TORRENT_DELETE_STARTED_TORRENT_FILES = "frostwire.prefs.torrent.delete_started_torrent_files";
    public static final String PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX = "frostwire.prefs.torrent.transfer_detail_last_selected_tab_index";

    public static final String PREF_KEY_STORAGE_PATH = "frostwire.prefs.storage.path";

    public static final String PREF_KEY_UXSTATS_ENABLED = "frostwire.prefs.uxstats.enabled";

    public static final String ACTION_REQUEST_SHUTDOWN = "com.frostwire.android.ACTION_REQUEST_SHUTDOWN";
    public static final String ACTION_SHOW_TRANSFERS = "com.frostwire.android.ACTION_SHOW_TRANSFERS";
    public static final String ACTION_SHOW_VPN_STATUS_PROTECTED = "com.frostwire.android.ACTION_SHOW_VPN_STATUS_PROTECTED";
    public static final String ACTION_SHOW_VPN_STATUS_UNPROTECTED = "com.frostwire.android.ACTION_SHOW_VPN_STATUS_UNPROTECTED";
    public static final String ACTION_START_TRANSFER_FROM_PREVIEW = "com.frostwire.android.ACTION_START_TRANSFER_FROM_PREVIEW";
    public static final String ACTION_REFRESH_FINGER = "com.frostwire.android.ACTION_REFRESH_FINGER";
    public static final String ACTION_FILE_ADDED_OR_REMOVED = "com.frostwire.android.ACTION_FILE_ADDED_OR_REMOVED";
    public static final String ACTION_NOTIFY_UPDATE_AVAILABLE = "com.frostwire.android.NOTIFY_UPDATE_AVAILABLE";
    public static final String ACTION_NOTIFY_DATA_INTERNET_CONNECTION = "com.frostwire.android.NOTIFY_CHECK_INTERNET_CONNECTION";
    public static final String EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION = "com.frostwire.android.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION";
    public static final String EXTRA_DOWNLOAD_COMPLETE_PATH = "com.frostwire.android.EXTRA_DOWNLOAD_COMPLETE_PATH";
    public static final String EXTRA_REFRESH_FILE_TYPE = "com.frostwire.android.EXTRA_REFRESH_FILE_TYPE";
    public static final String EXTRA_FINISH_MAIN_ACTIVITY = "com.frostwire.android.EXTRA_FINISH_MAIN_ACTIVITY";
    public static final int NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED = 1001;

    // generic file types
    public static final byte FILE_TYPE_AUDIO = 0x00;
    public static final byte FILE_TYPE_PICTURES = 0x01;
    public static final byte FILE_TYPE_VIDEOS = 0x02;
    public static final byte FILE_TYPE_DOCUMENTS = 0x03;
    public static final byte FILE_TYPE_APPLICATIONS = 0x04;
    public static final byte FILE_TYPE_RINGTONES = 0x05;
    public static final byte FILE_TYPE_TORRENTS = 0x06;

    public static final String MIME_TYPE_BITTORRENT = "application/x-bittorrent";

    /**
     * URL where FrostWire checks for software updates
     */
    private static final String FROM_URL_PARAMETERS = "from=light&version=" + FROSTWIRE_VERSION_STRING + "&build=" + FROSTWIRE_BUILD;
    public static final String SERVER_UPDATE_URL = "http://update.frostwire.com/light?" + FROM_URL_PARAMETERS;
    public static final String FROSTWIRE_MORE_RESULTS = "http://www.frostwire.com/more.results";
    public static final String SERVER_PROMOTIONS_URL = "http://update.frostwire.com/o.php?" + FROM_URL_PARAMETERS;
    public static final String SUPPORT_URL = "http://support.frostwire.com/hc/en-us/categories/200014385-FrostWire-for-Android";
    public static final String TERMS_OF_USE_URL = "http://www.frostwire.com/terms";
    public static final String ALL_FEATURED_DOWNLOADS_URL = "http://www.frostwire.com/featured-downloads/";
    public static final String FROSTWIRE_PREVIEW_DOT_COM_URL = "http://www.frostwire-preview.com/";

    public static final String USER_AGENT = "FrostWire/light/" + Constants.FROSTWIRE_VERSION_STRING + "/" + Constants.FROSTWIRE_BUILD;

    /**
     * Social Media official URLS
     */
    public static final String SOCIAL_URL_FACEBOOK_PAGE = "https://www.facebook.com/FrostwireOfficial";
    public static final String SOCIAL_URL_TWITTER_PAGE = "https://twitter.com/frostwire";
    public static final String SOCIAL_URL_REDDIT_PAGE = "https://reddit.com/r/frostwire";
    public static final String SOCIAL_URL_GITHUB_PAGE = "https://github.com/frostwire/frostwire";

    public static final String FROSTWIRE_GIVE_URL = "http://www.frostwire.com/give/?from=";
    public static final String STICKERS_SHOP_URL = "http://www.frostwire.com/stickers";
    public static final String CONTACT_US_URL = "http://www.frostwire.com/contact";
    public static final String TRANSLATE_HELP_URL = "http://forum.frostwire.com/viewtopic.php?f=9&t=4922";
    public static final String CHANGELOG_URL = "https://github.com/frostwire/frostwire/blob/master/android/changelog.txt";

    private Constants() {
    }
}
