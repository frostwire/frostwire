/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.theme.IconRepainter;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransferActionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private static final ImageIcon play_solid;
    private static final AlphaIcon play_transparent;
    private static final ImageIcon share_solid;
    private static final AlphaIcon share_faded;

    static {
        play_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_play_over"));
        play_transparent = new AlphaIcon(play_solid, 0.1f);
        share_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("transfers_sharing_over"));
        share_faded = new AlphaIcon(share_solid, 0.1f);
    }

    private JLabel labelPlay;
    private JLabel labelShare;
    private BTDownload dl;
    /**
     * Cache the last known state to avoid re-querying during paint operations.
     * This prevents expensive JNI calls that block the EDT during rendering.
     */
    private boolean lastKnownCanShare = false;
    private boolean lastKnownCanPlay = false;
    private long lastStateCheckTime = 0;
    private static final long STATE_CHECK_INTERVAL_MS = 200; // Re-check state every 200ms

    public TransferActionsRenderer() {
        setupUI();
    }

    private void setupUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints c;
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        c.insets = new Insets(2, 5, 2, 5);
        labelShare = new JLabel(share_solid);
        labelShare.setToolTipText(I18n.tr("SHARE the download url/magnet of this seeding transfer"));
        labelShare.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (dl.getState().equals(TransferState.DOWNLOADING)) {
                        return;
                    }
                    BittorrentDownload.RendererHelper.onSeedTransfer(dl, true);
                }
            }
        });
        add(labelShare, c);
        labelPlay = new JLabel(play_transparent);
        labelPlay.setToolTipText(I18n.tr("Play/Preview"));
        labelPlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onPlay();
                }
            }
        });
        add(labelPlay, c);
        setEnabled(true);
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        updateUIData((TransferHolder) dataHolder);
    }

    private void updateUIData(TransferHolder actionsHolder) {
        dl = actionsHolder.getDl();

        // Only re-check state periodically to avoid expensive JNI calls during paint
        long now = System.currentTimeMillis();
        if (now - lastStateCheckTime >= STATE_CHECK_INTERVAL_MS) {
            try {
                lastKnownCanShare = BittorrentDownload.RendererHelper.canShareNow(dl);
                lastKnownCanPlay = dl.canPreview();
                lastStateCheckTime = now;
            } catch (Exception e) {
                // If state check fails, fall back to last known state
                System.err.println("Error checking transfer state: " + e.getMessage());
            }
        }

        labelShare.setIcon(lastKnownCanShare ? share_solid : share_faded);
        updatePlayButton();
    }

    private void updatePlayButton() {
        // Use cached play state to avoid expensive checks during render
        labelPlay.setIcon((isDlBeingPlayed()) ? IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("speaker")) : (lastKnownCanPlay) ? play_solid : play_transparent);
    }

    private void onPlay() {
        if (dl.canPreview() && !isDlBeingPlayed()) {
            File file = dl.getPreviewFile();
            if (file != null) {
                GUIMediator.instance().launchMedia(new MediaSource(file), !dl.isCompleted());
            }
            updatePlayButton();
        }
    }

    private boolean isDlBeingPlayed() {
        File file = dl.getPreviewFile();
        return file != null && MediaPlayer.instance().isThisBeingPlayed(dl.getPreviewFile());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Don't call updatePlayButton() here as it may trigger expensive state checks during paint
        // The state is already updated in updateUIData() which is called before painting
    }
}
