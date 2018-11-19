/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
 *
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


package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;

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

    public HandpickedTorrentDownloadDialogOnFetch(Activity activity) {
        contextRef = Ref.weak(activity);
        fragmentManagerRef = Ref.weak(activity.getFragmentManager());
    }

    @Override
    public void onTorrentInfoFetched(byte[] torrentInfoData, String magnetUri, long torrentFetcherDownloadTokenId) {
        createHandpickedTorrentDownloadDialog(torrentInfoData, magnetUri, torrentFetcherDownloadTokenId);
    }

    private void createHandpickedTorrentDownloadDialog(byte[] torrentInfoData, String magnetUri, long torrentFetcherDownloadTokenId) {
        if (!Ref.alive(contextRef) ||
            !Ref.alive(fragmentManagerRef) ||
            torrentInfoData == null || torrentInfoData.length == 0) {
            LOG.warn("Incomplete conditions to create HandpickedTorrentDownloadDialog.");
            return;
        }

        try {
            LOG.info("createHandpickedTorrentDownloadDialog!");
            final HandpickedTorrentDownloadDialog dlg =
                    HandpickedTorrentDownloadDialog.newInstance(
                            contextRef.get(),
                            TorrentInfo.bdecode(torrentInfoData),
                            magnetUri,
                            torrentFetcherDownloadTokenId);
            dlg.show(fragmentManagerRef.get());
        } catch (Throwable t) {
            LOG.warn("Could not create or show HandpickedTorrentDownloadDialog", t);
        }
    }
}
