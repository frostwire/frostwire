/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.TorrentNetworkMode;
import com.frostwire.gui.bittorrent.TorrentNetworkChoice;
import com.limegroup.gnutella.gui.SizeStableLabel;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The "Network" control of Transfer Details (frostwire/frostwire#1304): Mixed, I2P only or Clearnet
 * only, and the reason when the engine holds an I2P only torrent back. It never calls the engine:
 * the panel reads the mode off the EDT and hands it to {@link #show}, and the user's choice reaches
 * {@link #onUserChange}, which the panel runs on a background thread.
 */
final class TransferNetworkRow extends JPanel {
  private final TorrentNetworkChoice.NetworkCombo combo = new TorrentNetworkChoice.NetworkCombo();
  private final JLabel holdLabel = new SizeStableLabel();

  TransferNetworkRow() {
    super(new FlowLayout(FlowLayout.LEFT, 5, 0));
    setOpaque(false);
    add(combo);
    add(holdLabel);
    combo.setEnabled(false);
  }

  /** The user's choice (not a refresh) goes to {@code listener}, on the EDT. */
  void onUserChange(Consumer<TorrentNetworkMode> listener) {
    combo.onUserChange(listener);
  }

  /** Shows the torrent's mode and why it is held, if it is. EDT. */
  void show(TorrentNetworkMode mode, BTEngine.NetworkHold hold) {
    combo.setModeQuietly(mode);
    combo.setEnabled(true);
    holdLabel.setText(TorrentNetworkChoice.holdText(hold));
  }

  /** No torrent to show (or none readable): nothing chosen, nothing to change. EDT. */
  void clear() {
    combo.setModeQuietly(TorrentNetworkMode.DEFAULT);
    combo.setEnabled(false);
    holdLabel.setText("");
  }

  TorrentNetworkMode mode() {
    return combo.getMode();
  }

  boolean isChoiceEnabled() {
    return combo.isEnabled();
  }

  String holdText() {
    return holdLabel.getText();
  }
}
