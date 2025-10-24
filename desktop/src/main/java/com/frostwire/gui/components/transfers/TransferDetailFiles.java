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
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.List;

public final class TransferDetailFiles extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailFiles.class);
    private final TransferDetailFilesTableMediator tableMediator;
    private BittorrentDownload btDownload;

    TransferDetailFiles() {
        tableMediator = new TransferDetailFilesTableMediator();
        setLayout(new MigLayout("fillx, insets 0 0 0 0"));
        add(tableMediator.getComponent(), "gap 0 0 0 0, growx, growy");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload != null && btDownload.getDl() != null) {
            // Move heavy I/O work to background thread to avoid EDT violations
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    List<TransferItem> items = btDownload.getDl().getItems();
                    if (items != null && items.size() > 0) {
                        // Update UI on EDT
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (this.btDownload != btDownload) {
                                    this.btDownload = btDownload;
                                    tableMediator.clearTable();
                                    int i = 0;
                                    for (TransferItem item : items) {
                                        if (!item.isSkipped()) {
                                            tableMediator.add(new TransferItemHolder(i++, item));
                                        }
                                    }
                                } else {
                                    int i = 0;
                                    for (TransferItem item : items) {
                                        if (!item.isSkipped()) {
                                            tableMediator.update(new TransferItemHolder(i++, item));
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                LOG.error("Error updating transfer files details UI", e);
                            }
                        });
                    }
                } catch (Throwable e) {
                    LOG.error("Error fetching transfer files details", e);
                }
            });
        }
    }

    /**
     * A Transfer item does not know its own position within the torrent item list,
     * this class keeps that number and a reference to the transfer item.
     * <p>
     * Also, this is necessary to update the table, since tableMediator.update() doesn't work with plain TransferItems
     */
    public static class TransferItemHolder {
        public final TransferItem transferItem;
        final int fileOffset;

        TransferItemHolder(int fileOffset, TransferItem transferItem) {
            this.fileOffset = fileOffset;
            this.transferItem = transferItem;
        }

        @Override
        public int hashCode() {
            return fileOffset;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TransferItemHolder && ((TransferItemHolder) obj).fileOffset == fileOffset;
        }
    }
}
