/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.List;

public final class TransferDetailPeers extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailPeers.class);
    private TransferDetailPeersTableMediator tableMediator;
    private BittorrentDownload btDownload;

    TransferDetailPeers() {
        super(new MigLayout("fillx, insets 0 0 0 0, gap 0 0"));
        // Defer table mediator creation to avoid EDT violation
        // JTable initialization triggers expensive UI updates (>2 second EDT block)
        SwingUtilities.invokeLater(() -> {
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
        if (this.btDownload != btDownload) {
            tableMediator.clearTable();
        }
        this.btDownload = btDownload;
        try {
            List<PeerInfo> items = btDownload.getDl().getTorrentHandle().peerInfo();
            if (items != null && items.size() > 0) {
                if (tableMediator.getSize() == 0) {
                    int i = 0;
                    for (PeerInfo item : items) {
                        tableMediator.add(new PeerItemHolder(i++, item));
                    }
                } else {
                    int i = 0;
                    for (PeerInfo item : items) {
                        try {
                            tableMediator.update(new PeerItemHolder(i++, item));
                        } catch (IndexOutOfBoundsException ignored) {
                            // peer might not be there anymore, reload table from scratch
                            tableMediator.clearTable();
                            updateData(btDownload);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("Error updating data: " + e.getMessage());
        }
    }

    public class PeerItemHolder {
        final int peerOffset;
        final PeerInfo peerItem;

        PeerItemHolder(int peerOffset, PeerInfo peerItem) {
            this.peerOffset = peerOffset;
            this.peerItem = peerItem;
        }

        @Override
        public int hashCode() {
            return peerOffset;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PeerItemHolder && ((PeerItemHolder) obj).peerOffset == peerOffset;
        }
    }
}
