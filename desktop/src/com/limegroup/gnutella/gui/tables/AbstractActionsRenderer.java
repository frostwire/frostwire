/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.frostwire.uxstats.UXAction.LIBRARY_SHARE_FROM_ROW_ACTION;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class AbstractActionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private final static float BUTTONS_TRANSPARENCY = 0.85f;
    private final static ImageIcon play_solid;
    private final static AlphaIcon play_transparent;
    private final static ImageIcon download_solid;
    private final static AlphaIcon download_transparent;
    private static final ImageIcon share_solid;

    private JLabel labelPlay;
    private JLabel labelDownload;
    private JLabel labelShare;
    private boolean showSolid;
    protected AbstractActionsHolder actionsHolder;
    protected AbstractActionsHolder lastClickedActionsHolder;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
        download_solid = GUIMediator.getThemeImage("search_result_download_over");
        download_transparent = new AlphaIcon(download_solid, BUTTONS_TRANSPARENCY);
        share_solid = GUIMediator.getThemeImage("transfers_sharing_over");
    }

    public AbstractActionsRenderer() {
        setupUI();
    }

    protected abstract void onPlayAction();

    protected abstract void onDownloadAction();

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        actionsHolder = (AbstractActionsHolder) dataHolder;

        if (table.getSelectedRowCount() == 1 && row == table.getSelectedRow()) {
            lastClickedActionsHolder = actionsHolder;
        }

        showSolid = mouseIsOverRow(table, row);
        updatePlayButton();

        labelPlay.setVisible(actionsHolder.isPlayable());
        labelDownload.setIcon(showSolid ? download_solid : download_transparent);
        labelDownload.setVisible(actionsHolder.isDownloadable());
    }

    private void setupUI() {
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

        labelShare = new JLabel(share_solid);
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
            if (lastClickedActionsHolder==null ||
                BTDownloadMediator.instance().isActiveTorrentDownload(lastClickedActionsHolder.getFile())) {
                return;
            }

            if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
                TorrentUtil.makeTorrentAndDownload(lastClickedActionsHolder.getFile(), null, true);
                final BTDownload dl = BTDownloadMediator.instance().findBTDownload(lastClickedActionsHolder.getFile());
                dl.setDeleteDataWhenRemove(false);
            }

            UXStats.instance().log(LIBRARY_SHARE_FROM_ROW_ACTION);
        }
    }

    private void updatePlayButton() {
        cancelEdit();
        labelPlay.setIcon((actionsHolder.isPlaying()) ? GUIMediator.getThemeImage("speaker") : (showSolid) ? play_solid : play_transparent);
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