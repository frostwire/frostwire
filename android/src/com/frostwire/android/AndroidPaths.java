/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android;

import android.app.Application;
import android.os.Environment;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.util.Logger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.concurrent.CountDownLatch;


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

    private final Application app;
    private volatile File internalFilesDir;

    public AndroidPaths(Application app) {
        this.app = app;
        // All disk operations must be done in the background
        final CountDownLatch waitForInternalFiles = new CountDownLatch(1);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            internalFilesDir = app.getFilesDir();
            waitForInternalFiles.countDown();
        });
        if (internalFilesDir == null) {
            try {
                // takes between 2 o 5 ms to get the internal files dir
                waitForInternalFiles.await();
            } catch (InterruptedException e) {
                LOG.error("AndroidPaths: Error waiting for internal files dir (time out?)", e);
            }
        } else {
            LOG.info("AndroidPaths: Internal Files Dir: " + internalFilesDir.getAbsolutePath() + " (already set) no need to wait for thread");
        }
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

        // remove trailing slash
        return removedDataPathFromFilePath.replace(f.getName(), "").
                replaceAll("/\\z", "");
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
        return new File(destinationFolder, filename);
    }
}
