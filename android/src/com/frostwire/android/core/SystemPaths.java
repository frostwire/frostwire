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

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemPaths {

    private static final String APP_STORAGE_PATH = "FrostWire";
    private static final String TORRENTS_PATH = "Torrents";
    private static final String TORRENT_DATA_PATH = "TorrentsData";

    private SystemPaths() {
    }

    public static File getAppStorage() {
        String path = ConfigurationManager.instance().getString(Constants.PREF_KEY_STORAGE_PATH);
        return new File(path, APP_STORAGE_PATH);
    }

    public static File getTorrents() {
        return new File(getAppStorage(), TORRENTS_PATH);
    }

    public static File getTorrentData() {
        return new File(getAppStorage(), TORRENT_DATA_PATH);
    }

    public static File getTemp() {
        String path = ConfigurationManager.instance().getString(Constants.PREF_KEY_STORAGE_TEMP_PATH);
        return new File(path);
    }
}
