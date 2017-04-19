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

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public interface AppSettings {

    String string(String key);

    void string(String key, String value);

    int int32(String key);

    void int32(String key, int value);

    long int64(String key);

    void int64(String key, long value);

    boolean bool(String key);

    void bool(String key, boolean value);

    // keys

    String SEARCH_EXTRATORRENT_ENABLED = "search_extratorrent_enabled";
    String SEARCH_ZOOQLE_ENABLED = "search_zooqle_enabled";
    String SEARCH_YOUTUBE_ENABLED = "search_youtube_enabled";
    String SEARCH_SOUNDCLOUD_ENABLED = "search_soundcloud_enabled";
    String SEARCH_ARCHIVE_ENABLED = "search_archive_enabled";
    String SEARCH_FROSTCLICK_ENABLED = "search_frostclick_enabled";
    String SEARCH_TORLOCK_ENABLED = "search_torlock_enabled";
    String SEARCH_EZTV_ENABLED = "search_eztv_enabled";
    String SEARCH_TBP_ENABLED = "search_tbp_enabled";
    String SEARCH_MONOVA_ENABLED = "search_monova_enabled";
    String SEARCH_YIFY_ENABLED = "search_yify_enabled";
    String SEARCH_TORRENTDOWNLOADS_ENABLED = "search_torrentdownloads_enabled";
    String SEARCH_LIMETORRENTS_ENABLED = "search_limetorrents_enabled";
}
