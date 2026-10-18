/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.util.Logger;
import com.frostwire.util.SafeText;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;

public final class TransferDetailPeers extends JPanel
    implements TransferDetailComponent.TransferDetailPanel {
  private static final Logger LOG = Logger.getLogger(TransferDetailPeers.class);
  private TransferDetailPeersTableMediator tableMediator;
  private BittorrentDownload btDownload;

  TransferDetailPeers() {
    super(new MigLayout("fillx, insets 0 0 0 0, gap 0 0"));
    // Defer table mediator creation to avoid EDT violation
    // JTable initialization triggers expensive UI updates (>2 second EDT block)
    SwingUtilities.invokeLater(
        () -> {
          tableMediator = new TransferDetailPeersTableMediator();
          add(tableMediator.getComponent(), "growx, growy");
          revalidate();
          repaint();
        });
  }

  @Override
  public void updateData(BittorrentDownload btDownload) {
    if (tableMediator == null || btDownload == null || btDownload.getDl() == null) {
      return;
    }
    this.btDownload = btDownload;
    try {
      // Gather JNI data off EDT
      List<PeerInfo> items = btDownload.getDl().getTorrentHandle().peerInfo();
      // Copy data to plain holders off EDT; an empty list must still reach the table so peers
      // that disconnected are removed.
      List<PeerItemHolder> holders = new ArrayList<>();
      if (items != null) {
        for (PeerInfo item : items) {
          holders.add(new PeerItemHolder(item));
        }
      }

      // Update table on EDT
      SwingUtilities.invokeLater(
          () -> {
            try {
              tableMediator.syncPeers(holders);
            } catch (Throwable e) {
              LOG.error("Error updating peers table UI: " + e.getMessage());
            }
          });
    } catch (Throwable e) {
      LOG.error("Error updating data: " + e.getMessage());
    }
  }

  public class PeerItemHolder {
    /** Stable identity across refreshes: the peer's endpoint, not its position in the list. */
    final String key;

    final PeerInfo peerItem;
    final String address;
    final String client;
    final String flags;
    final String source;
    final String progress;

    PeerItemHolder(PeerInfo peerItem) {
      this.key = peerItem.ip();
      this.peerItem = peerItem;
      this.address = SafeText.sanitize(TransferDetailPeersDataLine.getBittorrentAddress(peerItem));
      String clientName = peerItem.client();
      this.client =
          SafeText.sanitize((clientName == null || clientName.isEmpty()) ? "Unknown" : clientName);
      this.flags =
          SafeText.sanitize(
              TransferDetailPeersDataLine.getFlagsAsString(peerItem.flags(), peerItem.source()));
      this.source =
          SafeText.sanitize(TransferDetailPeersDataLine.getSourceAsString(peerItem.source()));
      this.progress = SafeText.sanitize((100 * peerItem.progress()) + "%");
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof PeerItemHolder other && other.key.equals(key);
    }
  }
}
