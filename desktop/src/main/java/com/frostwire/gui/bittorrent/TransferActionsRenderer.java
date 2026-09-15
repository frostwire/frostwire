/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.theme.IconRepainter;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.MediaSource;
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
    // Lazy-loaded icons to avoid EDT blocking during class loading
    private static ImageIcon play_solid;
    private static AlphaIcon play_transparent;
    private static ImageIcon share_solid;
    private static AlphaIcon share_faded;
    private static volatile boolean iconsLoaded = false;

    /**
     * Lazy load icons on first access to avoid EDT blocking during class loading
     */
    private static synchronized void ensureIconsLoaded() {
        if (iconsLoaded) {
            return;
        }
        try {
            play_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_play_over"));
            play_transparent = new AlphaIcon(play_solid, 0.1f);
            share_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("transfers_sharing_over"));
            share_faded = new AlphaIcon(share_solid, 0.1f);
            iconsLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JLabel labelPlay;
    private JLabel labelShare;
    private BTDownload dl;

    public TransferActionsRenderer() {
        setupUI();
    }

    private void setupUI() {
        ensureIconsLoaded(); // Ensure icons are loaded on first use
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
        // State is computed off the EDT by BTDownloadDataLine.update() and stored
        // per row, so this shared renderer never queries the transfer while
        // painting (and one row's state cannot leak into another's).
        labelShare.setIcon(actionsHolder.canShare() ? share_solid : share_faded);
        labelPlay.setIcon(actionsHolder.canPlay() ? play_solid : play_transparent);
    }

    private void onPlay() {
        if (dl != null && dl.canPreview()) {
            File file = dl.getPreviewFile();
            if (file != null) {
                GUIMediator.instance().launchMedia(new MediaSource(file));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Action state is applied in updateUIData() before painting.
    }
}
