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
import com.frostwire.platform.Platforms;
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
     *
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
     * Environment.getExternalStoragePublicDirectory() + "/Downloads/FrostWire" (This path won't work on android 10 even with legacy flag on)
     * /storage/emulated/0/Download/FrostWire/
     * <p>
     * /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/
     */
    private static File storage(Application app) {
        if (SystemUtils.hasAndroid10OrNewer()) {
            if (SystemUtils.hasAndroid10()) {
                return app.getExternalFilesDir(null);
            }

            // On Android 11 and up, they finally let us use File objects in the public download directory as long as we have permission from the user
            return android11AndUpStorage();
        }

        /* For Older versions of Android where we used to have access to write to external storage
         *  <externalStoragePath>/Download/FrostWire/
         */
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), STORAGE_PATH);
    }

    public static File android11AndUpStorage() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STORAGE_PATH);
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

    /**
     * /storage/emulated/0/Download/FrostWire/foo.png -> "Download/FrostWire"
     * /storage/emulated/0/Download/FrostWire/SomeFolder/bar.gif -> "Download/FrostWire/SomeTorrentFolder"
     * <p>
     * Should not have any trailing slashes, nor double slashes.
     */
    public static String getRelativeFolderPathFromFileInDownloads(File f) {
        if (BTEngine.ctx.dataDir == null) {
            throw new RuntimeException("AndroidPaths.getRelativeFolderPath() BTEngine.ctx.dataDir is null, check your logic");
        }

        // "Download/FrostWire"
        String commonRelativePrefix = Environment.DIRECTORY_DOWNLOADS + "/FrostWire";
        commonRelativePrefix = commonRelativePrefix.replace("//", "/").replaceAll("/\\z", "");

        String fullOriginalFilePath = f.getAbsolutePath();

        // Let's remove this from the fullOriginalFilePath and we should now have only either the file name by itself
        // or the torrent folders and sub-folders containing it
        String removedDataPathFromFilePath = fullOriginalFilePath;
        if (SystemUtils.hasAndroid10()) {
            // in case it's an internal file (android 10)
            removedDataPathFromFilePath = fullOriginalFilePath.
                    replace(BTEngine.ctx.dataDir.getAbsolutePath() + "/", "");
        }

        // this is the most likely prefix (android 11+)
        removedDataPathFromFilePath = removedDataPathFromFilePath.
                replace("/storage/emulated/0/", "");


        // Single file download, not contained by folders or sub-folders
        if (removedDataPathFromFilePath.equals(f.getName())) {
            return commonRelativePrefix;
        }

        String result = removedDataPathFromFilePath.replace(f.getName(), "").
                replaceAll("/\\z", ""); // remove trailing slash
        return result;
    }

    /**
     * /storage/emulated/0/Android/data/com.frostwire.android/files/myfile.ext -> /storage/emulated/0/Download/FrostWire/myfile.ext
     * /storage/emulated/0/Android/data/com.frostwire.android/files/folder/myfile.ext -> /storage/emulated/0/Download/FrostWire/folder/myfile.ext
     */
    public static File getDestinationFileFromInternalFileInAndroid10(File internalSourceFile) {
        String fullSourcePath = internalSourceFile.getAbsolutePath();

        // bar.ext
        String filename = internalSourceFile.getName();

        // Subtract data path from android 10
        // "/storage/emulated/0/Android/data/com.frostwire.android/files" ->
        String dataPath = Platforms.get().systemPaths().data().getAbsolutePath();

        String possiblyFolderAndFileName = fullSourcePath.replace(dataPath, "");
        String possiblyFolderName = possiblyFolderAndFileName.replace(filename, "");

        File android11StorageFolder = AndroidPaths.android11AndUpStorage();
        File destinationFolder = android11StorageFolder;

        if (!possiblyFolderName.equals("/") && possiblyFolderName.length() > 1) {
            destinationFolder = new File(android11StorageFolder, possiblyFolderName);
        }
        final File destinationFile = new File(destinationFolder, filename);
        return destinationFile;
    }
}
