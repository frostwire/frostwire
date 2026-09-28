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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.apache.commons.io.FileUtils;

/** Human-readable details and actions for an IceBridge distributed-search hit. */
final class DistributedSearchResultDetailsWindow {

  /** Values longer than this are shortened on screen; copying always uses the full value. */
  static final int MAX_DISPLAY_CHARS = 72;

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

    JLabel status = new JLabel(" ");
    status.setForeground(Color.GRAY);
    Timer clearStatus = new Timer(2500, e -> status.setText(" "));
    clearStatus.setRepeats(false);

    JPanel form = buildForm(details.fields, status, clearStatus);

    JLabel note =
        new JLabel(
            "<html>"
                + I18n.tr(
                    "The info hash identifies the torrent; the holder ID routes metadata requests through IceBridge.")
                + "</html>");
    note.setForeground(Color.GRAY);
    note.setFont(note.getFont().deriveFont(note.getFont().getSize2D() - 1f));

    JPanel center = new JPanel(new BorderLayout(0, 10));
    center.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
    center.add(form, BorderLayout.CENTER);
    center.add(note, BorderLayout.SOUTH);

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
    }
    JButton close = new JButton(I18n.tr("Close"));
    close.addActionListener(e -> dialog.dispose());
    actions.add(close);

    JPanel south = new JPanel(new BorderLayout());
    status.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
    south.add(status, BorderLayout.WEST);
    south.add(actions, BorderLayout.EAST);

    dialog.setLayout(new BorderLayout());
    dialog.add(center, BorderLayout.CENTER);
    dialog.add(south, BorderLayout.SOUTH);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.getRootPane().setDefaultButton(close);
    dialog.pack();
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
    dialog.setVisible(true);
  }

  /** Label column, value column, and a copy-icon column reserved for every row. */
  private static JPanel buildForm(List<Field> fields, JLabel status, Timer clearStatus) {
    JPanel form = new JPanel(new GridBagLayout());
    ImageIcon copyIcon = GUIMediator.getThemeImage("copy_paste_gray.png");
    ImageIcon copyPressedIcon = GUIMediator.getThemeImage("copy_paste.png");
    int row = 0;
    for (Field field : fields) {
      GridBagConstraints c = new GridBagConstraints();
      c.gridy = row++;
      c.insets = new Insets(3, 0, 3, 10);
      c.anchor = GridBagConstraints.NORTHEAST;

      JLabel label = new JLabel(field.label + ":");
      label.setForeground(Color.GRAY);
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      c.gridx = 0;
      form.add(label, c);

      JLabel value = new JLabel(displayHtml(field));
      value.setFont(value.getFont().deriveFont(Font.PLAIN));
      if (field.monospace) {
        value.setFont(new Font(Font.MONOSPACED, Font.PLAIN, value.getFont().getSize()));
      }
      c.gridx = 1;
      c.weightx = 1;
      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.HORIZONTAL;
      form.add(value, c);

      if (field.copyable) {
        Runnable doCopy =
            () -> {
              copy(field.value);
              status.setText(I18n.tr("Copied to clipboard") + ": " + field.label);
              clearStatus.restart();
            };
        String tip = I18n.tr("Click to copy") + " " + field.label;
        value.setToolTipText(
            field.display.equals(field.value)
                ? tip
                : "<html>" + tip + "<br>" + escape(field.value) + "</html>");
        value.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        value.addMouseListener(
            new MouseAdapter() {
              @Override
              public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                  doCopy.run();
                }
              }
            });

        JButton copyButton = new JButton(copyIcon);
        copyButton.setPressedIcon(copyPressedIcon);
        copyButton.setRolloverIcon(copyPressedIcon);
        copyButton.setContentAreaFilled(false);
        copyButton.setBorderPainted(false);
        copyButton.setFocusable(false);
        copyButton.setMargin(new Insets(0, 0, 0, 0));
        copyButton.setBorder(BorderFactory.createEmptyBorder());
        copyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyButton.setToolTipText(tip);
        copyButton.getAccessibleContext().setAccessibleName(tip);
        copyButton.addActionListener(e -> doCopy.run());
        GridBagConstraints ic = new GridBagConstraints();
        ic.gridx = 2;
        ic.gridy = c.gridy;
        ic.insets = new Insets(2, 0, 2, 0);
        ic.anchor = GridBagConstraints.NORTH;
        form.add(copyButton, ic);
      } else {
        // Keep the icon column width so values stay aligned.
        GridBagConstraints ic = new GridBagConstraints();
        ic.gridx = 2;
        ic.gridy = c.gridy;
        form.add(spacer(copyIcon), ic);
      }
    }
    return form;
  }

  private static JComponent spacer(ImageIcon icon) {
    JLabel spacer = new JLabel();
    int w = icon == null ? 16 : icon.getIconWidth();
    spacer.setPreferredSize(new java.awt.Dimension(w, 1));
    return spacer;
  }

  private static String displayHtml(Field field) {
    return "<html>" + escape(field.display).replace("\n", "<br>") + "</html>";
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static Details describe(CompositeFileSearchResult result, String query, PeerDirectory directory) {
    String magnet = nullToEmpty(result.getDetailsUrl());
    byte[] holderPub = LibTorrentMagnetDownloader.parseHolderPub(magnet);
    String holderHex = holderPub == null ? "" : Hex.encode(holderPub);
    String infoHash = result.getTorrentHash().orElse("");
    String peerAddress = "";
    List<Field> fields = new ArrayList<>();
    add(fields, I18n.tr("Name"), result.getDisplayName(), true, false);
    add(fields, I18n.tr("File"), result.getFilename(), true, false);
    add(
        fields,
        I18n.tr("Size"),
        result.getSize() >= 0
            ? FileUtils.byteCountToDisplaySize(result.getSize())
            : I18n.tr("Unknown"),
        false,
        false);
    add(fields, I18n.tr("Search"), query, false, false);
    add(fields, I18n.tr("Source"), result.getSource(), false, false);
    add(fields, I18n.tr("Info Hash"), infoHash, true, true);
    add(fields, I18n.tr("IceBridge Holder ID"), holderHex, true, true);
    add(
        fields,
        I18n.tr("Shared-catalog browsing"),
        LibTorrentMagnetDownloader.hasPublicCatalogFlag(magnet)
            ? I18n.tr("Holder opted in")
            : I18n.tr("Not offered"),
        false,
        false);

    PeerDirectory.PeerInfo peer =
        holderPub == null || directory == null ? null : directory.get(holderPub).orElse(null);
    if (peer != null) {
      peerAddress = formatAddress(peer.hostname(), peer.rudpPort());
      add(fields, I18n.tr("Known peer address"), peerAddress, true, true);
      add(
          fields,
          I18n.tr("Peer verified"),
          peer.isVerified() ? I18n.tr("Yes") : I18n.tr("No"),
          false,
          false);
      add(fields, I18n.tr("IceBridge version"), peer.icebridgeVersion(), false, false);
      add(fields, I18n.tr("Peer capabilities"), capabilities(peer.capabilities()), false, false);
    } else {
      add(
          fields,
          I18n.tr("Peer directory"),
          I18n.tr("Holder details are not currently cached."),
          false,
          false);
    }

    List<String> endpoints = seederEndpoints(magnet);
    if (endpoints.isEmpty()) {
      add(fields, I18n.tr("Seeder endpoints"), I18n.tr("None advertised"), false, false);
    } else {
      add(fields, I18n.tr("Seeder endpoints"), String.join("\n", endpoints), true, true);
    }
    add(fields, I18n.tr("Magnet URI"), magnet, true, true);

    StringBuilder text = new StringBuilder();
    for (Field f : fields) {
      text.append(f.label).append(": ").append(f.value).append('\n');
    }
    return new Details(
        text.toString(), fields, magnet, infoHash, holderPub, holderHex, peerAddress, endpoints);
  }

  private static void add(
      List<Field> fields, String label, String value, boolean copyable, boolean monospace) {
    if (value == null || value.isBlank()) {
      return;
    }
    fields.add(new Field(label, value, copyable, monospace));
  }

  /** Shortens each line of a long value for display; the full value is what gets copied. */
  static String shorten(String value) {
    StringBuilder out = new StringBuilder();
    String[] lines = value.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.length() > MAX_DISPLAY_CHARS) {
        line = line.substring(0, MAX_DISPLAY_CHARS - 1) + "\u2026";
      }
      if (i > 0) {
        out.append('\n');
      }
      out.append(line);
    }
    return out.toString();
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

  /** One labeled row. {@code value} is copied; {@code display} is what is drawn. */
  static final class Field {
    final String label;
    final String value;
    final String display;
    final boolean copyable;
    final boolean monospace;

    Field(String label, String value, boolean copyable, boolean monospace) {
      this.label = label;
      this.value = value;
      this.display = shorten(value);
      this.copyable = copyable;
      this.monospace = monospace;
    }
  }

  static final class Details {
    final String description;
    final List<Field> fields;
    final String magnet;
    final String infoHash;
    final byte[] holderPub;
    final String holderHex;
    final String peerAddress;
    final List<String> seederEndpoints;

    Details(
        String description,
        List<Field> fields,
        String magnet,
        String infoHash,
        byte[] holderPub,
        String holderHex,
        String peerAddress,
        List<String> seederEndpoints) {
      this.description = description;
      this.fields = List.copyOf(fields);
      this.magnet = magnet;
      this.infoHash = infoHash;
      this.holderPub = holderPub == null ? null : holderPub.clone();
      this.holderHex = holderHex;
      this.peerAddress = peerAddress;
      this.seederEndpoints = List.copyOf(seederEndpoints);
    }
  }
}
