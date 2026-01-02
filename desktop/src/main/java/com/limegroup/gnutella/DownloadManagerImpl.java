/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.UpdateSettings;

import java.io.File;
import java.util.Objects;

public final class DownloadManagerImpl implements DownloadManager {
    private static final Logger LOG = Logger.getLogger(DownloadManagerImpl.class);
    private final ActivityCallback activityCallback;

    public DownloadManagerImpl(ActivityCallback downloadCallback) {
        this.activityCallback = downloadCallback;
    }

    private void addDownload(BTDownload dl) {
        synchronized (this) {
            activityCallback.addDownload(dl);
        }
    }

    private void updateDownload(BTDownload dl) {
        synchronized (this) {
            activityCallback.updateDownload(dl);
        }
    }

    public void loadSavedDownloadsAndScheduleWriting() {
        try {
            BTEngine engine = BTEngine.getInstance();
            engine.setListener(new BTEngineAdapter() {
                @Override
                public void downloadAdded(BTEngine engine, BTDownload dl) {

                    if (engine == null || dl == null) {
                        LOG.info("DownloadManagerImpl::loadSavedDownloadsAndScheduleWriting::BTEngineListener::downloadAdded: engine or dl are null, aborted.");
                        return;
                    }
                    String name = dl.getName();
                    LOG.info("DownloadManagerImpl::loadSavedDownloadsAndScheduleWriting::BTEngineListener::downloadAdded: name=" + name);
                    if (name == null || name.contains("fetch_magnet:")) {
                        return;
                    }
                    File savePath = dl.getSavePath();
                    if (savePath.toString().contains("fetch_magnet")) {
                        return;
                    }
                    // don't add frostwire update downloads to the download manager.
                    final File parentFile = savePath.getParentFile();
                    // save path must have been a root folder, like D:\, so no parent file.
                    if (Objects.requireNonNullElse(parentFile, savePath).getAbsolutePath().equals(UpdateSettings.UPDATES_DIR.getAbsolutePath())) {
                        LOG.info("DownloadManagerImpl::loadSavedDownloadsAndScheduleWriting::BTEngineListener::downloadAdded: Update download, not adding to transfer manager: " + savePath);
                        return;
                    }
                    addDownload(dl);
                }

                @Override
                public void downloadUpdate(BTEngine engine, BTDownload dl) {
                    updateDownload(dl);
                }
            });
            engine.restoreDownloads();
        } catch (Throwable e) {
            LOG.error("General error loading saved downloads", e);
        }
    }
}
