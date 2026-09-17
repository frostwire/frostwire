/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.relay.CatalogBrowser;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

/**
 * Shows the shared-torrent catalog of a peer that opted in to being browsable (Distributed search
 * result magnet flag {@code x.hc=1}).
 *
 * <p>The catalog is fetched over the IceBridge mesh with the same signed request the relay exposes
 * as {@code GET /catalog} (peers answer only when they opted in to {@code PUBLIC_CATALOG}); if the
 * mesh cannot answer, the peer's DHT-published index is tried so peers that are not currently on
 * the mesh are still browsable. Fetches run off the EDT and stale results from a superseded peer
 * are discarded.
 *
 * <p>Selected rows can be downloaded straight from the browsed holder: the synthesized magnet
 * carries {@code x.hp}, so the normal download path fetches the .torrent from that holder over the
 * mesh ({@code TORRENT_FETCH}) instead of relying on public trackers.
 *
 * <p>Threading: construct and interact on the EDT only; fetching is internal.
 */
public final class PeerCatalogWindow {

  private static final Logger LOG = Logger.getLogger(PeerCatalogWindow.class);
  private static final int FETCH_TIMEOUT_MS = 8000;
  private static final int MAX_PEER_CHOICES = 50;

  private static PeerCatalogWindow instance;

