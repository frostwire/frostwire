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
public final class AndroidPaths implements SystemPaths {

    private static final String STORAGE_PATH = "FrostWire"; // for Android10+ it's not used
    public static final String TORRENT_DATA_PATH = "TorrentsData";
    public static final String TORRENTS_PATH = "Torrents";
    private static final String TEMP_PATH = "temp";
    private static final String LIBTORRENT_PATH = "libtorrent";

    private static final String UPDATE_APK_NAME = "frostwire.apk";

    private final Application app;
    private final File internalFilesDir; // internal files should go here

    // NEXT EXPERIMENT:
    // -> Save everything in internal storage.
    // -> Librarian.mediaStoreInsert using only an internal content uri's


    public AndroidPaths(Application app) {
        this.app = app;
        internalFilesDir = app.getFilesDir();
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
        return new File(internalFilesDir, TEMP_PATH);
    }

    @Override
    public File libtorrent() {
        return new File(internalFilesDir, LIBTORRENT_PATH);
    }

    /**
     * For Android 10+ we'll store all files in internal storage for now
     * We will use our file provider paths to share files with the outside world
     */
    private static File storage(Application app) {
        if (SystemUtils.hasAndroid11OrNewer()) {
            return app.getFilesDir();
        }
        /* For Older versions of Android where we used to have access to write to external storage
         *  <externalStoragePath>/FrostWire/
         */
        String path = ConfigurationManager.instance().getString(Constants.PREF_KEY_STORAGE_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());
        if (path.toLowerCase().endsWith("/" + STORAGE_PATH.toLowerCase())) {
            return new File(path);
        } else {
            return new File(path, STORAGE_PATH);
        }
    }
}
