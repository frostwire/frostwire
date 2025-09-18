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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.components.transfers.TransferDetailFiles;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class TransferDetailFilesActionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private final static ImageIcon play_solid;
    private final static AlphaIcon play_transparent;
    private final static ImageIcon share_solid;
    private final static AlphaIcon share_faded;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, 0.1f);
        share_solid = GUIMediator.getThemeImage("transfers_sharing_over");
        share_faded = new AlphaIcon(share_solid, 0.1f);
    }

    private final JLabel playButton;
    private final JLabel shareButton;
    private TransferDetailFiles.TransferItemHolder transferItemHolder;

    public TransferDetailFilesActionsRenderer() {
        setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        setLayout(new MigLayout("gap 2px, fillx, center, insets 5px 5px 5px 5px", "[20px!][20px!]"));
        playButton = new JLabel(play_transparent);
        playButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onPlay();
                }
            }
        });
        shareButton = new JLabel(share_faded);
        shareButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onShare();
                }
            }
        });
        add(playButton, "width 20px!, growx 0, aligny top, push");
        add(shareButton, "width 20px!, growx 0, aligny top, push");
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        cancelEdit(); // Gubatron: This is what solved the flickering/empty cell bug when the first column cells were clicked on. -Dec 28th 2020
        if (!(dataHolder instanceof TransferDetailFiles.TransferItemHolder)) {
            if (transferItemHolder != null) {
                updateButtons();
            }
            return;
        }
        transferItemHolder = (TransferDetailFiles.TransferItemHolder) dataHolder;
        updateButtons();
    }

    private void onPlay() {
        if (!transferItemHolder.transferItem.isComplete()) {
            return;
        }
        new PlayAction(transferItemHolder).actionPerformed(null);
    }

    private void onShare() {
        if (!transferItemHolder.transferItem.isComplete()) {
            return;
        }
        File file = transferItemHolder.transferItem.getFile();
        if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
            new Thread(() -> {
                TorrentUtil.makeTorrentAndDownload(file, null, true);
                GUIMediator.safeInvokeLater(() -> BTDownloadMediator.instance().updateTableFilters());
            }).start();
        }
    }

    private void updateButtons() {
        if (transferItemHolder == null) {
            return;
        }
        playButton.setIcon(transferItemHolder.transferItem.isComplete() ? play_solid : play_transparent);
        shareButton.setIcon(transferItemHolder.transferItem.isComplete() ? share_solid : share_faded);
        playButton.invalidate();
        shareButton.invalidate();
    }

    public final static class OpenInFolderAction extends AbstractAction {
        private final TransferDetailFiles.TransferItemHolder transferItemHolder;

        public OpenInFolderAction(TransferDetailFiles.TransferItemHolder itemHolder) {
            transferItemHolder = itemHolder;
            putValue(Action.NAME, I18n.tr("Explore"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Explore"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Open Folder Containing the File"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        public void actionPerformed(ActionEvent ae) {
            File selectedFile = transferItemHolder.transferItem.getFile();
            if (selectedFile.isFile() && selectedFile.getParentFile() != null) {
                GUIMediator.launchExplorer(selectedFile);
            }
        }
    }

    public final static class PlayAction extends AbstractAction {
        private final TransferDetailFiles.TransferItemHolder transferItemHolder;

        public PlayAction(TransferDetailFiles.TransferItemHolder itemHolder) {
            transferItemHolder = itemHolder;
            putValue(Action.NAME, I18n.tr("Play"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Play"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = transferItemHolder.transferItem.getFile();
            if (MediaPlayer.isPlayableFile(file)) {
                MediaPlayer.instance().asyncLoadMedia(new MediaSource(file), false, false);
            } else {
                GUIMediator.launchFile(file);
            }
        }
    }
}
