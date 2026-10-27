/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Program Files guard must not throw when the ProgramFiles variable is missing. */
class TorrentSaveFolderComponentTest {

  @Test
  void missingProgramFilesVariableIsNotAMatch() {
    assertFalse(TorrentSaveFolderComponent.isInsideProgramFiles("C:\\Downloads", null));
    assertFalse(TorrentSaveFolderComponent.isInsideProgramFiles("C:\\Downloads", ""));
  }

  @Test
  void folderInsideProgramFilesIsAMatchIgnoringCase() {
    assertTrue(
        TorrentSaveFolderComponent.isInsideProgramFiles(
            "c:\\program files\\FrostWire\\Torrent Data", "C:\\Program Files"));
    assertFalse(
        TorrentSaveFolderComponent.isInsideProgramFiles("D:\\Torrent Data", "C:\\Program Files"));
  }
}
