/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.OffMainQuery;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Hex;

/** User-confirmed positive endorsement for the verified peer whose result seeded successfully. */
public final class EndorseSourcePeerMenuAction extends MenuAction {
  private final BittorrentDownload download;

  public EndorseSourcePeerMenuAction(Context context, BittorrentDownload download) {
    super(context, R.drawable.contextmenu_icon_copy, R.string.endorse_source_peer, 0);
    this.download = download;
  }

  /**
   * Whether the transfer menu offers this action. Safe on the main thread: reads only the
   * transfer's cached state and its in-memory source attribution. Tapping the item runs the full
   * {@link #canEndorse} check in the background.
   */
  public static boolean mayOffer(BittorrentDownload download) {
    if (!(download instanceof UIBittorrentDownload)
        || download.getState() != TransferState.SEEDING
        || !download.isComplete()) {
      return false;
    }
    com.frostwire.bittorrent.BTDownload bt = ((UIBittorrentDownload) download).getDl();
    return bt != null
        && bt.getDistributedSourcePeerPub() != null
        && SearchEngine.DISTRIBUTED_WIRING.karmaChainWriter() != null;
  }

  /**
   * Whether the source peer can be endorsed now. Reads the v1 info hash from libtorrent and the
   * karma chain: never on the main thread.
   */
  public static boolean canEndorse(BittorrentDownload download) {
    return SourceEndorsement.of(download) != null;
  }

  @Override
  public void onClick(Context context) {
    if (!mayOffer(download)) {
      return;
    }
    OffMainQuery.<SourceEndorsement>on(SystemUtils.HandlerThreadName.HIGH_PRIORITY)
        .run(
            () -> canEndorse(download) ? SourceEndorsement.of(download) : null,
            endorsement -> confirm(context, endorsement));
  }

  private void confirm(Context context, SourceEndorsement endorsement) {
    if (context instanceof Activity && ((Activity) context).isFinishing()) {
      return;
    }
    if (endorsement == null) {
      UIUtils.showShortMessage(context, R.string.endorse_source_peer_unavailable);
      return;
    }
    String peerHex = Hex.encode(endorsement.sourcePeer);
    new AlertDialog.Builder(context)
        .setTitle(R.string.endorse_source_peer)
        .setMessage(
            context.getString(
                R.string.endorse_source_peer_confirmation,
                download.getDisplayName(),
                peerHex.substring(0, 16)))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(
            R.string.endorse_source_peer,
            (dialog, which) ->
                SystemUtils.postToHandler(
                    SystemUtils.HandlerThreadName.HIGH_PRIORITY,
                    () -> {
                      boolean endorsed = endorsement.write();
                      SystemUtils.postToUIThread(
                          () ->
                              UIUtils.showShortMessage(
                                  context,
                                  endorsed
                                      ? R.string.endorse_source_peer_success
                                      : R.string.endorse_source_peer_unavailable));
                    }))
        .show();
  }

  /** The source, info hash and writer that passed the check, so the endorsement uses the same. */
  private static final class SourceEndorsement {
    final KarmaChainWriter writer;
    final byte[] sourcePeer;
    final byte[] infoHash;

    private SourceEndorsement(KarmaChainWriter writer, byte[] sourcePeer, byte[] infoHash) {
      this.writer = writer;
      this.sourcePeer = sourcePeer;
      this.infoHash = infoHash;
    }

    /** Null unless the transfer's source can be endorsed now. Not on the main thread. */
    static SourceEndorsement of(BittorrentDownload download) {
      if (!mayOffer(download)) {
        return null;
      }
      com.frostwire.bittorrent.BTDownload bt = ((UIBittorrentDownload) download).getDl();
      byte[] peer = bt.getDistributedSourcePeerPub();
      String infoHashHex = bt.getV1InfoHash();
      KarmaChainWriter writer = SearchEngine.DISTRIBUTED_WIRING.karmaChainWriter();
      if (peer == null || infoHashHex == null || writer == null) {
        return null;
      }
      byte[] infoHash = Hex.decode(infoHashHex);
      return writer.canEndorseSourcePeer(peer, infoHash)
          ? new SourceEndorsement(writer, peer, infoHash)
          : null;
    }

    /** A writer revoked since the check (identity replaced, relay stopped) refuses to write. */
    boolean write() {
      return writer.endorseSourcePeer(sourcePeer, infoHash);
    }
  }
}
