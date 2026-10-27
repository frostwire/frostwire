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
}
