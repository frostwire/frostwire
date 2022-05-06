/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android;

import android.app.Application;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.SystemPaths;
import com.frostwire.util.Logger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * @author gubatron
 * @author aldenml
 */
public final class AndroidPaths implements SystemPaths {
    private static final Logger LOG = Logger.getLogger(AndroidPaths.class);
    private static final String STORAGE_PATH = "FrostWire";
    public static final String TORRENTS_PATH = "Torrents";
    private static final String TEMP_PATH = "temp";
    private static final String LIBTORRENT_PATH = "libtorrent";
    private static final String UPDATE_APK_NAME = "frostwire.apk";

    private final Application app;
    private final File internalFilesDir;

    /**
     * Saves to shared public storage using Environment.getExternalStoragePublicDirectory() [deprecated, but fkcu it! this is what we need].
     * This is where the user will typically place and manage their own files, so we'll try to save to Downloads/FrostWire/
     */
    private static final boolean USE_EXTERNAL_STORAGE_PUBLIC_DIRECTORY_ON_OR_AFTER_ANDROID_10 = true;

    private static final String VOLUME_EXTERNAL_NAME = SystemUtils.hasAndroid10OrNewer() ?
            MediaStore.VOLUME_EXTERNAL_PRIMARY :
            MediaStore.VOLUME_EXTERNAL;

    private static final boolean USE_MEDIASTORE_DOWNLOADS = true;

    private static final Map<Byte, String> fileTypeFolders = new HashMap<>();
    private static final Object fileTypeFoldersLock = new Object();

    @RequiresApi(api = Build.VERSION_CODES.N)
    public AndroidPaths(Application app) {
        this.app = app;
        internalFilesDir = app.getFilesDir();
        LOG.info("");
    }

    /**
     * Downloads/FrostWire
     * (Used to be FrostWire/TorrentData)
     */
    @Override
    public File data() {
        return storage(app);
    }

    /**
     * Downloads/FrostWire/Torrents
     * @return
     */
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

    private static final boolean APP_PATHS_SHOWN = false;

    /**
     * Environment.getExternalStoragePublicDirectory() + "/FrostWire"
     * /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/
     */
    private static File storage(Application app) {
        if (SystemUtils.hasAndroid10OrNewer()) {
            if (USE_EXTERNAL_STORAGE_PUBLIC_DIRECTORY_ON_OR_AFTER_ANDROID_10) {
                return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STORAGE_PATH);
            } else {
                return new File(STORAGE_PATH);
            }
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

    public static byte getFileType(String filePath, boolean returnTorrentsAsDocument) {
        byte result = Constants.FILE_TYPE_UNKNOWN;

        MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(filePath));

        if (mt != null) {
            result = (byte) mt.getId();
        }

        if (returnTorrentsAsDocument && result == Constants.FILE_TYPE_TORRENTS) {
            result = Constants.FILE_TYPE_DOCUMENTS;
        }

        return result;
    }

    public static String getRelativeFolderPath(File f) {
        if (BTEngine.ctx.dataDir == null) {
            throw new RuntimeException("AndroidPaths.getRelativeFolderPath() BTEngine.ctx.dataDir is null, check your logic");
        }
        byte fileType = AndroidPaths.getFileType(f.getAbsolutePath(), true);

        // "Music","Movies","Pictures","Download"
        String fileTypeSubfolder = AndroidPaths.getFileTypeExternalRelativeFolderName(fileType);

        // "Music/FrostWire"
        String mediaStoreFolderPrefix = fileTypeSubfolder + "/FrostWire";
        mediaStoreFolderPrefix = mediaStoreFolderPrefix.replace("//", "/");

        String fullOriginalFilePath = f.getAbsolutePath();

        // BTEngine.ctx.dataDir -> /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/TorrentData
        // Let's remove this from the fullOriginalFilePath and we should now have only either the file name by itself
        // or the torrent folders and sub-folders containing it
        String removedDataPathFromFilePath = fullOriginalFilePath.replace(BTEngine.ctx.dataDir.getAbsolutePath() + "/", "");

        // Single file download, not contained by folders or sub-folders
        if (removedDataPathFromFilePath.equals(f.getName())) {
            return mediaStoreFolderPrefix;
        }

        String fileFoldersWithoutDataPath = removedDataPathFromFilePath.replace(f.getName(), "");
        return (mediaStoreFolderPrefix + "/" + fileFoldersWithoutDataPath).replace("//", "/");
    }

    /**
     * FILE_TYPE_AUDIO -> "Music"
     * FILE_TYPE_VIDEOS -> "Movies"
     * ...
     * Based on Android's Environment.DIRECTORY_XXX constants
     * We'll use these for MediaStore relative path prefixes concatenated to "/FrostWire" so the user
     * can easily find what's been downloaded with FrostWire in external folders.
     */
    private static String getFileTypeExternalRelativeFolderName(byte fileType) {
        synchronized (fileTypeFoldersLock) {
            // thread safe lazy load check
            if (fileTypeFolders.size() == 0) {
                fileTypeFolders.put(Constants.FILE_TYPE_AUDIO, Environment.DIRECTORY_MUSIC);
                fileTypeFolders.put(Constants.FILE_TYPE_VIDEOS, Environment.DIRECTORY_MOVIES);
                fileTypeFolders.put(Constants.FILE_TYPE_RINGTONES, Environment.DIRECTORY_RINGTONES);
                fileTypeFolders.put(Constants.FILE_TYPE_PICTURES, Environment.DIRECTORY_PICTURES);
                fileTypeFolders.put(Constants.FILE_TYPE_TORRENTS, Environment.DIRECTORY_DOWNLOADS);
                fileTypeFolders.put(Constants.FILE_TYPE_DOCUMENTS, Environment.DIRECTORY_DOCUMENTS);
            }
        }
        return fileTypeFolders.get(fileType);
    }
}
