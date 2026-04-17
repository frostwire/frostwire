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
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;
import com.frostwire.util.SafeText;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.ArrayList;
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
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    List<TransferItem> items = btDownload.getDl().getItems();
                    if (items != null && !items.isEmpty()) {
                        // Build holders here (background thread) so JNI calls in
                        // TransferItemHolder constructor (isComplete/getProgress) never touch the EDT.
                        List<TransferItemHolder> holders = new ArrayList<>(items.size());
                        int i = 0;
                        for (TransferItem item : items) {
                            if (!item.isSkipped()) {
                                holders.add(new TransferItemHolder(i++, item));
                            }
                        }
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (this.btDownload != btDownload) {
                                    this.btDownload = btDownload;
                                    tableMediator.clearTable();
                                    for (TransferItemHolder holder : holders) {
                                        tableMediator.add(holder);
                                    }
                                } else {
                                    for (TransferItemHolder holder : holders) {
                                        tableMediator.update(holder);
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
        // Pre-computed on the background thread so renderers never call JNI on the EDT.
        public final boolean complete;
        public final int progress;
        final String displayName;
        final String fileType;

        TransferItemHolder(int fileOffset, TransferItem transferItem) {
            this.fileOffset = fileOffset;
            this.transferItem = transferItem;
            // isComplete() / getProgress() call fileProgress() JNI — safe here because
            // TransferItemHolder is always constructed inside BackgroundQueuedExecutorService.
            this.complete = transferItem.isComplete();
            this.progress = this.complete ? 100 : transferItem.getProgress();
            String fileName = SafeText.sanitize(transferItem.getName());
            this.displayName = fileName;
            int extensionSeparator = fileName.lastIndexOf('.');
            this.fileType = extensionSeparator >= 0 && extensionSeparator + 1 < fileName.length()
                    ? fileName.substring(extensionSeparator + 1)
                    : "";
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
