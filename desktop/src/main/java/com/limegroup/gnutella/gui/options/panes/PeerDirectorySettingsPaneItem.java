/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.DistributedSearchEngineWire;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

public final class PeerDirectorySettingsPaneItem extends AbstractPaneItem {

  private static final Logger LOG = Logger.getLogger(PeerDirectorySettingsPaneItem.class);

  private static final String TITLE = I18n.tr("Peers");
  private static final String LABEL =
      I18n.tr(
          "Peers discovered on the distributed search network, ordered by trust score. "
              + "Right-click a peer to copy its key, block it, remove it, or browse its shared torrents.");

  private static final String[] COLUMN_NAMES = {
    I18n.tr("Peer Public Key"),
    I18n.tr("Hostname"),
    I18n.tr("IceBridge"),
    I18n.tr("uTP Port"),
    I18n.tr("Trust Score"),
    I18n.tr("Verified"),
    I18n.tr("Spam"),
    I18n.tr("Endorsers"),
    I18n.tr("Last Updated")
  };

  private final PeerTableModel TABLE_MODEL = new PeerTableModel();
  private final JTable PEER_TABLE = new JTable(TABLE_MODEL);
  private final JLabel COUNT_LABEL = new JLabel(I18n.tr("Showing 0 of 0 peers"));
  private final JButton REFRESH_BUTTON = new JButton(I18n.tr("Refresh"));

  public PeerDirectorySettingsPaneItem() {
    super(TITLE, LABEL);

    PEER_TABLE.setAutoCreateRowSorter(true);
    PEER_TABLE.setRowSelectionAllowed(true);
    PEER_TABLE.setColumnSelectionAllowed(false);
    PEER_TABLE.getTableHeader().setReorderingAllowed(false);
    PEER_TABLE.addMouseListener(new PeerTableMouseListener());

    add(buildTablePanel());
    add(getHorizontalSeparator());
    add(buildActionsPanel());
    add(getVerticalSeparator());

    REFRESH_BUTTON.addActionListener(e -> refreshPeerList());
  }

  private JPanel buildTablePanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(2, 4, 2, 4);

    JScrollPane scroll = new JScrollPane(PEER_TABLE);
    scroll.setPreferredSize(new Dimension(800, 280));

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.weighty = 1;
    c.fill = GridBagConstraints.BOTH;
    panel.add(scroll, c);

    c.gridx = 0;
    c.gridy = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(COUNT_LABEL, c);

