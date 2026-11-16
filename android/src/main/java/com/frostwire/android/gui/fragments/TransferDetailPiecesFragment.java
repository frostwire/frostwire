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
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
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

  /** The selected piece and its transfer, kept when the screen is recreated. */
  private static final String STATE_SELECTED_PIECE = "pieces_selected_piece";

  private static final String STATE_SELECTED_PIECE_INFOHASH = "pieces_selected_piece_infohash";

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

  /**
   * The piece a recreated screen had selected, -1 for none, and its transfer's info hash: it's
   * selected again once that transfer is bound, the transfer comes back after the view does.
   */
  private volatile int restoredPiece = -1;

  private volatile String restoredPieceInfoHash;

  /** Reads the pieces off the main thread: libtorrent, or a test's own. */
  private final PiecesReader piecesReader;

  /** A piece's priority is set on a background thread, never on the main one. */
  private final PiecePriorities piecePriorities =
      new PiecePriorities(
          task -> SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, task),
          SystemUtils::postToUIThread);

  public TransferDetailPiecesFragment() {
    this(null);
  }

  /** {@code reader} null reads the pieces from libtorrent. */
  TransferDetailPiecesFragment(@Nullable PiecesReader reader) {
    super(R.layout.fragment_transfer_detail_pieces);
    totalPieces = -1;
    piecesReader = reader != null ? reader : new LibtorrentPiecesReader();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    int piece = selectedPiece;
    String infoHash = uiBittorrentDownload != null ? uiBittorrentDownload.getInfoHash() : null;
    if (piece < 0 || infoHash == null) {
      // saved again before the restored selection's transfer came back
      piece = restoredPiece;
      infoHash = restoredPieceInfoHash;
    }
    // the card is open while a piece is selected: closing it clears the selection
    if (piece >= 0 && infoHash != null) {
      outState.putInt(STATE_SELECTED_PIECE, piece);
      outState.putString(STATE_SELECTED_PIECE_INFOHASH, infoHash);
    }
  }

  @Override
  public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);
    if (savedInstanceState == null) {
      return;
    }
    int piece = savedInstanceState.getInt(STATE_SELECTED_PIECE, -1);
    String infoHash = savedInstanceState.getString(STATE_SELECTED_PIECE_INFOHASH);
    if (piece >= 0 && infoHash != null) {
      restoredPiece = piece;
      restoredPieceInfoHash = infoHash;
      restoreSelectedPiece();
    }
  }

  /**
   * Selects the restored piece again once its transfer is bound, or drops it for another transfer.
   * The next refresh reads the piece off the main thread, then highlights it and opens its card, or
   * clears it if the transfer has no such piece.
   */
  private void restoreSelectedPiece() {
    String infoHash = restoredPieceInfoHash;
    UIBittorrentDownload download = uiBittorrentDownload;
    if (infoHash == null || download == null) {
      return;
    }
    int piece = restoredPiece;
    restoredPieceInfoHash = null;
    restoredPiece = -1;
    if (infoHash.equals(download.getInfoHash())) {
      selectedPiece = piece;
    }
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
    // a recreated screen's transfer is bound after its view is restored
    restoreSelectedPiece();
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
          PieceDetails details = piecesReader.readPieceDetails(infoHash, piece);
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

    restoreSelectedPiece();
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
    // the tapped piece's panel follows it: missing, downloading, downloaded
    final PiecesData data = piecesReader.readPieces(expectedInfoHash, selectedPiece);
    if (data == null) {
      return;
    }
    SystemUtils.postToUIThread(
        () -> {
          applyPiecesData(
              expectedInfoHash,
              data.pieces,
              data.priorityMarks,
              data.piecesCount,
              data.totalPieces >= 0 ? data.totalPieces : totalPieces,
              data.pieceSize != null ? data.pieceSize : pieceSizeString);
          showSelectedPiece(expectedInfoHash, data);
        });
  }

  /**
   * Highlights the selected piece and refreshes its card; a piece restored with the screen is
   * highlighted and its card opened here, once its details are read, or cleared if the transfer has
   * no such piece.
   */
  private void showSelectedPiece(String expectedInfoHash, PiecesData data) {
    int piece = data.selectedPiece;
    if (piece < 0
        || piece != selectedPiece
        || !isAdded()
        || uiBittorrentDownload == null
        || !expectedInfoHash.equals(uiBittorrentDownload.getInfoHash())) {
      return;
    }
    if (data.totalPieces >= 0 && piece >= data.totalPieces) {
      clearSelectedPiece();
      return;
    }
    if (data.selectedPieceDetails == null) {
      return;
    }
    if (hexHiveView != null && hexHiveView.getSelectedPiece() != piece) {
      hexHiveView.setSelectedPiece(piece);
      placePiecePanel(piece);
    }
    showPieceDetails(data.selectedPieceDetails);
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

  /** What one refresh read of a transfer's pieces, off the main thread. */
  static final class PiecesData {
    /** Null when libtorrent doesn't say. */
    final PieceIndexBitfield pieces;

    /** Null when every piece is downloaded, see {@link PiecePriorities#mark}. */
    final byte[] priorityMarks;

    /** Downloaded pieces, -1 when unknown. */
    final long piecesCount;

    /** -1 when unknown: a magnet's pieces before its metadata. */
    final int totalPieces;

    /** Null when unknown. */
    final String pieceSize;

    /** The piece selected when the read began, -1 for none. */
    final int selectedPiece;

    /** Null without a selected piece, or one that can't be read now. */
    final PieceDetails selectedPieceDetails;

    PiecesData(
        PieceIndexBitfield pieces,
        byte[] priorityMarks,
        long piecesCount,
        int totalPieces,
        String pieceSize,
        int selectedPiece,
        PieceDetails selectedPieceDetails) {
      this.pieces = pieces;
      this.priorityMarks = priorityMarks;
      this.piecesCount = piecesCount;
      this.totalPieces = totalPieces;
      this.pieceSize = pieceSize;
      this.selectedPiece = selectedPiece;
      this.selectedPieceDetails = selectedPieceDetails;
    }
  }

  /** Reads a transfer's pieces, on a background thread only. */
  interface PiecesReader {
    /** Null when the transfer's pieces can't be read now. */
    @Nullable
    PiecesData readPieces(String infoHash, int selectedPiece);

    /** Null when the piece can't be read now. */
    @Nullable
    PieceDetails readPieceDetails(String infoHash, int piece);
  }

  /** Reads the bound transfer's pieces from libtorrent. */
  private final class LibtorrentPiecesReader implements PiecesReader {
    @Override
    public PiecesData readPieces(String infoHash, int piece) {
      TorrentHandle handle = handle(infoHash);
      if (handle == null) {
        return null;
      }
      try {
        TorrentStatus status = handle.status(TorrentHandle.QUERY_PIECES);
        TorrentInfo torrentInfo = handle.torrentFile();
        PieceIndexBitfield pieces = status != null ? status.pieces() : null;
        // a downloaded piece is drawn full whatever its priority
        byte[] priorityMarks =
            pieces == null || pieces.isAllSet() ? null : readPriorityMarks(handle);
        // null for a piece past the torrent's last one
        PieceDetails details =
            piece >= 0 ? PieceDetails.read(infoHash, handle, torrentInfo, piece) : null;
        return new PiecesData(
            pieces,
            priorityMarks,
            pieces != null ? pieces.count() : -1,
            torrentInfo != null ? torrentInfo.numPieces() : -1,
            torrentInfo != null ? UIUtils.getBytesInHuman(torrentInfo.pieceSizeForReq(0)) : null,
            piece,
            details);
      } catch (Throwable t) {
        LOG.warn("Skipping pieces update because torrent status is unavailable", t);
        return null;
      }
    }

    @Override
    public PieceDetails readPieceDetails(String infoHash, int piece) {
      SystemUtils.ensureBackgroundThreadOrCrash("TransferDetailPiecesFragment::readPieceDetails");
      TorrentHandle handle = handle(infoHash);
      try {
        return handle != null
            ? PieceDetails.read(infoHash, handle, handle.torrentFile(), piece)
            : null;
      } catch (Throwable t) {
        LOG.warn("Could not read the details of piece " + piece, t);
        return null;
      }
    }

    /** The bound transfer's handle, null if it's another transfer's or there's none yet. */
    private TorrentHandle handle(String infoHash) {
      UIBittorrentDownload download = uiBittorrentDownload;
      if (download == null || !infoHash.equals(download.getInfoHash())) {
        return null;
      }
      return torrentHandle != null ? torrentHandle : download.getDl().getTorrentHandle();
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
