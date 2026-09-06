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
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.settings.SearchEnginesSettings;

/**
 * Public shares must still be active, non-private transfers with full metadata. Local history
 * preferences never authorize publication to peers.
 */
public final class BtTransferShareVisibility implements ShareVisibilityPolicy {

  public static final BtTransferShareVisibility INSTANCE = new BtTransferShareVisibility();
  public static final ShareVisibilityPolicy LOCAL =
      infoHashHex ->
          SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue()
              || isActiveTransfer(infoHashHex);

  private BtTransferShareVisibility() {}

  @Override
  public boolean isVisible(String infoHashHex) {
    return SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue()
        && SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.getValue()
        && isActiveTransfer(infoHashHex);
  }

  private static boolean isActiveTransfer(String infoHashHex) {
    if (infoHashHex == null || infoHashHex.isEmpty()) {
      return false;
    }
    try {
      BTDownload dl = TransferAdapter.findDownload(infoHashHex);
      if (dl == null || dl.isPaused()) {
        return false;
      }
      // Must have full .torrent / info-dict in session.
      TorrentHandle th = dl.getTorrentHandle();
      if (th == null || !th.isValid()) {
        return false;
      }
      TorrentInfo ti = th.torrentFile();
      if (ti == null || ti.isPrivate()) {
        return false;
      }
      TransferState state = dl.getState();
      return (state == TransferState.SEEDING || state == TransferState.DOWNLOADING)
          && TransferAdapter.findDownload(infoHashHex) == dl;
    } catch (Throwable t) {
      return false;
    }
  }
}
