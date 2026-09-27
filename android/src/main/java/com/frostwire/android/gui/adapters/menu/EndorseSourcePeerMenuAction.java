/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
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

  public static boolean canEndorse(BittorrentDownload download) {
    if (!(download instanceof UIBittorrentDownload)
        || download.getState() != TransferState.SEEDING
        || !download.isComplete()) {
      return false;
    }
    UIBittorrentDownload ui = (UIBittorrentDownload) download;
    com.frostwire.bittorrent.BTDownload bt = ui.getDl();
    byte[] peer = bt == null ? null : bt.getDistributedSourcePeerPub();
    String infoHash = bt == null ? null : bt.getV1InfoHash();
    KarmaChainWriter writer = SearchEngine.DISTRIBUTED_WIRING.karmaChainWriter();
    return peer != null
        && infoHash != null
        && writer != null
        && writer.canEndorseSourcePeer(peer, Hex.decode(infoHash));
  }

  @Override
  public void onClick(Context context) {
    if (!canEndorse(download) || !(download instanceof UIBittorrentDownload)) {
      return;
    }
    com.frostwire.bittorrent.BTDownload bt = ((UIBittorrentDownload) download).getDl();
    byte[] sourcePeer = bt.getDistributedSourcePeerPub();
    byte[] infoHash = Hex.decode(bt.getV1InfoHash());
    KarmaChainWriter writer = SearchEngine.DISTRIBUTED_WIRING.karmaChainWriter();
    if (sourcePeer == null
        || writer == null
        || !writer.canEndorseSourcePeer(sourcePeer, infoHash)) {
      return;
    }
    String peerHex = Hex.encode(sourcePeer);
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
                new Thread(
                        () -> {
                          boolean endorsed = writer.endorseSourcePeer(sourcePeer, infoHash);
                          SystemUtils.postToUIThread(
                              () ->
                                  UIUtils.showShortMessage(
                                      context,
                                      endorsed
                                          ? R.string.endorse_source_peer_success
                                          : R.string.endorse_source_peer_unavailable));
                        },
                        "EndorseSourcePeer")
                    .start())
        .show();
  }
}
