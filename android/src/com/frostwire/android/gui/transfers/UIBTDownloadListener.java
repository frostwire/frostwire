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

package com.frostwire.android.gui.transfers;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.frostwire.android.AndroidPaths;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTDownloadListener;
import com.frostwire.platform.Platforms;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class UIBTDownloadListener implements BTDownloadListener {

    public static Logger LOG = Logger.getLogger(UIBTDownloadListener.class);

    @Override
    public void finished(BTDownload dl) {
        // this method will be called for all finished transfers even right after the app has been opened the first
        // time, right after it's done resuming transfers
        boolean paused = pauseSeedingIfNecessary(dl);
        TransferManager.instance().incrementDownloadsToReview();
        File savePath = dl.getSavePath().getAbsoluteFile(); // e.g. "Downloads/FrostWire"
        Engine engine = Engine.instance();
        engine.notifyDownloadFinished(dl.getDisplayName(), savePath, dl.getInfoHash());
        File torrentSaveFolder = dl.getContentSavePath();
        finalCleanup(dl, dl.getIncompleteFiles());
        fixBinPaths(torrentSaveFolder);


        if (SystemUtils.hasAndroid10()) {
            moveFinishedTransferItemsToMediaStoreAsync(dl);
        }
        MediaScannerConnection.scanFile(Engine.instance().getApplication(),
                new String[]{savePath.getAbsolutePath(),
                        dl.getSavePath().getAbsolutePath(),
                        Platforms.get().systemPaths().data().getAbsolutePath()},
                new String[]{"*/*"},
                (path, uri) -> LOG.info("UIBTDownloadListener::finished() -> mediaScan complete on " + path));
    }

    /**
     * We only use this method for Android 10.
     * The actual file copying to the Downloads/FrostWire folder
     * is done with the MediaStore API the main thread pool as to not block
     * the jlibtorrent's SessionManager alert loop thread while all the disk IO is happening
     */
    private static void moveFinishedTransferItemsToMediaStoreAsync(BTDownload dl) {
        // We write to /storage/emulated/0/Android/data/com.frostwire.android/files/
        // on Android10, therefore we need to use the media store to move
        // the downloaded files to the public Downloads/FrostWire folder
        dl.getItems().stream().filter(TransferItem::isComplete).forEach(item -> {
            // /storage/emulated/0/Android/data/com.frostwire.android/files/FOO_FOLDER/bar.ext
            final File sourceFile = item.getFile();
            // /storage/emulated/0/Download/FrostWire/FOO_FOLDER/bar.ext
            final File destinationFile = AndroidPaths.getDestinationFileFromInternalFileInAndroid10(sourceFile);

            // disk IO sent to DOWNLOADER handler thread
            SystemUtils.HandlerFactory.postTo(SystemUtils.HandlerThreadName.DOWNLOADER, () -> Librarian.mediaStoreSaveToDownloads(sourceFile, destinationFile));
        });
    }

    // The torrent's folder,e.g. Torrent Data/<foo folder>, not Torrent Data.
    private static void fixBinPaths(File torrentContentsFolder) {
        if (torrentContentsFolder != null && torrentContentsFolder.isDirectory()) {
            File[] files = torrentContentsFolder.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                if (f.isDirectory()) {
                    fixBinPaths(f);
                } else if (f.getAbsolutePath().endsWith(".bin")) {
                    String fileNameWithoutBin = f.getName().replace(".bin", "");
                    File renamed = new File(torrentContentsFolder, fileNameWithoutBin);
                    boolean renameSuccess = f.renameTo(renamed);
                    if (!renameSuccess) {
                        LOG.error("UIBTDownloadListener.fixBinPaths: failed to rename " + fileNameWithoutBin + " to " + renamed.getName());
                    } else {
                        LOG.info("UIBTDownloadListener.fixBinPaths: success renaming " + fileNameWithoutBin + " to " + renamed.getName());
                    }
                }
            }
        }
    }

    @Override
    public void removed(BTDownload dl, Set<File> incompleteFiles) {
        finalCleanup(dl, incompleteFiles);
    }

    private boolean pauseSeedingIfNecessary(BTDownload dl) {
        ConfigurationManager CM = ConfigurationManager.instance();
        boolean seedFinishedTorrents = CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
        boolean seedFinishedTorrentsOnWifiOnly = CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
        boolean isDataWIFIUp = NetworkManager.instance().isDataWIFIUp();
        boolean seedingDisabled = !seedFinishedTorrents || (!isDataWIFIUp && seedFinishedTorrentsOnWifiOnly);
        if (seedingDisabled) {
            dl.pause();
        }
        return seedingDisabled;
    }

    private void finalCleanup(BTDownload dl, Set<File> incompleteFiles) {
        if (incompleteFiles != null) {
            for (File f : incompleteFiles) {
                try {
                    if (f.exists() && !f.delete()) {
                        LOG.info("Can't delete file: " + f);
                    }
                } catch (Throwable e) {
                    LOG.info("Can't delete file: " + f);
                }
            }
        }

        deleteEmptyDirectoryRecursive(dl.getSavePath());
    }

    private static boolean deleteEmptyDirectoryRecursive(File directory) {
        // make sure we only delete canonical children of the parent file we
        // wish to delete. I have a hunch this might be an issue on OSX and
        // Linux under certain circumstances.
        // If anyone can test whether this really happens (possibly related to
        // symlinks), I would much appreciate it.
        String canonicalParent;
        try {
            canonicalParent = directory.getCanonicalPath();
        } catch (IOException ioe) {
            return false;
        }

        if (!directory.isDirectory()) {
            return false;
        }

        boolean canDelete = true;

        File[] files = directory.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                try {
                    if (!file.getCanonicalPath().startsWith(canonicalParent))
                        continue;
                } catch (IOException ioe) {
                    canDelete = false;
                }
                if (!deleteEmptyDirectoryRecursive(file)) {
                    canDelete = false;
                }
            }
        }

        return canDelete && directory.delete();
    }
}