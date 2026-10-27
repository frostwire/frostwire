/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;

/**
 * MainFrame.saveWindowState snapped any window smaller than the 1200x700 defaults back to 1200x700
 * from inside the resize event, fighting the user's drag and oversizing the window on small or
 * high-scale screens. The size the user chose is what gets saved.
 */
class MainFrameWindowSizeTest {

  @Test
  void aWindowSmallerThanTheDefaultsKeepsItsSize() {
    assertEquals(new Dimension(900, 620), MainFrame.sizeToSave(new Dimension(900, 620)));
    assertEquals(new Dimension(1199, 699), MainFrame.sizeToSave(new Dimension(1199, 699)));
  }

  @Test
  void aWindowLargerThanTheDefaultsKeepsItsSize() {
    assertEquals(new Dimension(1600, 1000), MainFrame.sizeToSave(new Dimension(1600, 1000)));
  }

  @Test
  void anUnrestorableSizeIsNotSaved() {
    assertNull(MainFrame.sizeToSave(new Dimension(0, 0)));
    assertNull(MainFrame.sizeToSave(new Dimension(100, 700)));
    assertNull(MainFrame.sizeToSave(new Dimension(1200, 100)));
  }

  @Test
  void minimumWindowSizeIsTheFloorOnALargeScreen() {
    assertEquals(
        new Dimension(1000, 650),
        MainFrame.minimumWindowSize(new Dimension(640, 480), new Dimension(2560, 1400)));
  }

  @Test
  void minimumWindowSizeIsCappedToASmallScreen() {
    // 1366x768 at 150% scaling, minus the taskbar: about 910x480 logical pixels.
    assertEquals(
        new Dimension(910, 480),
        MainFrame.minimumWindowSize(new Dimension(640, 480), new Dimension(910, 480)));
  }

  @Test
  void largerContentRaisesTheFloorButNeverPastTheScreen() {
    assertEquals(
        new Dimension(1100, 700),
        MainFrame.minimumWindowSize(new Dimension(1100, 700), new Dimension(2560, 1400)));
    assertEquals(
        new Dimension(1050, 680),
        MainFrame.minimumWindowSize(new Dimension(1100, 700), new Dimension(1050, 680)));
  }

  @Test
  void unknownScreenUsesTheFloor() {
    assertEquals(new Dimension(1000, 650), MainFrame.minimumWindowSize(new Dimension(), null));
  }
}
