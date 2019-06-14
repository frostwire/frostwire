/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.TorrentUtil;
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
    private final static ImageIcon play_solid;
    private final static AlphaIcon play_transparent;
    private final static ImageIcon download_solid;
    private final static AlphaIcon download_transparent;
    private static final ImageIcon share_solid;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
        download_solid = GUIMediator.getThemeImage("search_result_download_over");
        download_transparent = new AlphaIcon(download_solid, BUTTONS_TRANSPARENCY);
        share_solid = GUIMediator.getThemeImage("transfers_sharing_over");
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
        labelPlay.setIcon(actionsHolder.isPlaying() ? GUIMediator.getThemeImage("speaker") : (showSolid) ? play_solid : play_transparent);
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
