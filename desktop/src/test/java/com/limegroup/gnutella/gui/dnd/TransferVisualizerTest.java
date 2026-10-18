/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.dnd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransferVisualizerTest {

  @Test
  void dragImageRowsAreCappedNoMatterHowManyFilesAreDragged() {
    assertEquals(1, TransferVisualizer.rowCount(1));
    assertEquals(
        TransferVisualizer.MAX_IMAGE_ROWS,
        TransferVisualizer.rowCount(TransferVisualizer.MAX_IMAGE_ROWS));
    assertEquals(TransferVisualizer.MAX_IMAGE_ROWS, TransferVisualizer.rowCount(5_000));
  }

  @Test
  void onlyTheLastRowMentionsTheFilesNotDrawn() {
    int rows = TransferVisualizer.MAX_IMAGE_ROWS;

    assertEquals("a.mp3", TransferVisualizer.rowText("a.mp3", 0, rows, 100));
    String last = TransferVisualizer.rowText("z.mp3", rows - 1, rows, 100);
    assertTrue(last.startsWith("z.mp3 "));
    assertTrue(last.contains(String.valueOf(100 - rows)));
  }

  @Test
  void noExtraTextWhenEveryFileIsDrawn() {
    assertEquals("only.mp3", TransferVisualizer.rowText("only.mp3", 0, 1, 1));
  }
}
