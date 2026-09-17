/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import com.frostwire.search.relay.CatalogBrowser;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

/**
 * Shows the shared-torrent catalog of a peer that opted in to being browsable (Distributed search
 * result magnet flag {@code x.hc=1}).
 *
 * <p>The catalog is fetched over the IceBridge mesh with the same signed request the relay exposes
 * as {@code GET /catalog}; the peer answers only when it has opted in to {@code PUBLIC_CATALOG}.
 * Fetches run off the EDT and stale results from a superseded peer are discarded.
 *
 * <p>Threading: construct and interact on the EDT only; fetching is internal.
 */
public final class PeerCatalogWindow {

  private static final Logger LOG = Logger.getLogger(PeerCatalogWindow.class);
  private static final int FETCH_TIMEOUT_MS = 8000;

  private static PeerCatalogWindow instance;

  private final JFrame frame = new JFrame(I18n.tr("Peer Catalog"));
  private final CatalogTableModel model = new CatalogTableModel();
  private final JTable table = new JTable(model);
  private final JLabel peerLabel = new JLabel();
  private final JLabel status = new JLabel(I18n.tr("Browsing peer catalog..."));
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

  private void setPeer(byte[] pub) {
    this.peerPub = pub.clone();
    String hex = Hex.encode(peerPub);
    peerLabel.setText(I18n.tr("Peer:") + " " + hex.substring(0, 16) + "...");
  }

  private void buildUi() {
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    top.add(peerLabel);
    JButton refresh = new JButton(I18n.tr("Refresh"));
    refresh.addActionListener(e -> refresh());
    top.add(refresh);

    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    bottom.add(status);

    table.setFillsViewportHeight(true);
    table.setRowHeight(22);
    table.getColumnModel().getColumn(0).setPreferredWidth(460);
    table.getColumnModel().getColumn(1).setPreferredWidth(100);
    table.getColumnModel().getColumn(2).setPreferredWidth(70);

    JPanel content = new JPanel(new BorderLayout());
    content.add(top, BorderLayout.NORTH);
    content.add(new JScrollPane(table), BorderLayout.CENTER);
    content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.add(content, BorderLayout.CENTER);
    frame.add(bottom, BorderLayout.SOUTH);
    frame.setSize(new Dimension(760, 480));
    frame.setLocationRelativeTo(null);
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
                        ? I18n.tr("No answer. The peer may be offline or not sharing publicly.")
                        : I18n.tr("Shared torrents:") + " " + entries.size());
              });
        });
  }

  private static List<RemoteIndexFetcher.RemoteTorrentEntry> fetch(byte[] pub) {
    try {
      DistributedSearchTransport transport = SearchEngine.getDistributedSearchTransport();
      IdentityKeys identity = SearchEngine.getDistributedIdentity();
      if (transport == null || identity == null) {
        return Collections.emptyList();
      }
      return new CatalogBrowser(identity, transport).fetchCatalog(pub, FETCH_TIMEOUT_MS);
    } catch (Throwable t) {
      LOG.debug("peer catalog fetch failed", t);
      return Collections.emptyList();
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
