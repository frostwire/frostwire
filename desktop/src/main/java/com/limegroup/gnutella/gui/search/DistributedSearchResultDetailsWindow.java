/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.search.relay.NodeCapabilities;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.util.Hex;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.icebridge.PeerCatalogWindow;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.apache.commons.io.FileUtils;

/** Human-readable details and actions for an IceBridge distributed-search hit. */
final class DistributedSearchResultDetailsWindow {

  private DistributedSearchResultDetailsWindow() {}

  static void show(CompositeFileSearchResult result, String query, Runnable downloadTorrent) {
    if (result == null) {
      return;
    }
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> show(result, query, downloadTorrent));
      return;
    }

    Details details = describe(result, query, currentPeerDirectory());
    JDialog dialog =
        new JDialog(
            GUIMediator.getAppFrame(),
            I18n.tr("Distributed Search Result Details"),
            Dialog.ModalityType.MODELESS);
    JTextArea text = new JTextArea(details.description);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setCaretPosition(0);
    text.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(620, 360));

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    if (downloadTorrent != null) {
      JButton download = new JButton(I18n.tr("Download .torrent"));
      download.addActionListener(
          e -> {
            dialog.dispose();
            downloadTorrent.run();
          });
      actions.add(download);
    }
    if (details.holderPub != null) {
      JButton browse = new JButton(I18n.tr("Browse Shared Torrents"));
      browse.setToolTipText(
          I18n.tr("Browse this holder's shared index over IceBridge or its public DHT index."));
      browse.addActionListener(e -> PeerCatalogWindow.showForPeer(details.holderPub));
      actions.add(browse);
      JButton copyHolder = new JButton(I18n.tr("Copy Holder ID"));
      copyHolder.addActionListener(e -> copy(details.holderHex));
      actions.add(copyHolder);
    }
    if (!details.peerAddress.isEmpty()) {
      JButton copyPeerAddress = new JButton(I18n.tr("Copy Peer Address"));
      copyPeerAddress.addActionListener(e -> copy(details.peerAddress));
      actions.add(copyPeerAddress);
    }
    if (!details.seederEndpoints.isEmpty()) {
      JButton copySeederEndpoints = new JButton(I18n.tr("Copy Seeder Endpoints"));
      copySeederEndpoints.addActionListener(
          e -> copy(String.join(System.lineSeparator(), details.seederEndpoints)));
      actions.add(copySeederEndpoints);
    }
    if (!details.infoHash.isEmpty()) {
      JButton copyHash = new JButton(I18n.tr("Copy Info Hash"));
      copyHash.addActionListener(e -> copy(details.infoHash));
      actions.add(copyHash);
    }
    if (!details.magnet.isEmpty()) {
      JButton copyMagnet = new JButton(I18n.tr("Copy Magnet"));
      copyMagnet.addActionListener(e -> copy(details.magnet));
      actions.add(copyMagnet);
    }
    JButton close = new JButton(I18n.tr("Close"));
    close.addActionListener(e -> dialog.dispose());
    actions.add(close);

    dialog.setLayout(new BorderLayout());
    dialog.add(scroll, BorderLayout.CENTER);
    dialog.add(actions, BorderLayout.SOUTH);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.pack();
    dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
    dialog.setVisible(true);
  }

  static Details describe(CompositeFileSearchResult result, String query, PeerDirectory directory) {
    String magnet = nullToEmpty(result.getDetailsUrl());
    byte[] holderPub = LibTorrentMagnetDownloader.parseHolderPub(magnet);
    String holderHex = holderPub == null ? "" : Hex.encode(holderPub);
    String infoHash = result.getTorrentHash().orElse("");
    String peerAddress = "";
    StringBuilder text = new StringBuilder();
    append(text, I18n.tr("Name"), result.getDisplayName());
    append(text, I18n.tr("File"), result.getFilename());
    append(
        text,
        I18n.tr("Size"),
        result.getSize() >= 0
            ? FileUtils.byteCountToDisplaySize(result.getSize())
            : I18n.tr("Unknown"));
    append(text, I18n.tr("Search"), query);
    append(text, I18n.tr("Source"), result.getSource());
    append(text, I18n.tr("Info Hash"), infoHash);
    append(text, I18n.tr("IceBridge Holder ID"), holderHex);
    append(
        text,
        I18n.tr("Holder opted in to shared-catalog browsing"),
        LibTorrentMagnetDownloader.hasPublicCatalogFlag(magnet) ? I18n.tr("Yes") : I18n.tr("No"));

    PeerDirectory.PeerInfo peer =
        holderPub == null || directory == null ? null : directory.get(holderPub).orElse(null);
    if (peer != null) {
      peerAddress = formatAddress(peer.hostname(), peer.rudpPort());
      append(text, I18n.tr("Known peer address"), peerAddress);
      append(text, I18n.tr("IceBridge rUDP port"), Integer.toString(peer.rudpPort()));
      append(text, I18n.tr("Peer verified"), peer.isVerified() ? I18n.tr("Yes") : I18n.tr("No"));
      append(text, I18n.tr("IceBridge version"), peer.icebridgeVersion());
      append(text, I18n.tr("Peer capabilities"), capabilities(peer.capabilities()));
    } else {
      append(text, I18n.tr("Peer directory"), I18n.tr("Holder details are not currently cached."));
    }

    List<String> endpoints = seederEndpoints(magnet);
    append(
        text,
        I18n.tr("Advertised seeder endpoints"),
        endpoints.isEmpty() ? I18n.tr("None") : String.join("\n", endpoints));
    append(text, I18n.tr("Magnet URI"), magnet);
    text.append('\n')
        .append(
            I18n.tr(
                "The info hash identifies the torrent; the holder ID routes metadata requests through IceBridge."));
    return new Details(
        text.toString(), magnet, infoHash, holderPub, holderHex, peerAddress, endpoints);
  }

  static List<String> seederEndpoints(String magnet) {
    List<String> endpoints = new ArrayList<>();
    if (magnet == null || magnet.isEmpty()) {
      return endpoints;
    }
    int queryStart = magnet.indexOf('?');
    if (queryStart < 0 || queryStart == magnet.length() - 1) {
      return endpoints;
    }
    for (String pair : magnet.substring(queryStart + 1).split("&")) {
      int equals = pair.indexOf('=');
      if (equals <= 0 || !"x.pe".equals(pair.substring(0, equals))) {
        continue;
      }
      try {
        String endpoint = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
        if (!endpoint.isBlank() && endpoint.length() <= 256 && !endpoints.contains(endpoint)) {
          endpoints.add(endpoint);
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore malformed endpoint hints; keep the signed result details usable.
      }
    }
    return endpoints;
  }

  private static PeerDirectory currentPeerDirectory() {
    try {
      return SearchEngine.getDistributedPeerDirectory();
    } catch (Throwable unavailable) {
      return null;
    }
  }

  private static String capabilities(long bits) {
    List<String> names = new ArrayList<>();
    if (NodeCapabilities.has(bits, NodeCapabilities.SEARCH)) names.add("SEARCH");
    if (NodeCapabilities.has(bits, NodeCapabilities.RELAY)) names.add("RELAY");
    if (NodeCapabilities.has(bits, NodeCapabilities.INDEX)) names.add("INDEX");
    if (NodeCapabilities.has(bits, NodeCapabilities.PUBLIC_CATALOG)) names.add("PUBLIC_CATALOG");
    return names.isEmpty() ? I18n.tr("Unknown") : String.join(", ", names);
  }

  private static void append(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(nullToEmpty(value)).append('\n');
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String formatAddress(String host, int port) {
    if (host == null || host.isBlank() || port <= 0) {
      return "";
    }
    return host.indexOf(':') >= 0 && !host.startsWith("[")
        ? "[" + host + "]:" + port
        : host + ":" + port;
  }

  private static void copy(String value) {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
  }

  static final class Details {
    final String description;
    final String magnet;
    final String infoHash;
    final byte[] holderPub;
    final String holderHex;
    final String peerAddress;
    final List<String> seederEndpoints;

    Details(
        String description,
        String magnet,
        String infoHash,
        byte[] holderPub,
        String holderHex,
        String peerAddress,
        List<String> seederEndpoints) {
      this.description = description;
      this.magnet = magnet;
      this.infoHash = infoHash;
      this.holderPub = holderPub == null ? null : holderPub.clone();
      this.holderHex = holderHex;
      this.peerAddress = peerAddress;
      this.seederEndpoints = List.copyOf(seederEndpoints);
    }
  }
}
