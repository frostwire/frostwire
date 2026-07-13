/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.limegroup.gnutella.settings.SearchEnginesSettings;

/**
 * Desktop share visibility: by default only torrents that are still in the
 * transfer table with full metadata (active seed or in-swarm download).
 * When {@link SearchEnginesSettings#LOCAL_SEARCH_INCLUDE_INACTIVE} is true,
 * all LocalIndex rows are eligible (historical shares).
 */
public final class BtTransferShareVisibility implements ShareVisibilityPolicy {

    public static final BtTransferShareVisibility INSTANCE = new BtTransferShareVisibility();

    private BtTransferShareVisibility() {
    }

    @Override
    public boolean isVisible(String infoHashHex) {
        if (SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue()) {
            return true;
        }
        if (infoHashHex == null || infoHashHex.isEmpty()) {
            return false;
        }
        try {
            BTDownload dl = TransferAdapter.findDownload(infoHashHex);
            if (dl == null) {
                return false;
            }
            // Must have full .torrent / info-dict in session.
            TorrentHandle th = dl.getTorrentHandle();
            if (th == null || !th.isValid()) {
                return false;
            }
            TorrentInfo ti = th.torrentFile();
            if (ti == null) {
                return false;
            }
            // Actively seeding, or still an in-progress swarm member.
            return dl.isSeeding() || !dl.isFinished();
        } catch (Throwable t) {
            return false;
        }
    }
}
