/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes.ipfilter;

import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.options.panes.IPFilterPaneItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

public class IPFilterHttpListener implements HttpClient.HttpClientListener {
    private static final Logger LOG = Logger.getLogger(IPFilterHttpListener.class);
    private final IPFilterPaneItem ipFilterPaneItem;
    private final File downloadedFile;
    private final FileOutputStream fos;
    private final String downloadingString = I18n.tr("Downloading");
    private int totalRead = 0;
    private long contentLength = -1;

    public IPFilterHttpListener(IPFilterPaneItem ipFilterPaneItem, File downloadedFile) throws FileNotFoundException {
        this.downloadedFile = downloadedFile;
        this.ipFilterPaneItem = ipFilterPaneItem;
        try {
            fos = new FileOutputStream(downloadedFile);
        } catch (FileNotFoundException e) {
            LOG.error("IPFilterHttpListener can't create output stream -> " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onError(HttpClient client, Throwable e) {
        LOG.info("onError(): " + e.getMessage());
        LOG.error(e.getMessage(), e);
        enableImportControls();
    }

    @Override
    public void onData(HttpClient client, byte[] buffer, int offset, int length) {
        totalRead += length;
        try {
            fos.write(buffer, offset, length);
        } catch (Throwable t) {
            onError(client, t);
            return;
        }
        if (contentLength != -1) {
            updateProgressBar((int) ((totalRead * 100.0f / contentLength)), downloadingString);
        }
    }

    @Override
    public void onComplete(HttpClient client) {
        LOG.info("onComplete()");
        try {
            fos.flush();
            fos.close();
        } catch (Throwable t) {
            LOG.error("onComplete(): " + t.getMessage(), t);
            onError(client, t);
            return;
        }
        updateProgressBar(100, "");
        enableImportControls();
        importFromIPBlockFileAsync(downloadedFile);
    }

    @Override
    public void onCancel(HttpClient client) {
        enableImportControls();
    }

    @Override
    public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
        LOG.info("onHeaders()");
        if (headerFields != null && headerFields.containsKey("Content-Length")) {
            List<String> contentLengthHeader = headerFields.get("Content-Length");
            if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
                contentLength = Long.parseLong(contentLengthHeader.get(0));
            }
        }
    }

    private void enableImportControls() {
        ipFilterPaneItem.enableImportControls(true);
    }

    private void updateProgressBar(int progress, String statusString) {
        ipFilterPaneItem.updateProgressBar(progress, statusString);
    }

    private void importFromIPBlockFileAsync(File downloadedFile) {
        ipFilterPaneItem.importFromIPBlockFileAsync(downloadedFile, true);
    }
}