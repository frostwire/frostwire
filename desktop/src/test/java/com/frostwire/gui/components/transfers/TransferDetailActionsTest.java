/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structural regression test: the transfer detail General tab offers the no-brainer per-torrent
 * actions (recheck local data, sequential download toggle) and the Trackers menu offers a validated
 * Add Tracker dialog.
 */
class TransferDetailActionsTest {

  @Test
  void generalTabOffersRecheckAndSequentialToggle() throws Exception {
    String general =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailGeneral.java");
    assertTrue(general.contains("forceRecheck"), "General tab must offer Check Local Data");
    assertTrue(
        general.contains("isSequentialDownload"), "General tab must reflect sequential state");
    assertTrue(
        general.contains("setSequentialDownload"), "General tab must toggle sequential download");
  }

  @Test
  void trackersMenuOffersValidatedAddTracker() throws Exception {
    String factory =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediatorAdvancedMenuFactory.java");
    assertTrue(factory.contains("AddTrackerAction"), "Trackers menu must offer Add Tracker");
    assertTrue(
        factory.contains("TrackerUrlValidator.isValidTrackerUrl"),
        "Add Tracker must validate with TrackerUrlValidator");
    assertTrue(
        factory.contains("TrackerUrlValidator.isValidTrackerUrl")
            && factory.contains("changeTrackers"),
        "Edit Trackers must validate with TrackerUrlValidator");
  }

  private static String readSource(String moduleRelative) throws Exception {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return Files.readString(file);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
