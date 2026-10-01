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
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexAnnouncementPublisher;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Public shares must still be active, non-private transfers with full metadata. Local history
 * preferences never authorize publication to peers.
 */
public final class BtTransferShareVisibility implements ShareVisibilityPolicy {

  public static final BtTransferShareVisibility INSTANCE =
      new BtTransferShareVisibility(
          SearchEnginesSettings.ICEBRIDGE_ENABLED::getValue,
          SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG::getValue,
          BtTransferShareVisibility::transferStatus);
  public static final ShareVisibilityPolicy CATALOG = INSTANCE::isCatalogVisible;
  public static final ShareVisibilityPolicy LOCAL =
      infoHashHex ->
          SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue()
              || isActiveTransfer(transferStatus(infoHashHex));

  private final BooleanSupplier enabled;
  private final BooleanSupplier publicCatalog;
  private final Function<String, TransferStatus> transfers;

  BtTransferShareVisibility(
      BooleanSupplier enabled,
      BooleanSupplier publicCatalog,
      Function<String, TransferStatus> transfers) {
    this.enabled = enabled;
    this.publicCatalog = publicCatalog;
    this.transfers = transfers;
  }

  /** Shared by startup and behavioral tests; consent is read again on every publication. */
  public IndexAnnouncementPublisher createCatalogPublisher(
      LocalIndex index, IdentityKeys identity) {
    return new IndexAnnouncementPublisher(index, identity, this::isCatalogVisible);
  }

  @Override
  public boolean isVisible(String infoHashHex) {
    return isVisible(infoHashHex, false);
  }

  boolean isCatalogVisible(String infoHashHex) {
    return isVisible(infoHashHex, true);
  }

  private boolean isVisible(String infoHashHex, boolean catalog) {
    try {
      if (infoHashHex == null
          || infoHashHex.isEmpty()
          || !enabled.getAsBoolean()
          || (catalog && !publicCatalog.getAsBoolean())) {
        return false;
      }
      TransferStatus status = transfers.apply(infoHashHex);
      return isActiveTransfer(status)
          && (!catalog || status.state() == TransferState.SEEDING)
          && enabled.getAsBoolean()
          && (!catalog || publicCatalog.getAsBoolean());
    } catch (Throwable unavailable) {
      return false;
    }
  }

  private static boolean isActiveTransfer(TransferStatus status) {
    return status != null
        && !status.paused()
        && status.metadataValid()
        && !status.privateTorrent()
        && (status.state() == TransferState.SEEDING || status.state() == TransferState.DOWNLOADING);
  }

  record TransferStatus(
      TransferState state, boolean paused, boolean metadataValid, boolean privateTorrent) {}

  private static TransferStatus transferStatus(String infoHashHex) {
    if (infoHashHex == null || infoHashHex.isEmpty()) {
      return null;
    }
    try {
      BTDownload dl = TransferAdapter.findDownload(infoHashHex);
      if (dl == null) {
        return null;
      }
      // Must have full .torrent / info-dict in session.
      TorrentHandle th = dl.getTorrentHandle();
      if (th == null || !th.isValid()) {
        return null;
      }
      TorrentInfo ti = th.torrentFile();
      if (ti == null) {
        return null;
      }
      TransferState state = dl.getState();
      TransferStatus status =
          new TransferStatus(state, dl.isPaused(), ti.isValid(), ti.isPrivate());
      return TransferAdapter.findDownload(infoHashHex) == dl ? status : null;
    } catch (Throwable t) {
      return null;
    }
  }
}
