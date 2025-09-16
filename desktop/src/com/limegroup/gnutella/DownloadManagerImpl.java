/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.UpdateSettings;

import java.io.File;

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
                        return;
                    }
                    String name = dl.getName();
                    if (name == null || name.contains("fetch_magnet:")) {
                        return;
                    }
                    File savePath = dl.getSavePath();
                    if (savePath != null && savePath.toString().contains("fetch_magnet")) {
                        return;
                    }
                    // don't add frostwire update downloads to the download manager.
                    if (savePath != null) {
                        final File parentFile = savePath.getParentFile();
                        if (parentFile != null) {
                            if (parentFile.getAbsolutePath().equals(UpdateSettings.UPDATES_DIR.getAbsolutePath())) {
                                LOG.info("Update download, not adding to transfer manager: " + savePath);
                                return;
                            }
                        } else if (savePath.getAbsolutePath().equals(UpdateSettings.UPDATES_DIR.getAbsolutePath())) {
                            // save path must have been a root folder, like D:\, so no parent file.
                            LOG.info("Update download, not adding to transfer manager: " + savePath);
                            return;
                        }
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
