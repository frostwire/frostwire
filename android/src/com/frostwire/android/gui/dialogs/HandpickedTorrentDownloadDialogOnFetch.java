/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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


package com.frostwire.android.gui.dialogs;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.frostwire.android.gui.transfers.TorrentFetcherListener;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * Created on 4/20/16, in Denver, CO.
 *
 * @author gubatron
 * @author aldenml
 *
 */
public class HandpickedTorrentDownloadDialogOnFetch implements TorrentFetcherListener {
    private final WeakReference<Context> contextRef;
    private final WeakReference<FragmentManager> fragmentManagerRef;
    private static final Logger LOG = Logger.getLogger(HandpickedTorrentDownloadDialogOnFetch.class);
    private final boolean openTransfersOnCancel;

    public HandpickedTorrentDownloadDialogOnFetch(AppCompatActivity activity, boolean _openTransfersOnCancel) {
        contextRef = Ref.weak(activity);
        fragmentManagerRef = Ref.weak(activity.getSupportFragmentManager());
        openTransfersOnCancel = _openTransfersOnCancel;
    }

    @Override
    public void onTorrentInfoFetched(byte[] torrentInfoData, String magnetUri, long torrentFetcherDownloadTokenId) {
        createHandpickedTorrentDownloadDialog(torrentInfoData, magnetUri, torrentFetcherDownloadTokenId, openTransfersOnCancel);
    }

    private void createHandpickedTorrentDownloadDialog(
            byte[] torrentInfoData,
            String magnetUri,
            long torrentFetcherDownloadTokenId,
            boolean openTransfersOnCancel) {

        if (!Ref.alive(contextRef) ||
                !Ref.alive(fragmentManagerRef) ||
                torrentInfoData == null || torrentInfoData.length == 0) {
            LOG.warn("Incomplete conditions to create HandpickedTorrentDownloadDialog.");
            return;
        }

        FragmentManager fragmentManager = fragmentManagerRef.get();
        if (fragmentManager == null) {
            LOG.warn("FragmentManager is null. Cannot show dialog.");
            return;
        }

        String dialogTag = "HANDPICKED_TORRENT_DOWNLOAD_DIALOG";

        // Check if the dialog is already present
        if (fragmentManager.findFragmentByTag(dialogTag) != null) {
            LOG.warn("HandpickedTorrentDownloadDialog is already shown.");
            return;
        }

        try {
            LOG.info("createHandpickedTorrentDownloadDialog!");
            final HandpickedTorrentDownloadDialog dlg =
                    HandpickedTorrentDownloadDialog.newInstance(
                            contextRef.get(),
                            TorrentInfo.bdecode(torrentInfoData),
                            magnetUri,
                            torrentFetcherDownloadTokenId,
                            openTransfersOnCancel);

            dlg.show(fragmentManager, dialogTag);
        } catch (Throwable t) {
            LOG.warn("Could not create or show HandpickedTorrentDownloadDialog", t);
        }
    }

}