    c.fill = GridBagConstraints.NONE;
    return panel;
  }

  private JPanel buildActionsPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.add(REFRESH_BUTTON);
    return panel;
  }

  @Override
  public void initOptions() {
    refreshPeerList();
  }

  private void refreshPeerList() {
    PeerDirectory directory = DistributedSearchEngineWire.getPeerDirectory();
    if (directory == null) {
      TABLE_MODEL.setPeers(Collections.emptyList(), Collections.emptyList());
      COUNT_LABEL.setText(I18n.tr("(peer directory not available)"));
      return;
    }
    REFRESH_BUTTON.setEnabled(false);
    BackgroundQueuedExecutorService.schedule(
        () -> {
          try {
            List<PeerDirectory.PeerInfo> peers = directory.topByTrust(100);
            List<Double> trustScores = new ArrayList<>(peers.size());
            for (PeerDirectory.PeerInfo peer : peers) {
              trustScores.add(directory.trustScore(peer.peerPub()));
            }
            PeerRefresh refresh = new PeerRefresh(peers, trustScores, directory.size());
            GUIMediator.safeInvokeLater(
                () -> {
                  REFRESH_BUTTON.setEnabled(true);
                  TABLE_MODEL.setPeers(refresh.peers, refresh.trustScores);
                  COUNT_LABEL.setText(
                      I18n.tr("Showing")
                          + " "
                          + refresh.peers.size()
                          + " "
                          + I18n.tr("of")
                          + " "
                          + refresh.total
                          + " "
                          + I18n.tr("peers"));
                });
          } catch (Throwable t) {
            GUIMediator.safeInvokeLater(
                () -> {
                  REFRESH_BUTTON.setEnabled(true);
                  LOG.error("Failed to refresh peer list", t);
                  TABLE_MODEL.setPeers(Collections.emptyList(), Collections.emptyList());
                  COUNT_LABEL.setText(I18n.tr("(failed to read peer directory)"));
                });
          }
        });
  }

  private PeerDirectory.PeerInfo selectedPeer() {
    int viewRow = PEER_TABLE.getSelectedRow();
    if (viewRow < 0) {
      return null;
    }
    int modelRow = PEER_TABLE.convertRowIndexToModel(viewRow);
    return TABLE_MODEL.peerAt(modelRow);
  }

  private void copyPublicKey() {
    PeerDirectory.PeerInfo peer = selectedPeer();
    if (peer == null) {
      return;
    }
    String hex = Hex.encode(peer.peerPub());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(hex), null);
  }

  private void blockPeer() {
    PeerDirectory.PeerInfo peer = selectedPeer();
    if (peer == null) {
      return;
    }
    PeerDirectory directory = DistributedSearchEngineWire.getPeerDirectory();
    if (directory == null) {
      return;
    }
    try {
      directory.markSpam(peer.peerPub());
      refreshPeerList();
    } catch (Throwable t) {
      LOG.error("Failed to mark peer as spam", t);
      GUIMediator.showError(I18n.tr("Could not block peer: ") + t.getMessage());
    }
  }

  private void removePeer() {
    PeerDirectory.PeerInfo peer = selectedPeer();
    if (peer == null) {
      return;
    }
    PeerDirectory directory = DistributedSearchEngineWire.getPeerDirectory();
    if (directory == null) {
      return;
    }
    try {
      directory.evict(peer.peerPub());
      refreshPeerList();
    } catch (Throwable t) {
      LOG.error("Failed to evict peer", t);
      GUIMediator.showError(I18n.tr("Could not remove peer: ") + t.getMessage());
    }
  }

  private void browseSharedTorrents() {
    PeerDirectory.PeerInfo peer = selectedPeer();
    if (peer == null) {
      return;
    }
    byte[] peerPub = peer.peerPub();
    String peerHex = Hex.encode(peerPub);

    JDialog dialog =
        new JDialog(
            GUIMediator.getAppFrame(),
            I18n.tr("Shared Torrents") + " - " + truncateKey(peerHex),
            true);
    dialog.setLayout(new BorderLayout(4, 4));

    JLabel statusLabel = new JLabel(I18n.tr("Fetching peer catalog..."), SwingConstants.CENTER);
    statusLabel.setPreferredSize(new Dimension(640, 30));

    TorrentTableModel torrentModel = new TorrentTableModel();
    JTable torrentTable = new JTable(torrentModel);
    torrentTable.setAutoCreateRowSorter(true);
    torrentTable.getTableHeader().setReorderingAllowed(false);
    JScrollPane scroll = new JScrollPane(torrentTable);
    scroll.setPreferredSize(new Dimension(640, 320));

    JButton closeButton = new JButton(I18n.tr("Close"));
    closeButton.addActionListener(e -> dialog.dispose());
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(closeButton);

    dialog.add(statusLabel, BorderLayout.NORTH);
    dialog.add(scroll, BorderLayout.CENTER);
    dialog.add(buttonPanel, BorderLayout.SOUTH);
    dialog.pack();
    dialog.setLocationRelativeTo(GUIMediator.getAppFrame());

    SwingWorker<List<RemoteIndexFetcher.RemoteTorrentEntry>, Void> worker =
        new SwingWorker<List<RemoteIndexFetcher.RemoteTorrentEntry>, Void>() {
          @Override
          protected List<RemoteIndexFetcher.RemoteTorrentEntry> doInBackground() {
            try {
              BTEngine bt = BTEngine.getInstance();
              if (bt == null) {
                return null;
              }
              RemoteIndexFetcher.DhtIndexSource source = new RemoteIndexFetcher.DhtIndexSource(bt);
              RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
              return fetcher.fetchCatalog(peerPub);
            } catch (Throwable t) {
              LOG.error("Failed to fetch peer catalog for " + peerHex, t);
              return null;
            }
          }

          @Override
          protected void done() {
            try {
              List<RemoteIndexFetcher.RemoteTorrentEntry> entries = get();
              if (entries == null) {
                statusLabel.setText(I18n.tr("Could not fetch peer catalog (peer may be offline)"));
                torrentModel.setEntries(Collections.emptyList());
                return;
              }
              torrentModel.setEntries(entries);
              statusLabel.setText(entries.size() + " " + I18n.tr("torrents"));
            } catch (Throwable t) {
              LOG.error("Browse catalog worker failed", t);
              statusLabel.setText(I18n.tr("Could not fetch peer catalog (peer may be offline)"));
              torrentModel.setEntries(Collections.emptyList());
            }
          }
        };
    worker.execute();

    dialog.setVisible(true);
  }

  private static String truncateKey(String hex) {
    if (hex == null) {
      return "";
    }
    if (hex.length() <= 16) {
      return hex;
    }
    return hex.substring(0, 16) + "...";
  }

  private static String formatTimestamp(long ms) {
    if (ms <= 0) {
      return "—";
    }
    try {
      return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ms));
    } catch (Throwable t) {
      return "—";
    }
  }

  private static final class PeerRefresh {
    final List<PeerDirectory.PeerInfo> peers;
    final List<Double> trustScores;
    final int total;

    PeerRefresh(List<PeerDirectory.PeerInfo> peers, List<Double> trustScores, int total) {
      this.peers = peers;
      this.trustScores = trustScores;
      this.total = total;
    }
  }

  private final class PeerTableMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      showPopupIfNeeded(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      showPopupIfNeeded(e);
    }

    private void showPopupIfNeeded(MouseEvent e) {
      if (!e.isPopupTrigger()) {
        return;
      }
      int viewRow = PEER_TABLE.rowAtPoint(e.getPoint());
      if (viewRow < 0) {
        return;
      }
      PEER_TABLE.setRowSelectionInterval(viewRow, viewRow);

      JPopupMenu menu = new JPopupMenu();
      JMenuItem copyItem = new JMenuItem(I18n.tr("Copy Public Key"));
      copyItem.addActionListener(ev -> copyPublicKey());
      menu.add(copyItem);

      JMenuItem blockItem = new JMenuItem(I18n.tr("Block Peer (mark as spam)"));
      blockItem.addActionListener(ev -> blockPeer());
      menu.add(blockItem);

      JMenuItem removeItem = new JMenuItem(I18n.tr("Remove Peer"));
      removeItem.addActionListener(ev -> removePeer());
      menu.add(removeItem);

      menu.addSeparator();

      JMenuItem browseItem = new JMenuItem(I18n.tr("Browse Shared Torrents"));
      browseItem.addActionListener(ev -> browseSharedTorrents());
      menu.add(browseItem);

      menu.show(PEER_TABLE, e.getX(), e.getY());
    }
  }

  static final class PeerTableModel extends AbstractTableModel {

    private final List<PeerDirectory.PeerInfo> rows = new ArrayList<>();
    private final List<Double> trustScores = new ArrayList<>();

    void setPeers(List<PeerDirectory.PeerInfo> peers, List<Double> scores) {
      rows.clear();
      trustScores.clear();
      if (peers != null) {
        rows.addAll(peers);
      }
      if (scores != null && scores.size() == rows.size()) {
        trustScores.addAll(scores);
      } else {
        for (int i = 0; i < rows.size(); i++) {
          trustScores.add(0.0);
        }
      }
      fireTableDataChanged();
    }

    PeerDirectory.PeerInfo peerAt(int row) {
      if (row < 0 || row >= rows.size()) {
        return null;
      }
      return rows.get(row);
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
      PeerDirectory.PeerInfo p = rows.get(row);
      switch (column) {
        case 0:
          return truncateKey(Hex.encode(p.peerPub()));
        case 1:
          String host = p.hostname();
          return host == null ? "" : host;
        case 2:
          String ib = p.icebridgeVersion();
          return (ib == null || ib.isBlank()) ? "-" : ib;
        case 3:
          return p.utpPort();
        case 4:
          return String.format("%.2f", trustScores.get(row));
        case 5:
          return p.isVerified() ? I18n.tr("Yes") : I18n.tr("No");
        case 6:
          return p.isSpam() ? I18n.tr("Yes") : I18n.tr("No");
        case 7:
          return p.endorserCount();
        case 8:
          return formatTimestamp(p.lastUpdatedMs());
        default:
          return "";
      }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  }

  private static final class TorrentTableModel extends AbstractTableModel {

    private static final String[] COLS = {
      I18n.tr("Name"), I18n.tr("Size"), I18n.tr("Files"), I18n.tr("Info Hash")
    };

    private final List<RemoteIndexFetcher.RemoteTorrentEntry> rows = new ArrayList<>();

    void setEntries(List<RemoteIndexFetcher.RemoteTorrentEntry> entries) {
      rows.clear();
      if (entries != null) {
        rows.addAll(entries);
      }
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
      RemoteIndexFetcher.RemoteTorrentEntry e = rows.get(row);
      switch (column) {
        case 0:
          return e.name();
        case 1:
          return formatSize(e.sizeBytes());
        case 2:
          return e.fileCount();
        case 3:
          return e.infoHashHex();
        default:
          return "";
      }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    private static String formatSize(long bytes) {
      if (bytes < 0) {
        return "—";
      }
      double v = bytes;
      String[] units = {"B", "KB", "MB", "GB", "TB"};
      int u = 0;
      while (v >= 1024 && u < units.length - 1) {
        v /= 1024;
        u++;
      }
      return String.format("%.1f %s", v, units[u]);
    }
  }

  @Override
  public boolean applyOptions() {
    return true;
  }

  @Override
  public boolean isDirty() {
    return false;
  }
}
