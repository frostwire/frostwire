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

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.theme.IconRepainter;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractActionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private final static float BUTTONS_TRANSPARENCY = 0.85f;
    // Lazy-loaded icons to avoid EDT blocking during class loading
    private static ImageIcon play_solid;
    private static AlphaIcon play_transparent;
    private static ImageIcon download_solid;
    private static AlphaIcon download_transparent;
    private static ImageIcon share_solid;
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
            play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
            download_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_download_over"));
            download_transparent = new AlphaIcon(download_solid, BUTTONS_TRANSPARENCY);
            share_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("transfers_sharing_over"));
            iconsLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected AbstractActionsHolder actionsHolder;
    private JLabel labelPlay;
    private JLabel labelDownload;
    private boolean showSolid;

    protected AbstractActionsRenderer() {
        setupUI();
    }

    protected abstract void onPlayAction();

    protected abstract void onDownloadAction();

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        actionsHolder = (AbstractActionsHolder) dataHolder;
        showSolid = mouseIsOverRow(table, row);
        updatePlayButton();
        labelPlay.setVisible(actionsHolder.isPlayable());
        labelDownload.setIcon(showSolid ? download_solid : download_transparent);
        labelDownload.setVisible(actionsHolder.isDownloadable());
    }

    private void setupUI() {
        ensureIconsLoaded(); // Ensure icons are loaded on first use
        setLayout(new GridBagLayout());
        GridBagConstraints c;
        labelPlay = new JLabel(play_transparent);
        labelPlay.setToolTipText(I18n.tr("Play/Preview"));
        labelPlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelPlay_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelPlay, c);
        labelDownload = new JLabel(download_transparent);
        labelDownload.setToolTipText(I18n.tr("Download"));
        labelDownload.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelDownload_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelDownload, c);
        JLabel labelShare = new JLabel(share_solid);
        labelShare.setToolTipText(I18n.tr("SHARE the download-url or magnet-url of this file with a friend"));
        labelShare.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelShare_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelShare, c);
        setEnabled(true);
    }

    private void labelShare_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            // already being shared.
            if (actionsHolder == null ||
                    BTDownloadMediator.instance().isActiveTorrentDownload(actionsHolder.getFile())) {
                return;
            }
            if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
                TorrentUtil.makeTorrentAndDownload(actionsHolder.getFile(), null, true);
            }
        }
    }

    private void updatePlayButton() {
        cancelEdit();
        labelPlay.setIcon(actionsHolder.isPlaying() ? IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("speaker")) : (showSolid) ? play_solid : play_transparent);
    }

    private void labelPlay_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (actionsHolder.isPlayable() && !actionsHolder.isPlaying()) {
                onPlayAction();
                updatePlayButton();
            }
        }
    }

    private void labelDownload_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            onDownloadAction();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        updatePlayButton();
    }
}
