/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.components.transfers.TransferDetailFiles;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
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

    private JLabel playButton;
    private JLabel shareButton;
    private TransferDetailFiles.TransferItemHolder transferItemHolder;

    public TransferDetailFilesActionsRenderer() {
        setLayout(new MigLayout("insets 2px 18px 0 0", "align center"));
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
        add(playButton, "center");
        add(shareButton, "center");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        updateButtons();
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
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
        File file = transferItemHolder.transferItem.getFile();
        if (MediaPlayer.isPlayableFile(file)) {
            MediaPlayer.instance().asyncLoadMedia(new MediaSource(file), false, false);
        } else {
            GUIMediator.launchFile(file);
        }
    }

    private void onShare() {
        if (!transferItemHolder.transferItem.isComplete()) {
            return;
        }
        File file = transferItemHolder.transferItem.getFile();
        if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
            new Thread(() -> {
                TorrentUtil.makeTorrentAndDownload(file, null, true);
                GUIMediator.safeInvokeLater(() -> {
                    BTDownloadMediator.instance().updateTableFilters();
                });
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
}
