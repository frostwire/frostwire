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

package com.frostwire.android.gui.transfers;

import android.content.Context;
import android.media.MediaScannerConnection;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
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
