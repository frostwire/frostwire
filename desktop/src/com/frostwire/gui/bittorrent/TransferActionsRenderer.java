/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
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

    private static final float BUTTONS_TRANSPARENCY = 0.85f;
    private static final ImageIcon play_solid;
    private static final AlphaIcon play_transparent;
    private static final ImageIcon share_solid;
    private static final AlphaIcon share_transparent;


    private JLabel labelPlay;
    private JLabel labelShare;
    private boolean showSolid;
    private BTDownload dl;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
        share_solid = GUIMediator.getThemeImage("transfers_sharing_over");
        share_transparent = new AlphaIcon(share_solid, BUTTONS_TRANSPARENCY);
    }

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
        labelShare = new JLabel(share_solid);
        labelShare.setToolTipText(I18n.tr("Share the download url/magnet of this seeding transfer"));
        labelShare.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onShareTransfer();
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
        add(labelPlay,c);

        setEnabled(true);
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        updateUIData((TransferHolder) dataHolder, table, row, column);
    }

    private void updateUIData(TransferHolder actionsHolder, JTable table, int row, int column) {
        dl = actionsHolder.getDl();
        showSolid = mouseIsOverRow(table, row);
        updatePlayButton();
        labelPlay.setVisible(dl.canPreview());
        boolean canShareNow = dl instanceof BittorrentDownload || dl.isCompleted();
        labelShare.setVisible(canShareNow);
    }

    private void updatePlayButton() {
        labelPlay.setIcon((isDlBeingPlayed()) ? GUIMediator.getThemeImage("speaker") : (showSolid) ? play_solid : play_transparent);
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

    private void onShareTransfer() {
        boolean canShareNow = dl instanceof BittorrentDownload || dl.isCompleted();
        if (!canShareNow) {
            return;
        }

        if (dl instanceof BittorrentDownload) {
          if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(new BTDownload[] { dl })) {
              new ShareTorrentDialog(((BittorrentDownload) dl).getTorrentInfo()).setVisible(true);
          }
        } else if (dl instanceof SoundcloudDownload ||
                   dl instanceof HttpDownload) {
            if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
                TorrentUtil.makeTorrentAndDownload(dl.getSaveLocation(), null, true);
                dl.setDeleteDataWhenRemove(false);
                GUIMediator.instance().getBTDownloadMediator().remove(dl);
            }
        } else if (dl instanceof YouTubeDownload) {
          // TODO: normalize file, remove transfer, get rid of unnormalized file, then make torrent with normalized file.
          if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
              TorrentUtil.makeTorrentAndDownload(dl.getSaveLocation(), null, true);
              dl.setDeleteDataWhenRemove(false);
              GUIMediator.instance().getBTDownloadMediator().remove(dl);
          }
        }
    }

    private void seedTransfer() {
        if (dl instanceof BittorrentDownload) {
            TorrentUtil.askForPermissionToSeedAndSeedDownloads(new BTDownload[] { dl });
        }
    }

    private boolean isDlBeingPlayed() {
        File file = dl.getPreviewFile();
        return file != null && MediaPlayer.instance().isThisBeingPlayed(dl.getPreviewFile());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        updatePlayButton();
    }
}
