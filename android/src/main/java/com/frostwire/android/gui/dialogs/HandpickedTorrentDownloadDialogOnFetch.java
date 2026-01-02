/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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
