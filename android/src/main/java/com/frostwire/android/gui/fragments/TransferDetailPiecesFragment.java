/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.fragments;

import android.view.View;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.HexHiveView;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.PiecePriorities;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.util.Logger;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class TransferDetailPiecesFragment extends AbstractTransferDetailFragment {
  private static final Logger LOG = Logger.getLogger(TransferDetailPiecesFragment.class);
  private TextView piecesNumberTextView;
  private TextView pieceSizeTextView;
  private HexHiveView<PieceIndexBitfield> hexHiveView;
  private PieceAdapter hexDataAdapter;
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
    //        progressBar = findView(rootView,
    // R.id.fragment_transfer_detail_pieces_indeterminate_progress_bar);
    hexHiveView = findView(rootView, R.id.fragment_transfer_detail_pieces_hexhive_view);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void updateComponents() {
    if (uiBittorrentDownload == null) {
      return;
    }

    ensureTorrentHandleAsync();
    final String infoHash = uiBittorrentDownload.getInfoHash();
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> loadPiecesData(infoHash));
  }

  private void loadPiecesData(String expectedInfoHash) {
    SystemUtils.ensureBackgroundThreadOrCrash("TransferDetailPiecesFragment::loadPiecesData");
    if (uiBittorrentDownload == null
        || !expectedInfoHash.equals(uiBittorrentDownload.getInfoHash())) {
      return;
    }

    final TorrentHandle currentTorrentHandle =
        torrentHandle != null ? torrentHandle : uiBittorrentDownload.getDl().getTorrentHandle();
    if (currentTorrentHandle == null) {
      return;
    }

    final TorrentStatus status;
    final TorrentInfo torrentInfo;
    final byte[] priorityMarks;
    try {
      status = currentTorrentHandle.status(TorrentHandle.QUERY_PIECES);
      torrentInfo = currentTorrentHandle.torrentFile();
      PieceIndexBitfield downloaded = status != null ? status.pieces() : null;
      // a downloaded piece is drawn full whatever its priority
      priorityMarks =
          downloaded == null || downloaded.isAllSet()
              ? null
              : readPriorityMarks(currentTorrentHandle);
    } catch (Throwable t) {
      LOG.warn("Skipping pieces update because torrent status is unavailable", t);
      return;
    }

    final PieceIndexBitfield pieces = status != null ? status.pieces() : null;
    final long piecesCount = pieces != null ? pieces.count() : -1;
    final int totalPiecesValue = torrentInfo != null ? torrentInfo.numPieces() : totalPieces;
    final String pieceSizeValue =
        torrentInfo != null
            ? UIUtils.getBytesInHuman(torrentInfo.pieceSizeForReq(0))
            : pieceSizeString;

    SystemUtils.postToUIThread(
        () ->
            applyPiecesData(
                expectedInfoHash,
                pieces,
                priorityMarks,
                piecesCount,
                totalPiecesValue,
                pieceSizeValue));
  }

  /** The hive mark of each piece by its priority, see {@link PiecePriorities#mark}. */
  private static byte[] readPriorityMarks(TorrentHandle handle) {
    Priority[] priorities = handle.piecePriorities();
    byte[] marks = new byte[priorities.length];
    for (int i = 0; i < priorities.length; i++) {
      marks[i] = (byte) PiecePriorities.mark(priorities[i].swig());
    }
    return marks;
  }

  @SuppressWarnings("unchecked")
  private void applyPiecesData(
      String expectedInfoHash,
      PieceIndexBitfield pieces,
      byte[] priorityMarks,
      long piecesCount,
      int totalPiecesValue,
      String pieceSizeValue) {
    if (!isAdded()
        || uiBittorrentDownload == null
        || !expectedInfoHash.equals(uiBittorrentDownload.getInfoHash())) {
      return;
    }

    if (pieceSizeString == null && pieceSizeValue != null) {
      pieceSizeString = pieceSizeValue;
    }

    if (totalPieces == -1 && totalPiecesValue >= 0) {
      totalPieces = totalPiecesValue;
    }

    if (pieceSizeTextView != null && pieceSizeString != null) {
      pieceSizeTextView.setText(pieceSizeString);
    }
    if (piecesNumberTextView != null && totalPieces >= 0) {
      piecesNumberTextView.setText(String.valueOf(totalPieces));
    }
    if (hexHiveView != null && totalPieces >= 0) {
      hexHiveView.setVisibility(View.VISIBLE);
    }

    if (pieces == null || totalPieces < 0) {
      return;
    }

    if (hexDataAdapter == null) {
      hexDataAdapter = new PieceAdapter(totalPieces, pieces);
    } else if (piecesCount >= 0) {
      hexDataAdapter.updateData(pieces);
    }
    hexDataAdapter.setPriorityMarks(priorityMarks);

    if (hexHiveView != null && hexDataAdapter != null) {
      hexHiveView.updateData(hexDataAdapter);
    }
    if (piecesNumberTextView != null && piecesCount >= 0) {
      piecesNumberTextView.setText(piecesCount + "/" + totalPieces);
    }
  }

  static final class PieceAdapter implements HexHiveView.HexDataAdapter<PieceIndexBitfield> {
    private PieceIndexBitfield pieceIndexBitfield;
    private final int totalPieces;
    private int downloadedPieces;

    /** Read by the render thread, null when every piece is downloaded. */
    private volatile byte[] priorityMarks;

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

    void setPriorityMarks(byte[] marks) {
      priorityMarks = marks;
    }

    @Override
    public int priorityMark(int hexOffset) {
      byte[] marks = priorityMarks;
      return marks != null && hexOffset >= 0 && hexOffset < marks.length
          ? marks[hexOffset]
          : PiecePriorities.MARK_NONE;
    }
  }
}
