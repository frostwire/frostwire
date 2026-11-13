/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Create New Torrent had a fixed preferred size of 942 x 750, so pack() ignored what its grown
 * content needed and the progress bar and the Save and Close buttons were cut off until the user
 * resized it (frostwire/frostwire#1549).
 */
class CreateTorrentDialogSizeTest {
  private static final Dimension MINIMUM = new Dimension(942, 750);
  private static final Rectangle SCREEN = new Rectangle(0, 25, 1512, 920);

  @Test
  void contentTallerThanTheMinimumGetsItsHeight() {
    assertEquals(
        new Dimension(942, 860),
        CreateTorrentDialog.initialSize(new Dimension(900, 860), MINIMUM, SCREEN));
  }

  @Test
  void smallContentStillGetsTheMinimum() {
    assertEquals(
        MINIMUM, CreateTorrentDialog.initialSize(new Dimension(600, 500), MINIMUM, SCREEN));
  }

  @Test
  void neverLargerThanTheScreen() {
    assertEquals(
        new Dimension(1512, 920),
        CreateTorrentDialog.initialSize(new Dimension(2000, 1400), MINIMUM, SCREEN));
    assertEquals(
        new Dimension(942, 1000),
        CreateTorrentDialog.initialSize(new Dimension(900, 1000), MINIMUM, null));
  }

  @Test
  void theDialogNoLongerFixesItsPreferredSize() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"));
    assertFalse(
        source.contains("setPreferredSize(MINIMUM_DIALOG_DIMENSIONS)"),
        "a fixed preferred size makes pack() ignore the content");
  }
}
