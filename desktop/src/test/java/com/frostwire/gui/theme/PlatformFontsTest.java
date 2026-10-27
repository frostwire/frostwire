/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Font;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformFontsTest {

  @Test
  void sansSerifIsHelveticaOnlyOnMacOS() {
    assertEquals("Helvetica", PlatformFonts.sansSerifFamily(true));
    // Windows has no Helvetica: asking for it made the JDK register every installed font file.
    assertEquals(Font.DIALOG, PlatformFonts.sansSerifFamily(false));
  }

  /**
   * A per-language Windows font the system lacks must not be requested: Windows 10 and 11 ship
   * Gisha, Leelawadee and Meiryo only as optional supplemental fonts, and resolving a missing
   * family made the JDK scan every installed font file on the EDT (Icebase #e8674f3cd594, GitHub
   * #1329).
   */
  @Test
  void windowsUIFamilyIsNullWhenTheRecommendedFamilyIsMissing() {
    Set<String> windows10WithoutSupplementalFonts =
        Set.of("segoe ui", "malgun gothic", "microsoft jhenghei", "leelawadee ui", "dialog");

    assertNull(PlatformFonts.windowsUIFamily("th", windows10WithoutSupplementalFonts));
    assertNull(PlatformFonts.windowsUIFamily("he", windows10WithoutSupplementalFonts));
    assertNull(PlatformFonts.windowsUIFamily("ja", windows10WithoutSupplementalFonts));
    assertEquals(
        "Malgun Gothic", PlatformFonts.windowsUIFamily("ko", windows10WithoutSupplementalFonts));
    assertEquals(
        "Microsoft JhengHei",
        PlatformFonts.windowsUIFamily("zh_CN", windows10WithoutSupplementalFonts));
  }

  @Test
  void windowsUIFamilyKeepsInstalledRecommendedFamilies() {
    Set<String> windows7 = Set.of("meiryo", "gisha", "leelawadee", "segoe ui");

    assertEquals("Meiryo", PlatformFonts.windowsUIFamily("ja", windows7));
    assertEquals("Gisha", PlatformFonts.windowsUIFamily("he", windows7));
    assertEquals("Leelawadee", PlatformFonts.windowsUIFamily("th", windows7));
  }

  @Test
  void languagesWithoutARecommendedFamilyKeepTheLookAndFeelFont() {
    assertNull(PlatformFonts.windowsUIFamily("en", Set.of("meiryo")));
    assertNull(PlatformFonts.windowsUIFamily(null, Set.of("meiryo")));
    // No installed-family lookup at all for them.
    assertNull(PlatformFonts.windowsUIFamily("es"));
  }

  @Test
  void sansSerifKeepsStyleAndSize() {
    Font font = PlatformFonts.sansSerif(Font.BOLD, 13);
    assertEquals(Font.BOLD, font.getStyle());
    assertEquals(13, font.getSize());
  }
}
