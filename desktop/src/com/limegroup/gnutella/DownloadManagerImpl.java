/*
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
