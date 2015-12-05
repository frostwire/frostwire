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
    private static final ImageIcon seed_solid;
    private static final AlphaIcon seed_faded;
    private static final ImageIcon loading;
    private JLabel labelPlay;
    private JLabel labelShare;
    private JLabel labelSeed;
    private BTDownload dl;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, 0.1f);
        share_solid = GUIMediator.getThemeImage("transfers_sharing_over");
        share_faded = new AlphaIcon(share_solid, 0.1f);
        seed_solid = GUIMediator.getThemeImage("transfers_seeding_over");
        seed_faded = new AlphaIcon(seed_solid, 0.5f);
        loading = GUIMediator.getThemeImage("indeterminate_small_progress");
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

        c.insets = new Insets(2,5,2,5);

        labelShare = new JLabel(share_solid);
        labelShare.setToolTipText(I18n.tr("SHARE the download url/magnet of this seeding transfer"));
        labelShare.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (dl.getState().equals(TransferState.DOWNLOADING)) {
                        return;
                    }

                    onSeedTransfer(true);
                }
            }
        });
        add(labelShare, c);

        labelSeed = new JLabel(seed_faded);
        labelSeed.setToolTipText(I18n.tr("SEED this torrent transfer so others can download it. The more seeds, the faster the downloads."));
        labelSeed.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (dl.getState().equals(TransferState.DOWNLOADING)) {
                        return;
                    }

                    onSeedTransfer(false);
                    labelSeed.setIcon(loading);
                }
            }
        });
        add(labelSeed, c);

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
        boolean canShareNow = canShareNow();
        updatePlayButton();
        updateSeedingButton(canShareNow);
        labelShare.setIcon(canShareNow ? share_solid : share_faded); //updateSeedingButton :)
    }

    private boolean canShareNow() {
        return (dl instanceof BittorrentDownload && dl.isCompleted()) || dl.isCompleted();
    }

    private void updatePlayButton() {
        final boolean playable = dl.canPreview();
        labelPlay.setIcon((isDlBeingPlayed()) ? GUIMediator.getThemeImage("speaker") : (playable) ? play_solid : play_transparent);
    }

    private void updateSeedingButton(boolean canShareNow) {
        labelSeed.setVisible(true);
        if (dl instanceof BittorrentDownload) {
            final TransferState currentState = dl.getState();
            boolean isSeeding = currentState.equals(TransferState.SEEDING) && dl.isCompleted();
            boolean isPausing = currentState.equals(TransferState.PAUSING);
            if (!canShareNow) {
                labelSeed.setIcon(seed_faded);
            } else {
                labelSeed.setIcon(isPausing ? loading : (isSeeding ? seed_solid : seed_faded));
            }
        }
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

    private void onSeedTransfer(boolean showShareTorrentDialog) {
        boolean canShareNow = canShareNow();
        if (!canShareNow) {
            System.out.println("Not doing anything.");
            return;
        }

        if (dl instanceof BittorrentDownload &&
            dl.getState().equals(TransferState.SEEDING) &&
            !showShareTorrentDialog) {
            dl.pause();
            // sorry Dijkstra.
            return;
        }

        if (dl instanceof BittorrentDownload &&
            TorrentUtil.askForPermissionToSeedAndSeedDownloads(new BTDownload[] { dl }) &&
            showShareTorrentDialog) {
                new ShareTorrentDialog(((BittorrentDownload) dl).getTorrentInfo()).setVisible(true);
        } else if (dl instanceof SoundcloudDownload || dl instanceof YouTubeDownload || dl instanceof HttpDownload) {
            if (TorrentUtil.askForPermissionToSeedAndSeedDownloads(null)) {
                TorrentUtil.makeTorrentAndDownload(dl.getSaveLocation(), null, showShareTorrentDialog);
                dl.setDeleteDataWhenRemove(false);
                GUIMediator.instance().getBTDownloadMediator().remove(dl);
            }
        }
        // revise this if we decide to do file normalization, meanwhile, will handle these downloads in above case.
        /* else if (dl instanceof YouTubeDownload) {
            // TODO: normalize file, remove transfer, get rid of unnormalized file, then make torrent with normalized file.
        } */
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
