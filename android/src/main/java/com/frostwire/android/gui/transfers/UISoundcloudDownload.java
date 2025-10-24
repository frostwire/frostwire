/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.transfers;

import android.content.Context;
import android.media.MediaScannerConnection;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.android.util.TorrentUtils;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

/**
 * @author aldenml
 * @author gubatron
 */
public class UISoundcloudDownload extends SoundcloudDownload {

    private final TransferManager manager;
    private static final Logger LOG = Logger.getLogger(UISoundcloudDownload.class);

    public UISoundcloudDownload(TransferManager manager, SoundcloudSearchResult sr) {
        super(sr);
        this.manager = manager;
    }

    @Override
    public void remove(boolean deleteData) {
        super.remove(deleteData);

        manager.remove(this);
    }

    @Override
    protected void onComplete() {
        manager.incrementDownloadsToReview();
        Engine.instance().notifyDownloadFinished(getDisplayName(), savePath);
        
        // Seed the finished HTTP download if seeding is enabled
        TorrentUtils.seedFinishedHttpDownloadIfEnabled(savePath, getDisplayName(), Constants.FILE_TYPE_AUDIO, manager);
    }

    @Override
    protected void moveAndComplete(File src, File dst) {
        super.moveAndComplete(src, dst);
        if (SystemUtils.hasAndroid11OrNewer()) {
            Context context = SystemUtils.getApplicationContext();
            if (context == null) {
                return;
            }
            MediaScannerConnection.scanFile(context,
                    new String[]{dst.getAbsolutePath()},
                    new String[]{MimeDetector.getMimeType(FilenameUtils.getExtension(dst.getName()))},
                    (path, uri) -> LOG.info("UISoundCloudDownload::moveAndComplete() -> mediaScan complete on " + dst));
        }
    }
}
