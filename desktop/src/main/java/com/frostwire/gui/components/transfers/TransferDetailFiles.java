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
        if (btDownload == null || btDownload.getDl() == null) {
            return;
        }
        final boolean isPartial = btDownload.getDl().isPartial();
        final boolean showSkipped = BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue();

        Runnable backgroundTask = () -> {
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
                            // If the setting changed while we were building holders,
                            // a newer update (e.g. a subsequent checkbox click) is in
                            // flight and will refresh the UI. Skip this stale frame.
                            if (BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue() != showSkipped) {
                                return;
                            }
                            showSkippedCheckbox.setVisible(isPartial);
                            boolean switchingTorrent = this.btDownload != btDownload;
                            boolean shouldClear = switchingTorrent || forceRefresh;
                            forceRefresh = false;
                            if (shouldClear) {
                                this.btDownload = btDownload;
                                // Only sync checkbox state when switching to a new transfer.
                                // The user manages the checkbox state; refreshing the table
                                // should not fight with the user's click.
                                // Hiding/showing the checkbox must not change the saved state.
                                if (isPartial && switchingTorrent) {
                                    showSkippedCheckbox.setSelected(showSkipped);
                                }
                                tableMediator.clearTable();
                                for (TransferItemHolder holder : holders) {
                                    tableMediator.add(holder);
                                }
                            } else {
                                // TransferItemHolder has immutable pre-computed values (progress, skipped, priority).
                                // tableMediator.update(holder) only fires a repaint but cannot change the data
                                // because TransferDetailFilesDataLine.update() is empty and the holder reference
                                // in the data line is never replaced. Always clear+rebuild to show fresh data.
                                tableMediator.clearTable();
                                for (TransferItemHolder holder : holders) {
                                    tableMediator.add(holder);
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
        };

        if (SwingUtilities.isEventDispatchThread()) {
            // Checkbox clicks and other EDT callbacks must not block the EDT.
            // Use the common fork-join pool instead of the shared single-threaded
            // BackgroundQueuedExecutorService so the UI stays responsive.
            java.util.concurrent.ForkJoinPool.commonPool().execute(backgroundTask);
        } else {
            // Called from TransferDetailComponent's background refresh loop;
            // already off-EDT, so run directly to avoid double-queueing.
            backgroundTask.run();
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
