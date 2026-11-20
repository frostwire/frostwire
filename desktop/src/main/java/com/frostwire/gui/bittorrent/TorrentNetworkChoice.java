/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.TorrentNetworkMode;
import com.limegroup.gnutella.gui.I18n;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/**
 * The text and the combo box of the per-torrent network choice (frostwire/frostwire#1304), shared
 * by Transfer Details and the add-torrent dialog. All text is plain: tooltips are never HTML.
 *
 * <p>Nothing here touches libtorrent or the engine; the callers read and write the mode off the EDT
 * and hand the result to {@link NetworkCombo#setModeQuietly}.
 */
public final class TorrentNetworkChoice {

  /** The choices, in the order the combo shows them; the first is the default. */
  public static final List<TorrentNetworkMode> MODES =
      List.of(
          TorrentNetworkMode.MIXED, TorrentNetworkMode.I2P_ONLY, TorrentNetworkMode.CLEARNET_ONLY);

  private TorrentNetworkChoice() {}

  /** The name of the choice. I2P stays I2P in every language. */
  public static String label(TorrentNetworkMode mode) {
    switch (mode == null ? TorrentNetworkMode.DEFAULT : mode) {
      case I2P_ONLY:
        return I18n.tr("I2P only");
      case CLEARNET_ONLY:
        return I18n.tr("Clearnet only");
      default:
        return I18n.tr("Mixed");
    }
  }

  /** What the choice does, in plain text. The Clearnet only text says it is best effort. */
  public static String tooltip(TorrentNetworkMode mode) {
    switch (mode == null ? TorrentNetworkMode.DEFAULT : mode) {
      case I2P_ONLY:
        return I18n.tr(
            "Only use I2P peers. Needs I2P enabled in Options, and web seeds are not used. While any torrent is I2P only, mixed mode is off for all torrents.");
      case CLEARNET_ONLY:
        return I18n.tr(
            "Only use regular internet peers. Best effort: FrostWire does not refuse I2P peers for this torrent yet.");
      default:
        return I18n.tr("Use regular internet and I2P peers, as FrostWire always has.");
    }
  }

  /** The text shown beside the choice while the engine holds an I2P only torrent back. */
  public static String holdText(BTEngine.NetworkHold hold) {
    if (hold == null) {
      return "";
    }
    switch (hold) {
      case I2P_NOT_ENABLED:
        return I18n.tr("On hold: I2P is not enabled. Turn it on in Options > I2P.");
      default:
        return I18n.tr("On hold: the I2P router is not reachable. Check Options > I2P.");
    }
  }

  /** A combo box of {@link #MODES} that tells its listener only what the user chose. */
  public static final class NetworkCombo extends JComboBox<TorrentNetworkMode> {
    private boolean quiet;
    private Consumer<TorrentNetworkMode> onUserChange = mode -> {};

    public NetworkCombo() {
      super(MODES.toArray(new TorrentNetworkMode[0]));
      setRenderer(
          new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
              Component c = super.getListCellRendererComponent(list, value, index, selected, focus);
              if (value instanceof TorrentNetworkMode mode) {
                setText(label(mode));
                setToolTipText(tooltip(mode));
              }
              return c;
            }
          });
      addActionListener(
          e -> {
            TorrentNetworkMode mode = getMode();
            setToolTipText(tooltip(mode));
            if (!quiet) {
              onUserChange.accept(mode);
            }
          });
      setToolTipText(tooltip(getMode()));
    }

    /** The user's choice reaches {@code listener}; programmatic changes never do. */
    public void onUserChange(Consumer<TorrentNetworkMode> listener) {
      this.onUserChange = listener == null ? mode -> {} : listener;
    }

    /** The selected mode; the default when nothing is selected. */
    public TorrentNetworkMode getMode() {
      Object selected = getSelectedItem();
      return selected instanceof TorrentNetworkMode mode ? mode : TorrentNetworkMode.DEFAULT;
    }

    /** Shows {@code mode} without telling the listener (a refresh, not a choice). */
    public void setModeQuietly(TorrentNetworkMode mode) {
      quiet = true;
      try {
        setSelectedItem(mode == null ? TorrentNetworkMode.DEFAULT : mode);
      } finally {
        quiet = false;
      }
    }
  }
}
