/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Pieces hive redraws on every third refresh in Lite, every one in Full (#1555). */
class RefreshCadenceTest {
  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
  }

  @Test
  void fullUpdatesThePiecesOnEveryTick() {
    assertEquals(9, updatesIn(9));
  }

  @Test
  void liteUpdatesThePiecesOnEveryThirdTick() {
    PerformanceMode.resolve(Choice.LITE, null);
    assertEquals(3, updatesIn(9));
  }

  @Test
  void theOtherPanelsUpdateOnEveryTickInLite() {
    PerformanceMode.resolve(Choice.LITE, null);
    RefreshCadence cadence = new RefreshCadence();
    for (int i = 0; i < 5; i++) {
      assertTrue(cadence.due(1));
    }
  }

  @Test
  void theDetailRefreshAsksThePanelAndThePiecesPanelAsksTheCadence() throws Exception {
    String component = source("TransferDetailComponent.java");
    assertTrue(
        component.contains("&&refreshCadence.due(currentComponent.refreshEveryTicks())){"),
        "the detail refresh doesn't ask the panel how often to update");
    String pieces = source("TransferDetailPieces.java");
    assertTrue(
        pieces.contains("publicintrefreshEveryTicks(){returnRefreshCadence.piecesEveryTicks();}"),
        "the Pieces panel updates on every tick");
  }

  private static int updatesIn(int ticks) {
    RefreshCadence cadence = new RefreshCadence();
    int updates = 0;
    for (int i = 0; i < ticks; i++) {
      if (cadence.due(RefreshCadence.piecesEveryTicks())) {
        updates++;
      }
    }
    return updates;
  }

  private static String source(String file) throws Exception {
    return Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/frostwire/gui/components/transfers")
                .resolve(file))
        .replaceAll("\\s+", "");
  }
}
