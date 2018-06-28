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
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

public final class TransferDetailPieces extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private HexHivePanel HEXHIVEPANEL;
    private HexHivePanel.HexDataAdapter hexPanelAdapter;

    TransferDetailPieces() {
        super();
        HEXHIVEPANEL = new HexHivePanel(0x264053, 0xf2f2f2, 0x33b5e5,0xf2f2f2, 0, 0, 0, 0);
        setLayout(new MigLayout("fillx, insets 0 0 0 0"));
        add(HEXHIVEPANEL);
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        HEXHIVEPANEL.updateData(hexPanelAdapter);
    }

    private static class HexHiveAdapter implements HexHivePanel.HexDataAdapter<BittorrentDownload> {
        private BittorrentDownload bittorrentDownload;
        @Override
        public void updateData(BittorrentDownload data) {
            /*
            bittorrentDownload = data;
            TorrentStatus status = torrentHandle.status(TorrentHandle.QUERY_PIECES);
            TorrentInfo torrentInfo = torrentHandle.torrentFile();

            if (pieceSizeString == null) {
                pieceSizeString = UIUtils.getBytesInHuman(torrentInfo.pieceSize(0));
            }

            if (totalPieces == -1) {
                totalPieces = torrentInfo.numPieces();
                piecesNumberTextView.setText(String.valueOf(totalPieces));
                //progressBar.setVisibility(View.VISIBLE);
                hexHiveView.setVisibility(View.GONE);
            }

            PieceIndexBitfield pieces = status.pieces();
            long piecesCount = pieces.count();
            if (isAdded()) {
                // I do this color look-up only once and pass it down to the view holder
                // otherwise it has to be done thousands of times.
                pieceSizeTextView.setText(pieceSizeString);
                hexDataAdapter = new PieceAdapter(totalPieces, pieces);
                hexHiveView.setVisibility(View.VISIBLE);
            }
            if (hexDataAdapter != null) {
                if (piecesCount >= 0) {
                    hexDataAdapter.updateData(pieces);
                }
                //noinspection unchecked
                hexHiveView.updateData(hexDataAdapter);
                piecesNumberTextView.setText(piecesCount + "/" + totalPieces);
            }
            */
        }

        @Override
        public int getTotalHexagonsCount() {
            if (bittorrentDownload == null) {
                return 0;
            }
            //bittorrentDownload.getDl().
            return 0;
        }

        @Override
        public int getFullHexagonsCount() {
            if (bittorrentDownload == null) {
                return 0;
            }
            return 0;
        }

        @Override
        public boolean isFull(int hexOffset) {
            return false;
        }
    }
}