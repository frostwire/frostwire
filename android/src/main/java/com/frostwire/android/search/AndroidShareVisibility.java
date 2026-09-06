/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.transfers.BittorrentDownload;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Public authorization, independent of local history/search preferences. Call off main (JNI). */
public final class AndroidShareVisibility implements ShareVisibilityPolicy {
    private final BooleanSupplier participating;
    private final Predicate<String> shared;

    public AndroidShareVisibility(BooleanSupplier participating) {
        this(participating, hash -> isLiveTransfer(hash, null));
    }

    AndroidShareVisibility(BooleanSupplier participating, Predicate<String> shared) {
        this.participating = participating;
        this.shared = shared;
    }

    @Override
    public boolean isVisible(String hash) {
        try {
            return hash != null && participating.getAsBoolean()
                    && shared.test(hash.toLowerCase(Locale.ROOT)) && participating.getAsBoolean();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    static boolean isLiveTransfer(String hash, BTDownload expected) {
        return isLiveTransfer(hash, expected, TransferManager.instance()::getBittorrentDownload);
    }

    static boolean isLiveTransfer(String hash, BTDownload expected,
                                  Function<String, BittorrentDownload> lookup) {
        BittorrentDownload transfer = lookup.apply(hash);
        if (!(transfer instanceof UIBittorrentDownload)) {
            return false;
        }
        UIBittorrentDownload ui = (UIBittorrentDownload) transfer;
        BTDownload download = ui.getDl();
        if (download == null
                || ui.isRemovedFromSharing()
                || ui.isSharingPaused()
                || (expected != null && download != expected)
                || download.wasPaused()
                || download.isPaused()) {
            return false;
        }
        TorrentHandle handle = download.getTorrentHandle();
        if (handle == null || !handle.isValid()) {
            return false;
        }
        TorrentInfo info = handle.torrentFile();
        return info != null && info.isValid() && !info.isPrivate()
                && !ui.isRemovedFromSharing()
                && !ui.isSharingPaused() && !download.wasPaused() && !download.isPaused()
                && lookup.apply(hash) == ui;
    }
}
