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