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

import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailPiecesFragment extends AbstractTransferDetailFragment {
    private TextView piecesNumberTextView;
    private TextView pieceSizeTextView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
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
        progressBar = findView(v, R.id.fragment_transfer_detail_pieces_indeterminate_progress_bar);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVisible()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (uiBittorrentDownload == null) {
            return;
        }
        if (torrentHandle == null) {
            return;
        }
        TorrentStatus status = torrentHandle.status(TorrentHandle.QUERY_PIECES);
        TorrentInfo torrentInfo = torrentHandle.torrentFile();
        if (adapter == null && isAdded()) {
            Resources r = getResources();
            // I do this color look-up only once and pass it down to the view holder
            // otherwise it has to be done thousands of times.
            pieceSizeTextView.setText(UIUtils.getBytesInHuman(torrentInfo.pieceSize(0)));
            adapter = new PieceAdapter(torrentInfo.numPieces(),
                    status.pieces(),
                    r.getColor(R.color.basic_blue_highlight),
                    r.getColor(R.color.basic_gray_dark));
            recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 16));
        }

        if (adapter != null) {
            recyclerView.setAdapter(adapter);
            if (status.pieces().count() > 0) {
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }

        if (totalPieces == -1) {
            totalPieces = torrentInfo.numPieces(); // can't rely on this one, let's use the one on the torrent info
            piecesNumberTextView.setText(totalPieces + "");
        }
        onTime();
    }

    @Override
    public void onTime() {
        super.onTime();
        if (!isVisible() || uiBittorrentDownload == null) {
            return;
        }

        TorrentStatus status = torrentHandle.status(TorrentHandle.QUERY_PIECES);
        PieceIndexBitfield pieces = status.pieces();
        piecesNumberTextView.setText(pieces.count() + "/" + totalPieces);

        if (adapter != null) {
            if (pieces.count() > 0) {
                adapter.updateData(pieces);
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
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
        private final ImageView circleView;
        private final int downloadedColor;
        private final int notDownloadedColor;

        public PieceViewHolder(View itemView, int downloadedColor, int notDownloadedColor) {
            super(itemView);
            circleView = itemView.findViewById(R.id.view_transfer_detail_piece_image_view);
            this.downloadedColor = downloadedColor;
            this.notDownloadedColor = notDownloadedColor;
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
            circleView.setColorFilter(piece.downloaded ? downloadedColor : notDownloadedColor, PorterDuff.Mode.SRC_IN);
            circleView.invalidate();
        }
    }

    private static final class PieceAdapter extends RecyclerView.Adapter<PieceViewHolder> {
        private final int totalPieces;
        private final int downloadedColor;
        private final int notDownloadedColor;
        private PieceIndexBitfield pieces;

        PieceAdapter(int totalPieces, PieceIndexBitfield pieces, int downloadedColor, int notDownloadedColor) {
            this.totalPieces = totalPieces;
            this.pieces = pieces;
            this.downloadedColor = downloadedColor;
            this.notDownloadedColor = notDownloadedColor;
        }

        @Override
        public PieceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PieceViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_detail_piece, null), downloadedColor, notDownloadedColor);
        }

        @Override
        public void onBindViewHolder(PieceViewHolder holder, int position) {
            if (pieces != null && pieces.count() > 0 && position <= pieces.endIndex() ) {
                holder.updatePiece(new Piece(position, pieces.getBit(position)));
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