  private final JFrame frame = new JFrame(I18n.tr("Browse Shared Torrents"));
  private final CatalogTableModel model = new CatalogTableModel();
  private final JTable table = new JTable(model);
  private final JLabel peerLabel = new JLabel();
  private final JLabel status = new JLabel(I18n.tr("Browsing peer catalog..."));
  private final JButton downloadButton = new JButton(I18n.tr("Download"));
  private final JButton copyMagnetButton = new JButton(I18n.tr("Copy Magnet"));
  private final ExecutorService fetcher =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "peer-catalog-fetch");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicLong generation = new AtomicLong();
  private byte[] peerPub;

  private PeerCatalogWindow() {
    buildUi();
    frame.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            fetcher.shutdownNow();
            frame.dispose();
            instance = null;
          }
        });
  }

  /** Opens (or focuses) the window for {@code peerPub} and fetches its catalog. */
  public static void showForPeer(byte[] peerPub) {
    if (peerPub == null || peerPub.length != 32) {
      return;
    }
    if (!SwingUtilities.isEventDispatchThread()) {
      byte[] copy = peerPub.clone();
      SwingUtilities.invokeLater(() -> showForPeer(copy));
      return;
    }
    if (instance == null) {
      instance = new PeerCatalogWindow();
    }
    instance.setPeer(peerPub);
    instance.frame.setVisible(true);
    instance.frame.toFront();
    instance.refresh();
  }

  /**
   * Prompts for a peer (one of the known peers, or a pasted hex/base64url pubkey) and browses it.
   * Backs the Tools menu entry and the console button.
   */
  public static void showPeerPicker() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(PeerCatalogWindow::showPeerPicker);
      return;
    }
    List<PeerChoice> choices = knownPeers();
    JComboBox<Object> combo = new JComboBox<>(choices.toArray());
    combo.setEditable(true);
    if (choices.isEmpty()) {
      combo.setSelectedItem("");
    }
    int result =
        JOptionPane.showConfirmDialog(
            GUIMediator.getAppFrame(),
            combo,
            I18n.tr("Browse Shared Torrents"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (result != JOptionPane.OK_OPTION) {
      return;
    }
    Object selected = combo.getSelectedItem();
    String raw =
        selected instanceof PeerChoice ? ((PeerChoice) selected).hex : String.valueOf(selected);
    byte[] pub = parsePub(raw);
    if (pub == null) {
      JOptionPane.showMessageDialog(
          GUIMediator.getAppFrame(),
          I18n.tr("Invalid IceBridge URL: peer public key must be 64 hex characters (32 bytes)"),
          I18n.tr("IceBridge Error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    showForPeer(pub);
  }

  private static List<PeerChoice> knownPeers() {
    List<PeerChoice> out = new ArrayList<>();
    try {
      PeerDirectory dir = SearchEngine.getDistributedPeerDirectory();
      if (dir != null) {
        List<PeerDirectory.PeerInfo> infos = dir.topByTrustVerified(MAX_PEER_CHOICES);
        if (infos.isEmpty()) {
          infos = dir.topByTrust(MAX_PEER_CHOICES);
        }
        for (PeerDirectory.PeerInfo info : infos) {
          byte[] pub = info.peerPub();
          if (pub == null || pub.length != 32) {
            continue;
          }
          String hex = Hex.encode(pub);
          String host = info.hostname() == null ? "" : info.hostname();
          out.add(new PeerChoice(hex, hex.substring(0, 12) + "... " + host));
        }
      }
    } catch (Throwable t) {
      LOG.debug("peer picker could not list peers", t);
    }
    return out;
  }

  static byte[] parsePub(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.isEmpty()) {
      return null;
    }
    if (s.length() == 64 && isHex(s)) {
      try {
        byte[] pub = Hex.decode(s);
        return pub != null && pub.length == 32 ? pub : null;
      } catch (Throwable t) {
        return null;
      }
    }
    for (java.util.Base64.Decoder decoder :
        new java.util.Base64.Decoder[] {Base64.getUrlDecoder(), Base64.getDecoder()}) {
      try {
        byte[] pub = decoder.decode(s);
        if (pub.length == 32) {
          return pub;
        }
      } catch (Throwable ignored) {
        // Try the next alphabet.
      }
    }
    return null;
  }

  private static boolean isHex(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  private void setPeer(byte[] pub) {
    this.peerPub = pub.clone();
    String hex = Hex.encode(peerPub);
    peerLabel.setText(hex.substring(0, 16) + "...");
  }

  private void buildUi() {
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    top.add(peerLabel);
    JButton refresh = new JButton(I18n.tr("Refresh"));
    refresh.addActionListener(e -> refresh());
    top.add(refresh);
    JButton pick = new JButton(I18n.tr("Browse Shared Torrents") + "...");
    pick.addActionListener(e -> showPeerPicker());
    top.add(pick);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    downloadButton.setEnabled(false);
    downloadButton.addActionListener(e -> downloadSelected());
    actions.add(downloadButton);
    copyMagnetButton.setEnabled(false);
    copyMagnetButton.addActionListener(e -> copySelectedMagnet());
    actions.add(copyMagnetButton);

    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    bottom.add(status);

    table.setFillsViewportHeight(true);
    table.setRowHeight(22);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getColumnModel().getColumn(0).setPreferredWidth(460);
    table.getColumnModel().getColumn(1).setPreferredWidth(100);
    table.getColumnModel().getColumn(2).setPreferredWidth(70);
    table.getSelectionModel().addListSelectionListener(e -> updateActionState());
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              downloadSelected();
            }
          }
        });

    JPanel north = new JPanel(new BorderLayout());
    north.add(top, BorderLayout.NORTH);
    north.add(actions, BorderLayout.SOUTH);

    JPanel content = new JPanel(new BorderLayout());
    content.add(north, BorderLayout.NORTH);
    content.add(new JScrollPane(table), BorderLayout.CENTER);
    content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.add(content, BorderLayout.CENTER);
    frame.add(bottom, BorderLayout.SOUTH);
    frame.setSize(new Dimension(780, 500));
    frame.setLocationRelativeTo(null);
  }

  private void updateActionState() {
    boolean hasSelection = selectedEntry() != null && peerPub != null;
    downloadButton.setEnabled(hasSelection);
    copyMagnetButton.setEnabled(hasSelection);
  }

  private void refresh() {
    if (peerPub == null) {
      return;
    }
    final byte[] pub = peerPub.clone();
    final long gen = generation.incrementAndGet();
    model.setEntries(Collections.emptyList());
    status.setText(I18n.tr("Browsing peer catalog..."));
    fetcher.execute(
        () -> {
          List<RemoteIndexFetcher.RemoteTorrentEntry> entries = fetch(pub);
          SwingUtilities.invokeLater(
              () -> {
                if (gen != generation.get()) {
                  return;
                }
                model.setEntries(entries);
                status.setText(
                    entries.isEmpty()
                        ? I18n.tr("Could not fetch peer catalog (peer may be offline)")
                        : I18n.tr("Shared Torrents") + ": " + entries.size());
                updateActionState();
              });
        });
  }

  /** Mesh first (works for NAT'd peers on the mesh), then the peer's DHT-published index. */
  private static List<RemoteIndexFetcher.RemoteTorrentEntry> fetch(byte[] pub) {
    List<RemoteIndexFetcher.RemoteTorrentEntry> entries = fetchOverMesh(pub);
    if (!entries.isEmpty()) {
      return entries;
    }
    return fetchFromDht(pub);
  }

  private static List<RemoteIndexFetcher.RemoteTorrentEntry> fetchOverMesh(byte[] pub) {
    try {
      DistributedSearchTransport transport = SearchEngine.getDistributedSearchTransport();
      IdentityKeys identity = SearchEngine.getDistributedIdentity();
      if (transport == null || identity == null) {
        return Collections.emptyList();
      }
      return new CatalogBrowser(identity, transport).fetchCatalog(pub, FETCH_TIMEOUT_MS);
    } catch (Throwable t) {
      LOG.debug("peer catalog mesh fetch failed", t);
      return Collections.emptyList();
    }
  }

  private static List<RemoteIndexFetcher.RemoteTorrentEntry> fetchFromDht(byte[] pub) {
    try {
      RemoteIndexFetcher fetcher =
          new RemoteIndexFetcher(new RemoteIndexFetcher.DhtIndexSource(BTEngine.getInstance()));
      List<RemoteIndexFetcher.RemoteTorrentEntry> entries = fetcher.fetchCatalog(pub);
      return entries == null ? Collections.emptyList() : entries;
    } catch (Throwable t) {
      LOG.debug("peer catalog DHT fetch failed", t);
      return Collections.emptyList();
    }
  }

  private RemoteIndexFetcher.RemoteTorrentEntry selectedEntry() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return null;
    }
    return model.entryAt(table.convertRowIndexToModel(viewRow));
  }

  private void downloadSelected() {
    RemoteIndexFetcher.RemoteTorrentEntry entry = selectedEntry();
    if (entry == null || peerPub == null) {
      return;
    }
    GUIMediator.instance()
        .openTorrentSearchResult(newMeshTorrentSearchResult(entry, peerPub), true);
  }

  private void copySelectedMagnet() {
    RemoteIndexFetcher.RemoteTorrentEntry entry = selectedEntry();
    if (entry == null || peerPub == null) {
      return;
    }
    Toolkit.getDefaultToolkit()
        .getSystemClipboard()
        .setContents(new StringSelection(meshMagnet(entry, peerPub)), null);
  }

  static String meshMagnet(RemoteIndexFetcher.RemoteTorrentEntry entry, byte[] peerPub) {
    return UrlUtils.buildMagnetUrl(
            entry.infoHashHex(), entry.name(), DefaultTrackers.MAGNET_URL_PARAMETERS)
        + "&x.hp="
        + Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub);
  }

  private static TorrentSearchResult newMeshTorrentSearchResult(
      RemoteIndexFetcher.RemoteTorrentEntry entry, byte[] peerPub) {
    final String magnet = meshMagnet(entry, peerPub);
    final String infoHashHex = entry.infoHashHex();
    final String name = entry.name();
    final long size = entry.sizeBytes();
    return new TorrentSearchResult() {
      @Override
      public String getFilename() {
        return name + ".torrent";
      }

      @Override
      public long getSize() {
        return size;
      }

      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public String getDetailsUrl() {
        return magnet;
      }

      @Override
      public long getCreationTime() {
        return System.currentTimeMillis();
      }

      @Override
      public String getSource() {
        return "Distributed";
      }

      @Override
      public com.frostwire.licenses.License getLicense() {
        return Licenses.UNKNOWN;
      }

      @Override
      public String getThumbnailUrl() {
        return null;
      }

      @Override
      public boolean isPreliminary() {
        return false;
      }

      @Override
      public String getTorrentUrl() {
        return magnet;
      }

      @Override
      public String getHash() {
        return infoHashHex;
      }

      @Override
      public int getSeeds() {
        return 0;
      }

      @Override
      public String getReferrerUrl() {
        return null;
      }
    };
  }

  private static final class PeerChoice {
    final String hex;
    private final String label;

    PeerChoice(String hex, String label) {
      this.hex = hex;
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final class CatalogTableModel extends AbstractTableModel {
    private final List<RemoteIndexFetcher.RemoteTorrentEntry> entries = new ArrayList<>();

    void setEntries(List<RemoteIndexFetcher.RemoteTorrentEntry> newEntries) {
      entries.clear();
      if (newEntries != null) {
        entries.addAll(newEntries);
      }
      fireTableDataChanged();
    }

    RemoteIndexFetcher.RemoteTorrentEntry entryAt(int row) {
      return row >= 0 && row < entries.size() ? entries.get(row) : null;
    }

    @Override
    public int getRowCount() {
      return entries.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> I18n.tr("Name");
        case 1 -> I18n.tr("Size");
        default -> I18n.tr("Files");
      };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      RemoteIndexFetcher.RemoteTorrentEntry entry = entries.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> entry.name();
        case 1 -> GUIUtils.getBytesInHuman(entry.sizeBytes());
        default -> entry.fileCount();
      };
    }
  }
}
