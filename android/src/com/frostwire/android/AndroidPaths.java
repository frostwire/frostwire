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

package com.frostwire.android;

import android.app.Application;
import android.os.Build;
import android.os.Environment;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.SystemPaths;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
final class AndroidPaths implements SystemPaths {

    private static final String STORAGE_PATH = "FrostWire";
    private static final String TORRENT_DATA_PATH = "TorrentsData";
    private static final String TORRENTS_PATH = "Torrents";
    private static final String TEMP_PATH = "temp";
    private static final String LIBTORRENT_PATH = "libtorrent";

    private static final String UPDATE_APK_NAME = "frostwire.apk";

    private final Application app;

    private static final boolean requestLegacyExternalStorage = false;

    public AndroidPaths(Application app) {
        this.app = app;
    }

    @Override
    public File data() {
        return new File(storage(app), TORRENT_DATA_PATH);
    }

    @Override
    public File torrents() {
        return new File(storage(app), TORRENTS_PATH);
    }

    @Override
    public File temp() {
        if (!requestLegacyExternalStorage || SystemUtils.hasAndroid11OrNewer()) {
            return new File(app.getFilesDir(), TEMP_PATH);
        }
        return new File(app.getExternalFilesDir(null), TEMP_PATH);
    }

    @Override
    public File libtorrent() {
        if (!requestLegacyExternalStorage || SystemUtils.hasAndroid11OrNewer()) {
            return new File(app.getFilesDir(), LIBTORRENT_PATH);
        }
        return new File(app.getExternalFilesDir(null), LIBTORRENT_PATH);
    }

    @Override
    public File update() {
        if (!requestLegacyExternalStorage || SystemUtils.hasAndroid11OrNewer()) {
            return new File(app.getFilesDir(), UPDATE_APK_NAME);
        }
        return new File(app.getExternalFilesDir(null), UPDATE_APK_NAME);
    }

    private static File storage(Application app) {
        if (!requestLegacyExternalStorage || SystemUtils.hasAndroid11OrNewer()) {
            //File result = Environment.getDataDirectory();
            //return app.getFilesDir();
            return app.getExternalFilesDir(null);
        }
        String path = ConfigurationManager.instance().getString(Constants.PREF_KEY_STORAGE_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());
        if (path.toLowerCase().endsWith("/" + STORAGE_PATH.toLowerCase())) {
            return new File(path);
        } else {
            return new File(path, STORAGE_PATH);
        }
    }
}
