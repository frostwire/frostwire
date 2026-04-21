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
import com.frostwire.gui.bittorrent.TransferDetailFilesActionsRenderer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;
import com.frostwire.util.SafeText;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.BittorrentSettings;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class TransferDetailFiles extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailFiles.class);
    private final TransferDetailFilesTableMediator tableMediator;
    private final JCheckBox showSkippedCheckbox;
    private BittorrentDownload btDownload;
    private volatile boolean forceRefresh = false;

    TransferDetailFiles() {
        tableMediator = new TransferDetailFilesTableMediator();
        showSkippedCheckbox = new JCheckBox(I18n.tr("Show skipped files"));
        showSkippedCheckbox.setSelected(BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue());
        showSkippedCheckbox.setVisible(false);
        showSkippedCheckbox.addItemListener(e -> {
            BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.setValue(showSkippedCheckbox.isSelected());
            forceRefresh = true;
            if (btDownload != null) {
                updateData(btDownload);
            }
        });

        Runnable refreshCallback = () -> {
            if (btDownload != null) {
                forceRefresh = true;
                updateData(btDownload);
            }
        };
        TransferDetailFilesActionsRenderer.setOnDownloadStartedCallback(refreshCallback);
        tableMediator.setOnPriorityChangedCallback(refreshCallback);

        setLayout(new MigLayout("fillx, insets 0 0 0 0, wrap 1"));
        add(showSkippedCheckbox, "growx, gapleft 5, gaptop 5");
        add(tableMediator.getComponent(), "gap 0 0 0 0, growx, growy");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload != null && btDownload.getDl() != null) {
            final boolean isPartial = btDownload.getDl().isPartial();
            final boolean showSkipped = BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue();
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    List<TransferItem> items = btDownload.getDl().getItems();
                    if (items != null && !items.isEmpty()) {
                        // Build holders here (background thread) so JNI calls in
                        // TransferItemHolder constructor (isComplete/getProgress/isSkipped) never touch the EDT.
                        List<TransferItemHolder> holders = new ArrayList<>(items.size());
                        int i = 0;
                        for (TransferItem item : items) {
                            if (!item.isSkipped() || showSkipped) {
                                holders.add(new TransferItemHolder(i++, item));
                            }
                        }
                        SwingUtilities.invokeLater(() -> {
                            try {
                                showSkippedCheckbox.setVisible(isPartial);
                                boolean shouldClear = this.btDownload != btDownload || forceRefresh;
                                forceRefresh = false;
                                if (shouldClear) {
                                    this.btDownload = btDownload;
                                    // Only set checkbox state when switching to a new transfer.
                                    // The user manages the checkbox state; refreshing the table
                                    // should not fight with the user's click.
                                    if (!isPartial) {
                                        showSkippedCheckbox.setSelected(false);
                                    } else {
                                        showSkippedCheckbox.setSelected(showSkipped);
                                    }
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
        public final boolean skipped;
        public final int priority;
        final String displayName;
        final String fileType;

        TransferItemHolder(int fileOffset, TransferItem transferItem) {
            this.fileOffset = fileOffset;
            this.transferItem = transferItem;
            // isComplete() / getProgress() / isSkipped() / getPriority() call JNI — safe here because
            // TransferItemHolder is always constructed inside BackgroundQueuedExecutorService.
            this.complete = transferItem.isComplete();
            this.progress = this.complete ? 100 : transferItem.getProgress();
            this.skipped = transferItem.isSkipped();
            this.priority = transferItem.getPriority();
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
