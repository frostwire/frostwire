/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import com.frostwire.transfers.TransferState;
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
public final class TransferSeedingRenderer extends FWAbstractJPanelTableCellRenderer {
    private static final ImageIcon seed_solid;
    private static final AlphaIcon seed_faded;
    private static final ImageIcon loading;

    static {
        seed_solid = GUIMediator.getThemeImage("transfers_seeding_over");
        seed_faded = new AlphaIcon(seed_solid, 0.5f);
        loading = GUIMediator.getThemeImage("indeterminate_small_progress");
    }

    private JLabel labelSeed;
    private BTDownload dl;

    public TransferSeedingRenderer() {
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
        labelSeed = new JLabel(seed_faded);
        labelSeed.setOpaque(false);
        labelSeed.setDoubleBuffered(true);
        labelSeed.setToolTipText(I18n.tr("SEED this torrent transfer so others can download it. The more seeds, the faster the downloads."));
        labelSeed.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (dl.getState().equals(TransferState.DOWNLOADING)) {
                        return;
                    }
                    BittorrentDownload.RendererHelper.onSeedTransfer(dl, false);
                    labelSeed.setIcon(loading);
                }
            }
        });
        add(labelSeed, c);
        setEnabled(true);
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        updateUIData((SeedingHolder) dataHolder);
    }

    private void updateUIData(SeedingHolder actionsHolder) {
        if (actionsHolder != null) {
            dl = actionsHolder.getDl();
            boolean canShareNow = BittorrentDownload.RendererHelper.canShareNow(dl);
            updateSeedingButton(canShareNow);
        }
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
        } else {
            labelSeed.setIcon(seed_faded);
        }
    }
}
