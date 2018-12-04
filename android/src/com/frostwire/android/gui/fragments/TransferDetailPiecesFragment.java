/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.view.View;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.HexHiveView;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class TransferDetailPiecesFragment extends AbstractTransferDetailFragment {
    //private static Logger LOG = Logger.getLogger(TransferDetailPiecesFragment.class);
    private TextView piecesNumberTextView;
    private TextView pieceSizeTextView;
    private HexHiveView hexHiveView;
    private HexHiveView.HexDataAdapter<PieceIndexBitfield> hexDataAdapter;
//    private ProgressBar progressBar;
    private int totalPieces;
    private String pieceSizeString;

    public TransferDetailPiecesFragment() {
        super(R.layout.fragment_transfer_detail_pieces);
        totalPieces = -1;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (uiBittorrentDownload == null) {
            return;
        }
        if (torrentHandle == null) {
            return;
        }
        updateComponents();
    }

    @Override
    protected int getTabTitleStringId() {
        return R.string.pieces;
    }

    @Override
    public void ensureComponentsReferenced(View rootView) {
        piecesNumberTextView = findView(rootView, R.id.fragment_transfer_detail_pieces_pieces_number);
        pieceSizeTextView = findView(rootView, R.id.fragment_transfer_detail_pieces_piece_size_number);
//        progressBar = findView(rootView, R.id.fragment_transfer_detail_pieces_indeterminate_progress_bar);
        hexHiveView = findView(rootView, R.id.fragment_transfer_detail_pieces_hexhive_view);
    }

    @Override
    protected void updateComponents() {
        if (uiBittorrentDownload == null) {
            return;
        }
        ensureTorrentHandleAsync();
        if (torrentHandle == null) {
            return;
        }

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
    }

    private final static class PieceAdapter implements HexHiveView.HexDataAdapter<PieceIndexBitfield> {
        private PieceIndexBitfield pieceIndexBitfield;
        private final int totalPieces;
        private int downloadedPieces;

        public PieceAdapter(int totalPieces, PieceIndexBitfield pieces) {
            this.totalPieces = totalPieces;
            updateData(pieces);
        }

        @Override
        public void updateData(PieceIndexBitfield data) {
            pieceIndexBitfield = data;
            downloadedPieces = pieceIndexBitfield.count();
        }

        @Override
        public int getTotalHexagonsCount() {
            return totalPieces;
        }

        @Override
        public int getFullHexagonsCount() {
            return downloadedPieces;
        }

        @Override
        public boolean isFull(int hexOffset) {
            return totalPieces == downloadedPieces || pieceIndexBitfield.getBit(hexOffset);
        }
    }
}
