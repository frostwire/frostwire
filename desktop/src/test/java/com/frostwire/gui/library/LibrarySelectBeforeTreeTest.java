/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The click that first shows the Library built the explorer and asked it to select "Finished
 * Downloads" in the same event, before its tree (built in an invokeLater) existed: a
 * NullPointerException in LibraryExplorer.selectionListenerForSameItem (Icebase #b40753ec0d46,
 * build 338, Windows). The selection now waits for the tree.
 */
class LibrarySelectBeforeTreeTest {

  @Test
  void selectingBeforeTheTreeIsBuiltDoesNotThrow() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LibraryExplorer explorer = new LibraryExplorer();
          assertDoesNotThrow(explorer::selectFinishedDownloads);
          assertDoesNotThrow(() -> explorer.selectDirectoryHolderAt(0));
          assertDoesNotThrow(explorer::selectAudio);
        });
  }
}
