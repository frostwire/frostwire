/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.TorrentNetworkMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1304: Transfer Details' Network row, without a display. The row never calls
 * the engine: the panel reads the mode off the EDT and shows it, and runs the user's choice on a
 * background thread.
 */
class TransferNetworkRowTest {

  @Test
  void itStartsDisabledAndMixedUntilATorrentIsShown() {
    TransferNetworkRow row = new TransferNetworkRow();

    assertFalse(row.isChoiceEnabled());
    assertEquals(TorrentNetworkMode.MIXED, row.mode());
    assertEquals("", row.holdText());
  }

  @Test
  void aTorrentIsShownWithItsModeAndTheReasonItIsHeld() {
    TransferNetworkRow row = new TransferNetworkRow();

    row.show(TorrentNetworkMode.I2P_ONLY, BTEngine.NetworkHold.I2P_NOT_ENABLED);

    assertTrue(row.isChoiceEnabled());
    assertEquals(TorrentNetworkMode.I2P_ONLY, row.mode());
    assertTrue(row.holdText().contains("not enabled"), row.holdText());

    row.show(TorrentNetworkMode.I2P_ONLY, null); // I2P came up
    assertEquals("", row.holdText());
  }

  @Test
  void aRefreshIsNotAChoiceButTheUsersPickIs() {
    TransferNetworkRow row = new TransferNetworkRow();
    List<TorrentNetworkMode> chosen = new ArrayList<>();
    row.onUserChange(chosen::add);

    row.show(TorrentNetworkMode.CLEARNET_ONLY, null);
    row.show(TorrentNetworkMode.MIXED, null);
    assertTrue(chosen.isEmpty(), "the 1 s refresh must not write the mode back");
  }

  @Test
  void clearingShowsNothingChosenAndNothingToChange() {
    TransferNetworkRow row = new TransferNetworkRow();
    row.show(TorrentNetworkMode.I2P_ONLY, BTEngine.NetworkHold.BRIDGE_NOT_USABLE);

    row.clear();

    assertFalse(row.isChoiceEnabled());
    assertEquals(TorrentNetworkMode.MIXED, row.mode());
    assertEquals("", row.holdText());
  }

  @Test
  void thePanelChangesTheModeInTheBackgroundNotOnTheEdt() throws Exception {
    String source =
        TransferDetailRevalidationTest.readSource("TransferDetailGeneral.java")
            .replaceAll("\\s+", "");

    assertTrue(
        source.contains(
            "BackgroundQueuedExecutorService.schedule(()->target.setNetworkMode(mode))"),
        "the choice must be applied on the background queue");
    assertTrue(source.contains("networkRow.onUserChange(this::onNetworkChosen)"));
    assertTrue(source.contains("networkRow.show(networkMode,networkHold)"));
  }
}
