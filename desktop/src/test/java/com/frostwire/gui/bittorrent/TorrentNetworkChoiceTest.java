/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.TorrentNetworkMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1304: the per-torrent network choice's text and combo box, tested without a
 * display.
 */
class TorrentNetworkChoiceTest {

  @Test
  void theDefaultIsFirstAndEveryModeIsOffered() {
    assertEquals(TorrentNetworkMode.DEFAULT, TorrentNetworkChoice.MODES.get(0));
    assertEquals(Set.of(TorrentNetworkMode.values()), new HashSet<>(TorrentNetworkChoice.MODES));
  }

  @Test
  void everyChoiceHasADistinctLabelAndAPlainTextTooltip() {
    Set<String> labels = new HashSet<>();
    for (TorrentNetworkMode mode : TorrentNetworkChoice.MODES) {
      labels.add(TorrentNetworkChoice.label(mode));
      for (String text :
          List.of(TorrentNetworkChoice.label(mode), TorrentNetworkChoice.tooltip(mode))) {
        assertFalse(text.isBlank());
        assertFalse(text.toLowerCase().contains("<html"), "tooltips are never HTML: " + text);
        assertFalse(text.contains("<"), text);
      }
    }
    assertEquals(TorrentNetworkChoice.MODES.size(), labels.size());
    assertEquals(
        TorrentNetworkChoice.label(TorrentNetworkMode.MIXED), TorrentNetworkChoice.label(null));
  }

  @Test
  void clearnetOnlyAdmitsItIsBestEffortAndI2pOnlyNamesItsCost() {
    assertTrue(
        TorrentNetworkChoice.tooltip(TorrentNetworkMode.CLEARNET_ONLY).contains("Best effort"));
    String i2p = TorrentNetworkChoice.tooltip(TorrentNetworkMode.I2P_ONLY);
    assertTrue(i2p.contains("mixed mode is off"), i2p);
    assertTrue(i2p.contains("web seeds"), i2p);
  }

  @Test
  void theHoldTextNamesWhatToFixAndIsEmptyWithoutAHold() {
    assertEquals("", TorrentNetworkChoice.holdText(null));
    String disabled = TorrentNetworkChoice.holdText(BTEngine.NetworkHold.I2P_NOT_ENABLED);
    String unreachable = TorrentNetworkChoice.holdText(BTEngine.NetworkHold.BRIDGE_NOT_USABLE);
    assertTrue(disabled.contains("not enabled"), disabled);
    assertTrue(unreachable.contains("not reachable"), unreachable);
    assertFalse(disabled.equals(unreachable));
    assertFalse(disabled.contains("<") || unreachable.contains("<"));
  }

  @Test
  void theComboTellsItsListenerOnlyWhatTheUserChose() {
    TorrentNetworkChoice.NetworkCombo combo = new TorrentNetworkChoice.NetworkCombo();
    List<TorrentNetworkMode> heard = new ArrayList<>();
    combo.onUserChange(heard::add);
    assertEquals(TorrentNetworkMode.MIXED, combo.getMode());

    combo.setModeQuietly(TorrentNetworkMode.I2P_ONLY); // a refresh from the engine
    assertEquals(TorrentNetworkMode.I2P_ONLY, combo.getMode());
    assertTrue(heard.isEmpty(), "a refresh is not a choice");
    assertEquals(TorrentNetworkChoice.tooltip(TorrentNetworkMode.I2P_ONLY), combo.getToolTipText());

    combo.setSelectedItem(TorrentNetworkMode.CLEARNET_ONLY); // the user
    assertEquals(List.of(TorrentNetworkMode.CLEARNET_ONLY), heard);

    combo.setModeQuietly(null);
    assertEquals(TorrentNetworkMode.MIXED, combo.getMode());
    assertEquals(1, heard.size());
  }
}
