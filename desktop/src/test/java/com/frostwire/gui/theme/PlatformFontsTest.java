/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import org.junit.jupiter.api.Test;

class PlatformFontsTest {

  @Test
  void sansSerifIsHelveticaOnlyOnMacOS() {
    assertEquals("Helvetica", PlatformFonts.sansSerifFamily(true));
    // Windows has no Helvetica: asking for it made the JDK register every installed font file.
    assertEquals(Font.DIALOG, PlatformFonts.sansSerifFamily(false));
  }

  @Test
  void sansSerifKeepsStyleAndSize() {
    Font font = PlatformFonts.sansSerif(Font.BOLD, 13);
    assertEquals(Font.BOLD, font.getStyle());
    assertEquals(13, font.getSize());
  }
}
