/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.PlaybackUtil;
import java.io.File;
import org.junit.jupiter.api.Test;

class LibraryActionsHolderTest {

  /**
   * The Library's actions column asks isPlayable() for every painted row. It used to call
   * File.exists() and isDirectory() there, on the EDT (Icebase #d93320bdc558).
   */
  @Test
  void isPlayableReturnsTheDecidedValueWithoutCheckingTheDisk() {
    File missing = new File("this/path/does/not/exist/song.mp3");
    LibraryActionsHolder holder =
        new LibraryActionsHolder(null, false, PlaybackUtil.isPlayableFile(missing.getName()));

    assertTrue(holder.isPlayable(), "decided by name, no disk access");
    holder.setPlayable(PlaybackUtil.isPlayableFile(missing));
    assertFalse(holder.isPlayable(), "the background check found no file");
  }

  @Test
  void nameBasedGuessOnlyAcceptsPlayableExtensions() {
    assertTrue(PlaybackUtil.isPlayableFile("song.MP3".toLowerCase()));
    assertFalse(PlaybackUtil.isPlayableFile("notes.txt"));
  }
}
