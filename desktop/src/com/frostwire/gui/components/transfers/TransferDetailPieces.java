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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

public final class TransferDetailPieces extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailPieces.class);

    private final JLabel pieceSizeLabel;
    private final JLabel totalPiecesLabel;
    private final HexHivePanel hexHivePanel;
    private HexHiveAdapter hexHivePanelAdapter;
    private boolean pieceSizeAlreadySet = false;
    private boolean totalPiecesAlreadySet = false;
    private BittorrentDownload bittorrentDownload;

    TransferDetailPieces() {
        super();
        setLayout(new MigLayout("fill, insets 0 0 0 0, debug"));
        hexHivePanel = new HexHivePanel(
                0x264053,
                0xf2f2f2,
                0x33b5e5,
                0xf2f2f2,
                0,
                0,
                0,
                0);
        hexHivePanelAdapter = new HexHiveAdapter();

        pieceSizeLabel = new JLabel("<html><b>" + I18n.tr("Piece Size") + "</b>:</html>");
        totalPiecesLabel = new JLabel("<html><b>" + I18n.tr("Total Pieces") + "</b>:</html>");
        add(totalPiecesLabel, "left");
        add(pieceSizeLabel, "left, wrap");
        add(hexHivePanel, "push, grow, span 2");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        pieceSizeAlreadySet = (bittorrentDownload == btDownload);
        totalPiecesAlreadySet = (bittorrentDownload == btDownload);
        bittorrentDownload = btDownload;

        hexHivePanelAdapter.updateData(bittorrentDownload);

        updatePieceSizeLabel(hexHivePanelAdapter.getPieceSizeInHuman());
        updateTotalPiecesLabel(hexHivePanelAdapter.getFullHexagonsCount() + "/" + hexHivePanelAdapter.getTotalHexagonsCount());

        if (hexHivePanelAdapter.getTotalHexagonsCount() >= 0) {
            hexHivePanel.updateData(hexHivePanelAdapter);
            hexHivePanel.invalidate();
        }
        invalidate();
    }

    private void updatePieceSizeLabel(String pieceSize) {
        if (!pieceSizeAlreadySet) {
            pieceSizeLabel.setText("<html><b>" + I18n.tr("Piece Size") + "</b>:" + pieceSize + "</html>");
            pieceSizeAlreadySet = true;
        }
    }

    private void updateTotalPiecesLabel(String totalPieces) {
        if (!totalPiecesAlreadySet) {
            totalPiecesLabel.setText("<html><b>" + I18n.tr("Total Pieces") + "</b>:" + totalPieces + "</html>");
            totalPiecesAlreadySet = true;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    private final static class HexHiveAdapter implements HexHivePanel.HexDataAdapter<BittorrentDownload> {
        private BittorrentDownload bittorrentDownload;
        private int totalPieces;
        private int numFullPieces;
        private PieceIndexBitfield pieces;
        private String pieceSizeInHuman;

        @Override
        public void updateData(BittorrentDownload btDownload) {
            bittorrentDownload = btDownload;
            TorrentHandle torrentHandle = bittorrentDownload.getDl().getTorrentHandle();
            TorrentStatus status = torrentHandle.status(TorrentHandle.QUERY_PIECES);
            TorrentInfo torrentInfo = torrentHandle.torrentFile();
            pieceSizeInHuman = GUIUtils.getBytesInHuman(torrentInfo.pieceSize(0));
            totalPieces = torrentInfo.numPieces();
            pieces = status.pieces();
            if (pieces.isAllSet()) {
                numFullPieces = totalPieces;
            } else if (pieces.isNoneSet() || pieces.isEmpty()) {
                numFullPieces = 0;
            } else {
                numFullPieces = 0;
                for(int i=0; i < pieces.count(); i++) {
                    if (pieces.getBit(i)) {
                        numFullPieces++;
                    }
                }
            }
            LOG.info("HexHiveAdapter.updateData() totalPieces =" + totalPieces);
            LOG.info("HexHiveAdapter.updateData() pieces.count() =" + pieces.count());
            LOG.info("HexHiveAdapter.updateData() numFullPieces =" + numFullPieces);
        }

        @Override
        public int getTotalHexagonsCount() {
            if (bittorrentDownload == null) {
                return 0;
            }
            return totalPieces;
        }

        @Override
        public int getFullHexagonsCount() {
            if (bittorrentDownload == null) {
                return 0;
            }
            return numFullPieces;
        }

        @Override
        public boolean isFull(int hexOffset) {
            return pieces.getBit(hexOffset);
        }

        String getPieceSizeInHuman() {
            return pieceSizeInHuman;
        }
    }
}