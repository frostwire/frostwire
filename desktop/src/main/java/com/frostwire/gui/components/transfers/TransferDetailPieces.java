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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.FileSlice;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.PartialPieceInfo;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;

public final class TransferDetailPieces extends JPanel
    implements TransferDetailComponent.TransferDetailPanel {
  private final JLabel pieceSizeLabel;
  private final JLabel totalPiecesLabel;
  private final HexHivePanel hexHivePanel;
  private final HexHiveAdapter hexHivePanelAdapter;
  private boolean pieceSizeAlreadySet = false;
  private BittorrentDownload bittorrentDownload;

  TransferDetailPieces() {
    super(new MigLayout("fillx, insets 0 0 0 0, gap 0 5px"));
    int backgroundColor =
        ThemeMediator.isLightThemeOn() ? 0xffffff : ThemeMediator.APP_REALLY_DARK_COLOR.getRGB();
    hexHivePanel =
        new HexHivePanel(16, 0x264053, 0xf2f2f2, 0x33b5e5, backgroundColor, 5, 0, 0, 5, true);
    hexHivePanelAdapter = new HexHiveAdapter();
    // Create labels without HTML content to avoid EDT violation
    pieceSizeLabel = new JLabel();
    totalPiecesLabel = new JLabel();
    add(totalPiecesLabel, "gapleft 10px");
    add(pieceSizeLabel, "grow, gapright 10px, wmin 130px, hmax 25px, wrap");
    hexHivePanel.setBackground(Color.WHITE);
    hexHivePanel.setOpaque(true);
    // Defer HTML content and JScrollPane creation to avoid EDT violation
    // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
    SwingUtilities.invokeLater(
        () -> {
          pieceSizeLabel.setText("<html><b>" + I18n.tr("Piece Size") + "</b>:</html>");
          totalPiecesLabel.setText("<html><b>" + I18n.tr("Total Pieces") + "</b>:</html>");
          JScrollPane jScrollPane = new JScrollPane(hexHivePanel);
          jScrollPane.setOpaque(true);
          jScrollPane.getViewport().setOpaque(true);
          jScrollPane.getViewport().setBackground(new Color(backgroundColor));
          jScrollPane.setBackground(new Color(backgroundColor));
          jScrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          jScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          jScrollPane.setHorizontalScrollBarPolicy(
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
          add(jScrollPane, "push, grow, height 240px, gap 10px 10px 0px 5px, span 2");
          revalidate();
          repaint();
        });
  }

  @Override
  public void updateData(BittorrentDownload btDownload) {
    final boolean sameDownload = (bittorrentDownload == btDownload);
    bittorrentDownload = btDownload;
    // Already invoked from BackgroundQueuedExecutorService — JNI calls are safe here.
    hexHivePanelAdapter.updateData(btDownload);
    final String pieceSize = hexHivePanelAdapter.getPieceSizeInHuman();
    final String pieceCounts =
        hexHivePanelAdapter.getFullHexagonsCount()
            + "/"
            + hexHivePanelAdapter.getTotalHexagonsCount();
    final int totalHexs = hexHivePanelAdapter.getTotalHexagonsCount();
    final BittorrentDownload expectedDownload = btDownload;
    SwingUtilities.invokeLater(
        () -> {
          if (bittorrentDownload != expectedDownload) {
            return;
          }
          pieceSizeAlreadySet = sameDownload;
          updatePieceSizeLabel(pieceSize);
          updateTotalPiecesLabel(pieceCounts);
          if (totalHexs >= 0) {
            if (!sameDownload) {
              hexHivePanel.clearRenderCache();
            }
            hexHivePanel.updateData(hexHivePanelAdapter);
            hexHivePanel.revalidate();
          }
          revalidate();
          repaint();
        });
  }

  private void updatePieceSizeLabel(String pieceSize) {
    if (!pieceSizeAlreadySet) {
      pieceSizeLabel.setText(
          "<html><b nowrap>" + I18n.tr("Piece Size") + "</b>:" + pieceSize + "</html>");
      pieceSizeAlreadySet = true;
    }
  }

  private void updateTotalPiecesLabel(String totalPieces) {
    totalPiecesLabel.setText(
        "<html><b>" + I18n.tr("Total Pieces") + "</b>:" + totalPieces + "</html>");
  }

  /// /////////////////////////////////////////////////////////////////////////////

  private static final class HexHiveAdapter
      implements HexHivePanel.HexDataAdapter<BittorrentDownload> {
    private BittorrentDownload bittorrentDownload;
    private int totalPieces;
    private int numFullPieces;
    private PieceIndexBitfield pieces;
    private String pieceSizeInHuman;
    // read by the hover thread in describeHex
    private volatile TorrentHandle torrentHandle;
    private volatile TorrentInfo torrentInfo;

    @Override
    public void updateData(BittorrentDownload btDownload) {
      bittorrentDownload = btDownload;
      TorrentHandle torrentHandle = bittorrentDownload.getDl().getTorrentHandle();
      TorrentStatus status = torrentHandle.status(TorrentHandle.QUERY_PIECES);
      TorrentInfo torrentInfo = torrentHandle.torrentFile();
      this.torrentHandle = torrentHandle;
      this.torrentInfo = torrentInfo;
      pieceSizeInHuman = GUIUtils.getBytesInHuman(torrentInfo.pieceSizeForReq(0));
      totalPieces = torrentInfo.numPieces();
      pieces = status.pieces();
      if (pieces.isAllSet()) {
        numFullPieces = totalPieces;
      } else if (pieces.isNoneSet() || pieces.isEmpty()) {
        numFullPieces = 0;
      } else {
        numFullPieces = 0;
        for (int i = 0; i < pieces.count(); i++) {
          if (pieces.getBit(i)) {
            numFullPieces++;
          }
        }
      }
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

    private static final int MAX_LISTED_FILES = 4;
    private static final int MAX_FILE_NAME_LENGTH = 48;

    @Override
    public Map<String, String> describeHex(int piece) {
      TorrentHandle handle = torrentHandle;
      TorrentInfo info = torrentInfo;
      if (handle == null
          || info == null
          || !handle.isValid()
          || piece < 0
          || piece >= info.numPieces()) {
        return null;
      }
      Map<String, String> rows = new LinkedHashMap<>();
      int pieceSize = info.pieceSize(piece);
      long firstByte = (long) piece * info.pieceLength();
      rows.put(
          I18n.tr("Piece"),
          I18n.tr(
              "#{0} of {1}", String.format("%,d", piece), String.format("%,d", info.numPieces())));
      rows.put(I18n.tr("State"), describeState(handle, piece));
      rows.put(
          I18n.tr("Size"),
          I18n.tr(
              "{0} ({1} bytes)",
              GUIUtils.getBytesInHuman(pieceSize), String.format("%,d", pieceSize)));
      rows.put(
          I18n.tr("Byte range"), String.format("%,d - %,d", firstByte, firstByte + pieceSize - 1));
      int[] availability = handle.pieceAvailability();
      if (availability != null && piece < availability.length) {
        rows.put(I18n.tr("Peers with it"), String.valueOf(availability[piece]));
      }
      rows.put(I18n.tr("Priority"), describePriority(handle.piecePriority(piece)));
      Sha1Hash hash = info.hashForPiece(piece);
      if (hash != null && !hash.isAllZeros()) {
        // v2-only torrents carry no per-piece SHA-1
        rows.put("SHA-1", hash.toHex());
      }
      List<FileSlice> slices = info.mapBlock(piece, 0, pieceSize);
      FileStorage files = info.files();
      StringBuilder fileLines = new StringBuilder();
      int listed = 0;
      int skipped = 0;
      for (FileSlice slice : slices) {
        String path = files.filePath(slice.fileIndex());
        if (path.startsWith(".pad") || path.contains("/.pad/") || path.contains("\\.pad\\")) {
          continue; // BEP 47 padding files aren't real content
        }
        if (listed == MAX_LISTED_FILES) {
          skipped++;
          continue;
        }
        if (listed > 0) {
          fileLines.append('\n');
        }
        fileLines
            .append(abbreviate(files.fileName(slice.fileIndex()), MAX_FILE_NAME_LENGTH))
            .append(" (")
            .append(GUIUtils.getBytesInHuman(slice.size()))
            .append(')');
        listed++;
      }
      if (skipped > 0) {
        fileLines.append('\n').append(I18n.tr("and {0} more", skipped));
      }
      if (listed > 0) {
        rows.put(listed + skipped > 1 ? I18n.tr("Files") : I18n.tr("File"), fileLines.toString());
      }
      return rows;
    }

    private static String describeState(TorrentHandle handle, int piece) {
      if (handle.havePiece(piece)) {
        return I18n.tr("Downloaded and verified");
      }
      for (PartialPieceInfo partial : handle.getDownloadQueue()) {
        if (partial.pieceIndex() == piece) {
          return I18n.tr(
              "Downloading, {0} of {1} blocks ({2} requested)",
              partial.finished(), partial.blocksInPiece(), partial.requested());
        }
      }
      if (handle.piecePriority(piece) == Priority.IGNORE) {
        return I18n.tr("Not downloading (skipped files)");
      }
      return I18n.tr("Missing");
    }

    private static String describePriority(Priority priority) {
      if (priority == Priority.IGNORE) {
        return I18n.tr("Skip");
      }
      if (priority == Priority.NORMAL) {
        return I18n.tr("Normal");
      }
      return I18n.tr("High") + " (" + priority.ordinal() + ")";
    }

    private static String abbreviate(String text, int maxLength) {
      if (text == null || text.length() <= maxLength) {
        return text;
      }
      int keep = (maxLength - 3) / 2;
      return text.substring(0, keep) + "..." + text.substring(text.length() - keep);
    }
  }
}
