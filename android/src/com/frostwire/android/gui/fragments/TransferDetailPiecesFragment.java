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

import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.util.Logger;

import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailPiecesFragment extends AbstractTransferDetailFragment {
    private TextView piecesNumberTextView;
    private TextView pieceSizeTextView;
    private RecyclerView recyclerView;
    private PieceAdapter adapter;
    private int totalPieces;

    public TransferDetailPiecesFragment() {
        super(R.layout.fragment_transfer_detail_pieces);
        totalPieces = -1;
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        super.initComponents(v, savedInstanceState);
        piecesNumberTextView = findView(v, R.id.fragment_transfer_detail_pieces_pieces_number);
        pieceSizeTextView = findView(v, R.id.fragment_transfer_detail_pieces_piece_size_number);
        recyclerView = findView(v, R.id.fragment_transfer_detail_pieces_recycler_view);
    }

    @Override
    public void onResume() {
        if (uiBittorrentDownload == null) {
            return;
        }
        TorrentHandle torrentHandle = uiBittorrentDownload.getDl().getTorrentHandle();
        if (torrentHandle == null) {
            return;
        }
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        TorrentStatus status = torrentHandle.status();
        if (status == null) {
            return;
        }

        TorrentInfo torrentInfo = torrentHandle.torrentFile();

        if (adapter == null && isAdded()) {
            pieceSizeTextView.setText(UIUtils.getBytesInHuman(torrentInfo.pieceSize(0)));
            adapter = new PieceAdapter(torrentInfo.numPieces(), status.pieces());
            recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 16));
            recyclerView.setAdapter(adapter);
        }

        if (totalPieces == -1) {
            totalPieces = torrentInfo.numPieces(); // can't rely on this one, let's use the one on the torrent info
            piecesNumberTextView.setText(totalPieces + "");
        }

        subscription = TimerService.subscribe(this, 5);
        super.onResume();
    }

    @Override
    public void onTime() {
        super.onTime();
        if (!isVisible() || uiBittorrentDownload == null) {
            return;
        }
        TorrentStatus status = torrentHandle.status();
        if (status == null) {
            return;
        }
        PieceIndexBitfield pieces = status.pieces();
        piecesNumberTextView.setText(pieces.count() + "/" + totalPieces);
        if (adapter != null) {
            adapter.updateData(pieces);
        }
    }

    // making this abstraction instead of using 'Boolean'
    // in case we want to extend functionality to show things
    // like exact piece size, piece hash, piece availability (before it's downloaded)
    // This tab could be a lot more rich when the app evolves for Android Desktop/TV
    private static final class Piece {
        int offset;
        boolean downloaded;

        Piece(int offset, boolean downloaded) {
            this.offset = offset;
            this.downloaded = downloaded;
        }
    }

    private static final class PieceViewHolder extends RecyclerView.ViewHolder {
        private Piece piece;
        private static final int downloadedColor = R.color.basic_blue_highlight;
        private static final int notDownloadedColor = R.color.basic_gray_dark;
        private final ImageView circleView;

        public PieceViewHolder(View itemView) {
            super(itemView);
            circleView = itemView.findViewById(R.id.view_transfer_detail_piece_image_view);
        }

        public void updatePiece(Piece piece) {
            if (this.piece == null) {
                this.piece = piece;
            } else {
                this.piece.downloaded = piece.downloaded;
                this.piece.offset = piece.offset;
            }
            if (circleView == null) {
                return;
            }
            circleView.setColorFilter(piece.downloaded ? downloadedColor : notDownloadedColor);
        }
    }

    private static final class PieceAdapter extends RecyclerView.Adapter<PieceViewHolder> {
        private static final Logger LOG = Logger.getLogger(PieceAdapter.class);
        private final int totalPieces;
        private PieceIndexBitfield pieces;

        PieceAdapter(int totalPieces, PieceIndexBitfield pieces) {
            this.totalPieces = totalPieces;
            this.pieces = pieces;
        }

        @Override
        public PieceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PieceViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_detail_piece, null));
        }

        @Override
        public void onBindViewHolder(PieceViewHolder holder, int position) {
            LOG.info("onBindViewHolder(" + holder + "," + position + ")");
            if (pieces != null) {
                holder.updatePiece(new Piece(position, new Random(System.currentTimeMillis()).nextInt() % 2 == 0));
            }
        }

        @Override
        public int getItemCount() {
            return totalPieces;
        }

        public void updateData(PieceIndexBitfield pieces) {
            this.pieces = pieces;
            notifyDataSetChanged();
        }
    }
}
