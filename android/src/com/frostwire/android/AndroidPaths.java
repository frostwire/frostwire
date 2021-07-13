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
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.util.SystemUtils;
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
    public static final String TORRENT_DATA_PATH = "TorrentsData";
    public static final String TORRENTS_PATH = "Torrents";
    private static final String TEMP_PATH = "temp";
    private static final String LIBTORRENT_PATH = "libtorrent";
    private static final String UPDATE_APK_NAME = "frostwire.apk";

    private final Application app;
    private final File internalFilesDir;

    private static final boolean USE_EXTERNAL_STORAGE_DIR_ON_OR_AFTER_ANDROID_10 = true;

    private static final String VOLUME_EXTERNAL_NAME = SystemUtils.hasAndroid10OrNewer() ?
            MediaStore.VOLUME_EXTERNAL_PRIMARY :
            MediaStore.VOLUME_EXTERNAL;

    /**
     * If true uses MediaStore.Files, otherwise uses MediaStore.Downloads
     */
    private static final boolean USE_FILES_MEDIA_STORE = false;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public AndroidPaths(Application app) {
        this.app = app;
        internalFilesDir = app.getFilesDir();
        LOG.info("");
    }

    /**
     * /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/TorrentData
     * @return
     */
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

    private static volatile boolean APP_PATHS_SHOWN = false;


    /**
     *
     * getExternalFilesDir() + "/FrostWire"
     * /storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/
     * @param app
     * @return
     */
    private static File storage(Application app) {
        if (SystemUtils.hasAndroid10OrNewer()) {
            File externalDir = app.getExternalFilesDir(null);
            return new File(USE_EXTERNAL_STORAGE_DIR_ON_OR_AFTER_ANDROID_10 ?
                    externalDir : app.getFilesDir(),
                    STORAGE_PATH);
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

    /**
     * For Android 10+ the collection uri will be our internal URI
     * For older versions where we have external storage write access we use the external URI
     * <p>
     * These URIs are basically tables that will hold our audio, pictures, videos
     * <p>
     * The URL should have path names we define in filepaths.xml and provider_paths.xml
     * so that we can map their url subpaths to folders in external storage
     * <p>
     * mediaStoreVolumeUri will be internal, but let's parmetroize it in case we need external for something else
     * Valid values are MediaStore.VOLUME_INTERNAL | MediaStore.VOLUME_EXTERNAL_PRIMARY
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static Uri getMediaStoreCollectionUri(File file) {
        byte fileType = getFileType(file.getAbsolutePath(), true);
        return getMediaStoreCollectionUri(fileType, USE_EXTERNAL_STORAGE_DIR_ON_OR_AFTER_ANDROID_10);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static Uri getMediaStoreCollectionUri(byte fileType, boolean useExternalStorage) {
        String mediaStoreVolume = VOLUME_EXTERNAL_NAME;
        if (!useExternalStorage) {
            mediaStoreVolume = MediaStore.VOLUME_INTERNAL;
        }
        switch (fileType) {
            case Constants.FILE_TYPE_AUDIO:
                return SystemUtils.hasAndroid10OrNewer() ?
                        MediaStore.Audio.Media.getContentUri(mediaStoreVolume) :
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            case Constants.FILE_TYPE_PICTURES:
                return SystemUtils.hasAndroid10OrNewer() ?
                        MediaStore.Images.Media.getContentUri(mediaStoreVolume) :
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            case Constants.FILE_TYPE_VIDEOS:
                return SystemUtils.hasAndroid10OrNewer() ?
                        MediaStore.Video.Media.getContentUri(mediaStoreVolume) :
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            case Constants.FILE_TYPE_APPLICATIONS:
            case Constants.FILE_TYPE_TORRENTS:
            case Constants.FILE_TYPE_DOCUMENTS:
            case Constants.FILE_TYPE_FILES:
                return SystemUtils.hasAndroid10OrNewer() ?
                        ((USE_FILES_MEDIA_STORE) ?
                                MediaStore.Files.getContentUri(mediaStoreVolume) :
                                MediaStore.Downloads.getContentUri(mediaStoreVolume)) :
                        ((USE_FILES_MEDIA_STORE) ?
                                MediaStore.Files.getContentUri(VOLUME_EXTERNAL_NAME) :
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI);
        }
        return USE_FILES_MEDIA_STORE && SystemUtils.hasAndroid10OrNewer() ?
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL_NAME) :
                MediaStore.Downloads.EXTERNAL_CONTENT_URI;
    }

    public static byte getFileType(String filePath, boolean returnTorrentsAsDocument) {
        byte result = Constants.FILE_TYPE_DOCUMENTS;

        MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(filePath));

        if (mt != null) {
            result = (byte) mt.getId();
        }

        if (returnTorrentsAsDocument && result == Constants.FILE_TYPE_TORRENTS) {
            result = Constants.FILE_TYPE_DOCUMENTS;
        }

        return result;
    }

    private static final Map<Byte, String> fileTypeFolders = new HashMap<>();
    private static final Object fileTypeFoldersLock = new Object();

    public static File getExternalDestFolder(Context context, File srcFile) {
        ///storage/emulated/0/Android/data/com.frostwire.android/files/FrostWire/TorrentsData/creep-soundcloud.mp3 ->
        //content://com.frostwire.android.fileprovider/external_files/FrostWire/TorrentsData/creep-soundcloud.mp3

        // Copy file to shared external media folder
        byte fileType = AndroidPaths.getFileType(srcFile.getAbsolutePath(), true);
        // destFolder ->  /storage/emulated/0/Android/data/com.frostwire.android/files/Music/FrostWire
        String subFolder = fileTypeFolders.get(fileType);
        File destFolder = new File(context.getExternalFilesDir(null), subFolder + "/FrostWire");
        return destFolder;
    }

    /**
     * FILE_TYPE_AUDIO -> "Music"
     * FILE_TYPE_VIDEOS -> "Movies"
     * ...
     * Based on Android's Environment.DIRECTORY_XXX constants
     * We'll use these for MediaStore relative path prefixes concatenated to "/FrostWire" so the user
     * can easily find what's been downloaded with FrostWire in external folders.
     */
    public static String getFileTypeExternalRelativeFolderName(byte fileType) {
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
