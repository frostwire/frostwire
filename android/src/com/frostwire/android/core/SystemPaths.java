/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import android.content.Context;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemPaths {

    private static final String LIBTORRENT_PATH = "libtorrent";
    private static final String APP_STORAGE_PATH = "FrostWire";
    private static final String TORRENTS_PATH = "Torrents";
    private static final String TORRENT_DATA_PATH = "TorrentsData";
    private static final String TEMP_PATH = "Temp";

    private static final String AUDIO_PATH = "Music";
    private static final String PICTURES_PATH = "Pictures";
    private static final String VIDEOS_PATH = "Videos";
    private static final String DOCUMENTS_PATH = "Documents";
    private static final String APPLICATIONS_PATH = "Applications";
    private static final String RINGTONES_PATH = "Ringtones";

    private static final String APPLICATION_APK_NAME = "frostwire.apk";

    private SystemPaths() {
    }

    public static File getLibTorrent(Context context) {
        return new File(context.getExternalFilesDir(null), LIBTORRENT_PATH);
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
        return new File(getAppStorage(), TEMP_PATH);
    }

    public static File getSaveDirectory(byte fileType) {
        File parentFolder = getAppStorage();

        String folderName;

        switch (fileType) {
            case Constants.FILE_TYPE_AUDIO:
                folderName = AUDIO_PATH;
                break;
            case Constants.FILE_TYPE_PICTURES:
                folderName = PICTURES_PATH;
                break;
            case Constants.FILE_TYPE_VIDEOS:
                folderName = VIDEOS_PATH;
                break;
            case Constants.FILE_TYPE_DOCUMENTS:
                folderName = DOCUMENTS_PATH;
                break;
            case Constants.FILE_TYPE_APPLICATIONS:
                folderName = APPLICATIONS_PATH;
                break;
            case Constants.FILE_TYPE_RINGTONES:
                folderName = RINGTONES_PATH;
                break;
            case Constants.FILE_TYPE_TORRENTS:
                folderName = TORRENTS_PATH;
                break;
            default: // We will treat anything else like documents (unknown types)
                folderName = DOCUMENTS_PATH;
        }

        return new File(parentFolder, folderName);
    }

    public static File getUpdateApk() {
        return new File(getSaveDirectory(Constants.FILE_TYPE_APPLICATIONS), APPLICATION_APK_NAME);
    }
}
