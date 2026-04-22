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
import java.util.Collections;
import java.util.List;

public final class TransferDetailFiles extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailFiles.class);
    private final TransferDetailFilesTableMediator tableMediator;
    private final JCheckBox showSkippedCheckbox;
    private BittorrentDownload btDownload;
    /**
     * Cached full holder list (includes skipped files) for the current torrent.
     * Rebuilt from JNI only when the transfer data changes (new pieces, priority changes).
     * Checkbox clicks merely filter this cached list on the EDT — zero JNI, instant response.
     */
    private List<TransferItemHolder> allHolders = Collections.emptyList();
    private boolean cachedIsPartial = false;

    TransferDetailFiles() {
        tableMediator = new TransferDetailFilesTableMediator();
        showSkippedCheckbox = new JCheckBox(I18n.tr("Show skipped files"));
        showSkippedCheckbox.setSelected(BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue());
        showSkippedCheckbox.setVisible(false);
        showSkippedCheckbox.addItemListener(e -> {
            BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.setValue(showSkippedCheckbox.isSelected());
            // Checkbox click = instant filter of cached holders. No background thread, no JNI.
            applyFilterOnEdt();
        });

        Runnable refreshCallback = () -> {
            if (btDownload != null) {
                rebuildHolders(btDownload);
            }
        };
        TransferDetailFilesActionsRenderer.setOnDownloadStartedCallback(refreshCallback);
        tableMediator.setOnPriorityChangedCallback(refreshCallback);

        setLayout(new MigLayout("fillx, insets 0 0 0 0, wrap 1"));
        add(showSkippedCheckbox, "growx, gapleft 5, gaptop 5");
        add(tableMediator.getComponent(), "gap 0 0 0 0, growx, growy");
    }

    /**
     * Filters the cached {@link #allHolders} based on the current "Show skipped files"
     * setting and updates the table — entirely on the EDT, zero JNI calls.
     */
    private void applyFilterOnEdt() {
        boolean showSkipped = BittorrentSettings.SHOW_SKIPPED_FILES_IN_TRANSFER_DETAIL.getValue();
        showSkippedCheckbox.setVisible(cachedIsPartial);

        if (allHolders.isEmpty()) {
            tableMediator.setHolders(Collections.emptyList());
            return;
        }

        int visibleCount = 0;
        for (TransferItemHolder h : allHolders) {
            if (!h.skipped || showSkipped) {
                visibleCount++;
            }
        }
        List<TransferItemHolder> visible = new ArrayList<>(visibleCount);
        for (TransferItemHolder h : allHolders) {
            if (!h.skipped || showSkipped) {
                visible.add(h);
            }
        }
        tableMediator.setHolders(visible);
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload == null || btDownload.getDl() == null) {
            return;
        }
        // If this is the same torrent and we already have cached holders, the
        // refresh loop is just telling us to repaint with current data. The
        // cached holders are already up-to-date (or will be refreshed by the
        // priority-changed / download-started callbacks). Just re-apply filter.
        if (this.btDownload == btDownload && !allHolders.isEmpty()) {
            SwingUtilities.invokeLater(this::applyFilterOnEdt);
            return;
        }
        // New torrent or first view — rebuild from JNI in background.
        rebuildHolders(btDownload);
    }

    private void rebuildHolders(BittorrentDownload target) {
        Runnable task = () -> {
            try {
                // All JNI calls happen here, off the EDT.
                boolean newIsPartial = target.getDl().isPartial();
                List<TransferItem> items = target.getDl().getItems();
                List<TransferItemHolder> holders = Collections.emptyList();
                if (items != null && !items.isEmpty()) {
                    holders = new ArrayList<>(items.size());
                    int i = 0;
                    for (TransferItem item : items) {
                        holders.add(new TransferItemHolder(i++, item));
                    }
                }
                final boolean finalIsPartial = newIsPartial;
                final List<TransferItemHolder> finalHolders = holders;
                SwingUtilities.invokeLater(() -> {
                    // Guard against race: user may have selected a different
                    // torrent while we were building holders.
                    if (target != btDownload && btDownload != null) {
                        return;
                    }
                    this.btDownload = target;
                    this.cachedIsPartial = finalIsPartial;
                    this.allHolders = finalHolders;
                    showSkippedCheckbox.setVisible(finalIsPartial);
                    applyFilterOnEdt();
                });
            } catch (Throwable e) {
                LOG.error("Error rebuilding transfer file holders", e);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            // Spawns a dedicated thread (Android HIGH_PRIORITY pattern) so the
            // task starts immediately and never waits behind queued background work.
            new Thread(task, "TransferDetailFiles-Rebuild-" + System.currentTimeMillis()).start();
        } else {
            // Called from TransferDetailComponent's background refresh loop.
            task.run();
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
            // TransferItemHolder is always constructed inside a background thread.
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
