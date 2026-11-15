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

import android.graphics.Point;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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

  /** The tapped piece's details and priority choices, over the hive. */
  private PieceDetailsPanel piecePanel;

  /** The tapped piece, -1 for none; set on the main thread, read by the background refresh. */
  private volatile int selectedPiece = -1;

  /** A piece's priority is set on a background thread, never on the main one. */
  private final PiecePriorities piecePriorities =
      new PiecePriorities(
          task -> SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, task),
          SystemUtils::postToUIThread);

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
    piecePanel =
        new PieceDetailsPanel(
            findView(rootView, R.id.view_transfer_detail_piece_panel),
            this::onPriorityChosen,
            this::clearSelectedPiece);
    hexHiveView.setOnPieceTapListener(this::onPieceTapped);
    // a new view (the tab came back) has no piece selected
    selectedPiece = -1;
  }

  @Override
  protected void onTransferChanged() {
    // another transfer's pieces, the tapped one was of the previous transfer
    clearSelectedPiece();
  }

  /** Shows the tapped piece's details once they're read off the main thread. */
  private void onPieceTapped(int piece) {
    selectedPiece = piece;
    if (piecePanel == null) {
      return;
    }
    if (piece < 0 || uiBittorrentDownload == null) {
      piecePanel.hide();
      return;
    }
    if (piecePanel.shownPiece() != piece) {
      // no other piece's details while the tapped one's are read
      piecePanel.hide();
    }
    placePiecePanel(piece);
    final String infoHash = uiBittorrentDownload.getInfoHash();
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.HIGH_PRIORITY,
        () -> {
          PieceDetails details = readPieceDetails(infoHash, piece);
          if (details != null) {
            SystemUtils.postToUIThread(() -> showPieceDetails(details));
          }
        });
  }

  /** The panel goes to the half of the hive the piece isn't in, so the piece stays in sight. */
  private void placePiecePanel(int piece) {
    Point center = new Point();
    View panel = piecePanel.getView();
    if (!hexHiveView.pieceCenter(piece, center)
        || !(panel.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
      return;
    }
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
    int gravity = center.y > hexHiveView.getHeight() / 2 ? Gravity.TOP : Gravity.BOTTOM;
    if (params.gravity != gravity) {
      params.gravity = gravity;
      panel.setLayoutParams(params);
    }
  }

  private void clearSelectedPiece() {
    selectedPiece = -1;
    if (hexHiveView != null) {
      hexHiveView.setSelectedPiece(-1);
    }
    if (piecePanel != null) {
      piecePanel.hide();
    }
  }

  /** Sets the piece's priority off the main thread, then refreshes the hive and the panel. */
  private void onPriorityChosen(PieceDetails details, Priority priority) {
    TorrentHandle handle = torrentHandle;
    if (handle == null
        || uiBittorrentDownload == null
        || !details.infoHash.equals(uiBittorrentDownload.getInfoHash())) {
      return;
    }
    piecePriorities.apply(
        PiecePriorities.of(handle),
        details.piece,
        priority,
        () -> loadPiecesData(details.infoHash));
  }

  /** Background thread only, null if the piece can't be read now. */
  private PieceDetails readPieceDetails(String expectedInfoHash, int piece) {
    SystemUtils.ensureBackgroundThreadOrCrash("TransferDetailPiecesFragment::readPieceDetails");
    if (uiBittorrentDownload == null
        || !expectedInfoHash.equals(uiBittorrentDownload.getInfoHash())) {
      return null;
    }
    TorrentHandle handle =
        torrentHandle != null ? torrentHandle : uiBittorrentDownload.getDl().getTorrentHandle();
    try {
      return handle != null
          ? PieceDetails.read(expectedInfoHash, handle, handle.torrentFile(), piece)
          : null;
    } catch (Throwable t) {
      LOG.warn("Could not read the details of piece " + piece, t);
      return null;
    }
  }

  private void showPieceDetails(PieceDetails details) {
    // the tab closed, the transfer changed or another piece was tapped meanwhile
    if (!isAdded()
        || piecePanel == null
        || uiBittorrentDownload == null
        || !details.infoHash.equals(uiBittorrentDownload.getInfoHash())
        || details.piece != selectedPiece) {
      return;
    }
    piecePanel.show(details);
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
    final PieceDetails selectedPieceDetails;
    try {
      status = currentTorrentHandle.status(TorrentHandle.QUERY_PIECES);
      torrentInfo = currentTorrentHandle.torrentFile();
      PieceIndexBitfield downloaded = status != null ? status.pieces() : null;
      // a downloaded piece is drawn full whatever its priority
      priorityMarks =
          downloaded == null || downloaded.isAllSet()
              ? null
              : readPriorityMarks(currentTorrentHandle);
      // the tapped piece's panel follows it: missing, downloading, downloaded
      int piece = selectedPiece;
      selectedPieceDetails =
          piece >= 0
              ? PieceDetails.read(expectedInfoHash, currentTorrentHandle, torrentInfo, piece)
              : null;
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
        () -> {
          applyPiecesData(
              expectedInfoHash,
              pieces,
              priorityMarks,
              piecesCount,
              totalPiecesValue,
              pieceSizeValue);
          if (selectedPieceDetails != null) {
            showPieceDetails(selectedPieceDetails);
          }
        });
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
