/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.frostclick.Slide;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

/**
 * @author aldenml
 * @author gubatron
 */
public class UIHttpDownload extends HttpDownload {

    private final TransferManager manager;
    private final Logger LOG = Logger.getLogger(UIHttpDownload.class);

    public UIHttpDownload(TransferManager manager, HttpSearchResult sr) {
        super(convert(sr));
        this.manager = manager;
    }

    public UIHttpDownload(TransferManager manager, Slide slide) {
        super(convert(slide));
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
            Context context;
            try {
                context = Engine.instance().getApplication();
                if (context == null) {
                    context = MainApplication.context();
                }
            } catch (Throwable ignored) {
                context = MainApplication.context();
            }
            if (context != null) {
                MediaScannerConnection.scanFile(context,
                        new String[]{dst.getAbsolutePath()},
                        new String[]{MimeDetector.getMimeType(FilenameUtils.getExtension(dst.getName()))},
                        (path, uri) -> LOG.info("UIHttpDownload::moveAndComplete() -> mediaScan complete on " + dst));
            }
        }
    }

    private static Info convert(HttpSearchResult sr) {
        return new Info(sr.getDownloadUrl(), sr.getFilename(), sr.getDisplayName(), sr.getSize());
    }

    private static Info convert(Slide slide) {
        return new Info(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL,
                FilenameUtils.getName(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL),
                slide.title,
                slide.size);
    }
}
