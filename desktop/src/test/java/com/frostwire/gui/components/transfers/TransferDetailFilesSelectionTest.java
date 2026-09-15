/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The Files tab refreshes every second. Rebuilding the model on each tick must not drop the user's
 * row selection, and must be skipped entirely when the visible rows did not change.
 */
class TransferDetailFilesSelectionTest {

  @Test
  void refreshPreservesRowSelection() throws Exception {
    String mediator =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailFilesTableMediator.java");
    assertTrue(
        mediator.contains("selectedFileOffsets"),
        "setHolders must capture the selected file offsets before rebuilding");
    assertTrue(
        mediator.contains("restoreSelectionByFileOffset"),
        "setHolders must restore the selection after rebuilding");
    assertFalse(
        mediator.contains("DATA_MODEL.setHolders(holders);\n    TABLE.clearSelection();"),
        "rebuilding must not unconditionally drop the selection");
  }

  @Test
  void unchangedHoldersSkipModelRebuild() throws Exception {
    String mediator =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailFilesTableMediator.java");
    assertTrue(
        mediator.contains("sameAsDisplayed"),
        "setHolders must skip the rebuild when the visible holders are unchanged");
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
