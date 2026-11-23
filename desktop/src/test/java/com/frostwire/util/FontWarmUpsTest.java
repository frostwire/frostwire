/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.theme.PlatformFonts;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.UIDefaults;
import org.junit.jupiter.api.Test;

class FontWarmUpsTest {

  private static UIDefaults defaults() {
    UIDefaults defaults = new UIDefaults();
    defaults.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    defaults.put("Table.font", new Font(Font.DIALOG, Font.PLAIN, 13));
    defaults.put("TextField.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    return defaults;
  }

  @Test
  void derivedUiFontsHaveTheVariantsTheAppDerives() {
    Set<Font> fonts = FontWarmUps.derivedUiFonts(defaults());
    Font label = new Font(Font.DIALOG, Font.PLAIN, 12);
    assertTrue(fonts.contains(label.deriveFont(Font.BOLD)), "bold captions and matches");
    assertTrue(fonts.contains(label.deriveFont(Font.BOLD).deriveFont(9f)), "splash status label");
    assertTrue(fonts.contains(label.deriveFont(9f)), "slide overlay size - 3");
    assertTrue(fonts.contains(label.deriveFont(Font.BOLD).deriveFont(18f)), "slide title");
    assertTrue(fonts.contains(label.deriveFont(Font.BOLD | Font.ITALIC)), "CheckBoxList");
  }

  @Test
  void platformFontSizesHaveTheDialogTitlesAndLanguagePicker() {
    List<Font> fonts = FontWarmUps.platformFontSizes();
    assertTrue(fonts.contains(PlatformFonts.dialog(Font.BOLD, 18)), "PartialFilesDialog title");
    assertTrue(fonts.contains(PlatformFonts.dialog(Font.PLAIN, 11)), "language picker");
  }

  @Test
  void loadDerivedFacesLoadsEveryVariant() {
    int loaded = FontWarmUps.loadDerivedFaces(defaults());
    assertTrue(loaded >= FontWarmUps.derivedUiFonts(defaults()).size());
  }

  @Test
  void rasterizeVariantsSkipsTheFontsOtherStepsRasterize() {
    UIDefaults defaults = defaults();
    int rasterized = FontWarmUps.rasterizeVariants(defaults, null);
    Set<Font> all = FontWarmUps.derivedUiFonts(defaults);
    all.addAll(FontWarmUps.platformFontSizes());
    all.removeAll(JdkWarmUp.textFonts(defaults));
    all.removeAll(JdkWarmUp.mainWindowPlatformFonts());
    assertEquals(all.size(), rasterized);
    assertTrue(rasterized > 0);
  }

  @Test
  void clippingWarmUpHandlesSupplementaryCharacters() {
    assertTrue(
        FontWarmUps.SUPPLEMENTARY_SAMPLE
            .codePoints()
            .anyMatch(Character::isSupplementaryCodePoint));
    assertDoesNotThrow(() -> FontWarmUps.warmUpClipping(defaults()));
  }

  @Test
  void languageNamesStartWithAnUppercaseLetterLikeTheWizardList() {
    assertEquals("Deutsch", FontWarmUps.languageName(Locale.GERMAN));
    assertFalse(FontWarmUps.languageName(Locale.JAPANESE).isEmpty());
  }

  @Test
  void languageNamesWarmUpLaysOutEveryName() {
    List<String> names =
        List.of(
            FontWarmUps.languageName(Locale.GERMAN),
            FontWarmUps.languageName(Locale.JAPANESE),
            FontWarmUps.languageName(Locale.forLanguageTag("ar")));
    assertDoesNotThrow(
        () -> FontWarmUps.warmUpLanguageNames(names, FontWarmUps.derivedUiFonts(defaults()), null));
  }

  @Test
  void jdkWarmUpRunsTheFontWarmUps() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/frostwire/util/JdkWarmUp.java"));
    assertTrue(source.contains("FontWarmUps.loadDerivedFaces("), "derived faces before the splash");
    assertTrue(source.contains("FontWarmUps.rasterizeVariants("), "variant strikes");
    assertTrue(source.contains("FontWarmUps.warmUpLanguageNames("), "wizard language names");
    assertTrue(source.contains("FontWarmUps.warmUpClipping("), "clipping in the font sweep");
    int sweep = source.indexOf("private static void sweepFallbackFonts()");
    assertTrue(
        source.indexOf("FontWarmUps.warmUpClipping(") > sweep, "clipping stays in the sweep");
  }
}
