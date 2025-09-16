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

package com.limegroup.gnutella.gui;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.gui.tabs.TransfersTab;
import com.limegroup.gnutella.ActivityCallback;

import javax.swing.*;
import java.io.File;

/**
 * This class is the gateway from the backend to the frontend.  It
 * delegates all callbacks to the appropriate frontend classes, and it
 * also handles putting calls onto the Swing thread as necessary.
 * <p/>
 * It implements the <tt>ActivityCallback</tt> callback interface, designed
 * to make it easy to swap UIs.
 */
public final class VisualConnectionCallback implements ActivityCallback {
    private static VisualConnectionCallback INSTANCE;

    private VisualConnectionCallback() {
    }

    public static VisualConnectionCallback instance() {
        if (INSTANCE == null) {
            INSTANCE = new VisualConnectionCallback();
        }
        return INSTANCE;
    }

    /**
     * Show active downloads
     */
    public void showDownloads() {
        SwingUtilities.invokeLater(() -> GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL));
    }

    /**
     * Tell the GUI to deiconify.
     */
    public void restoreApplication() {
        SwingUtilities.invokeLater(GUIMediator::restoreView);
    }

    /**
     * Returns the MainFrame.
     */
    private MainFrame mf() {
        return GUIMediator.instance().getMainFrame();
    }

    public void handleTorrent(final File torrentFile) {
        SwingUtilities.invokeLater(() -> GUIMediator.instance().openTorrentFile(torrentFile, false));
    }

    public void handleTorrentMagnet(final String request, final boolean partialDownload) {
        SwingUtilities.invokeLater(() -> {
            GUIMediator.instance().setRemoteDownloadsAllowed(partialDownload);
            System.out.println("VisualConnectionCallback about to call openTorrentURI of request.");
            System.out.println(request);
            GUIMediator.instance().openTorrentURI(request, partialDownload);
        });
    }

    @Override
    public void addDownload(BTDownload dl) {
        Runnable doWorkRunnable = new AddDownload(dl);
        GUIMediator.safeInvokeLater(doWorkRunnable);
    }

    @Override
    public void updateDownload(BTDownload dl) {
        // no need of running this in the UI thread
        mf().getBTDownloadMediator().updateDownload(dl);
    }

    public boolean isRemoteDownloadsAllowed() {
        try {
            SwingUtilities.invokeAndWait(GUIMediator::instance);
        } catch (Exception e) {
            System.out.println("Failed to create GUIMediator");
            e.printStackTrace();
        }
        return GUIMediator.instance().isRemoteDownloadsAllowed();
    }

    private class AddDownload implements Runnable {
        private final BTDownload mgr;

        AddDownload(BTDownload mgr) {
            this.mgr = mgr;
        }

        public void run() {
            mf().getBTDownloadMediator().addDownload(mgr);
        }
    }
}
