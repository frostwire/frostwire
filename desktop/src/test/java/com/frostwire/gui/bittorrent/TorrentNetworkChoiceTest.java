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
  void aModeChosenInAnAddDialogIsRecordedBeforeTheAddAndTheDefaultIsNot() {
    List<String> recorded = new ArrayList<>();
    TorrentNetworkChoice.ModeSink sink =
        (hash, mode) -> {
          recorded.add(hash + "=" + mode);
          return true;
        };
    String hash = "0123456789abcdef0123456789abcdef01234567";

    assertFalse(TorrentNetworkChoice.recordBeforeAdd(hash, TorrentNetworkMode.MIXED, sink));
    assertFalse(TorrentNetworkChoice.recordBeforeAdd(hash, null, sink));
    assertFalse(TorrentNetworkChoice.recordBeforeAdd(null, TorrentNetworkMode.I2P_ONLY, sink));
    assertTrue(recorded.isEmpty(), "nothing is written for the default or without a hash");

    assertTrue(TorrentNetworkChoice.recordBeforeAdd(hash, TorrentNetworkMode.I2P_ONLY, sink));
    assertEquals(List.of(hash + "=I2P_ONLY"), recorded);
    assertFalse(
        TorrentNetworkChoice.recordBeforeAdd(
            hash, TorrentNetworkMode.CLEARNET_ONLY, (h, m) -> false),
        "a refused record is reported");
  }

  @Test
  void bothAddPathsRecordTheModeBeforeTheyAddTheTorrent() throws Exception {
    java.nio.file.Path dir =
        java.nio.file.Path.of(
            System.getProperty("user.dir"), "src/main/java/com/frostwire/gui/bittorrent");
    for (String file : List.of("TorrentFetcherDownload.java", "BTDownloadMediator.java")) {
      String source = java.nio.file.Files.readString(dir.resolve(file)).replaceAll("\\s+", "");
      int record = source.indexOf("TorrentNetworkChoice.recordBeforeAdd(");
      assertTrue(record > 0, file + " never records the mode");
      assertTrue(
          source.indexOf("engine.download(", record) > record,
          file + " must record the mode before it adds the torrent");
      assertTrue(source.contains("dlg.getNetworkMode()"), file);
    }
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
