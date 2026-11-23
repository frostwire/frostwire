/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import com.frostwire.gui.theme.PlatformFonts;
import com.limegroup.gnutella.gui.LanguageUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.UIDefaults;

/**
 * Font work the EDT still did on first use after {@link JdkWarmUp}'s text steps
 * (frostwire/frostwire #1562): the derived fonts the UI creates (bold captions, the splash status
 * label, the slide overlay), their font faces and strikes at the sizes the app really uses,
 * clipping text with supplementary characters, and the language names the setup wizard lists in
 * their own scripts. Each step is best effort and runs off the EDT, from {@link JdkWarmUp}.
 */
final class FontWarmUps {

  /** Printable text with supplementary characters: an emoji, a CJK extension B ideograph. */
  static final String SUPPLEMENTARY_SAMPLE =
      "FrostWire "
          + new String(Character.toChars(0x1F600))
          + " "
          + new String(Character.toChars(0x20000))
          + " "
          + new String(Character.toChars(0x1D538));

  /** What the app derives from a UI font: bold (captions, matches), italic, and both. */
  static final int[] DERIVED_STYLES = {Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC};

  /** The UI defaults, besides the text fonts, whose fonts components paint text with. */
  private static final String[] EXTRA_FONT_KEYS = {
    "TableHeader.font", "List.font", "Button.font", "ToggleButton.font"
  };

  private FontWarmUps() {}

  /**
   * Fonts the app derives from the UI's text fonts: bold and italic of each ({@code
   * GUIUtils.setBoldText}, {@code MatchHighlightingCellRenderer}, {@code CheckBoxList}), the splash
   * status label's bold 9 pt ({@code StatusComponent}), the slide overlay's sizes ({@code
   * SlideControlsOverlay}: -3, +3 and bold +6) and the library search field's +2.
   */
  static Set<Font> derivedUiFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>();
    for (Font base : uiFonts(defaults)) {
      for (int style : DERIVED_STYLES) {
        fonts.add(base.deriveFont(style));
      }
      float size = base.getSize2D();
      fonts.add(base.deriveFont(Font.BOLD).deriveFont(9f));
      fonts.add(base.deriveFont(size - 3f));
      fonts.add(base.deriveFont(size + 2f));
      fonts.add(base.deriveFont(size + 3f));
      fonts.add(base.deriveFont(Font.BOLD).deriveFont(size + 6f));
    }
    return fonts;
  }

  /**
   * The {@link PlatformFonts} the app sets directly, in the styles and sizes of its call sites
   * (dialog titles and text, the language pickers, the share and partial files dialogs, the
   * Transfers tab, the options panes), besides {@link JdkWarmUp#mainWindowPlatformFonts()}.
   */
  static List<Font> platformFontSizes() {
    List<Font> fonts = new ArrayList<>();
    for (int size : new int[] {11, 14, 16}) {
      fonts.add(PlatformFonts.dialog(Font.PLAIN, size));
    }
    for (int size : new int[] {13, 14, 16, 18}) {
      fonts.add(PlatformFonts.dialog(Font.BOLD, size));
    }
    fonts.add(PlatformFonts.sansSerif(Font.PLAIN, 11));
    fonts.add(PlatformFonts.sansSerif(Font.BOLD, 14));
    fonts.add(PlatformFonts.sansSerif(Font.BOLD, 16));
    return fonts;
  }

  /**
   * Creates the derived fonts and loads each one's face: {@code Font.deriveFont} of a logical font
   * builds a new composite font in the font manager, and measuring bold text opens the bold font
   * file, both on the EDT before (Icebase #c8a8c0cef552, #6beb0df3f63c, #a659e9bf11b5). Cheap: no
   * glyphs are rasterized. Returns how many fonts it loaded.
   */
  static int loadDerivedFaces(UIDefaults defaults) {
    Set<Font> fonts = derivedUiFonts(defaults);
    fonts.addAll(platformFontSizes());
    Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      for (Font font : fonts) {
        g.getFontMetrics(font).getAscent();
      }
    } finally {
      g.dispose();
    }
    return fonts.size();
  }

  /**
   * Rasterizes printable ASCII in every font of {@link #derivedUiFonts} and {@link
   * #platformFontSizes} that {@link JdkWarmUp}'s other steps do not, so a bold match, a dialog
   * title or the donation button's first paint finds its glyphs (Icebase #7fbda408ad3e,
   * #339923b2cb0e, #e57a4792eb60). Returns how many fonts it rasterized.
   */
  static int rasterizeVariants(UIDefaults defaults, Map<?, ?> desktopHints) {
    Set<Font> fonts = new LinkedHashSet<>(derivedUiFonts(defaults));
    fonts.addAll(platformFontSizes());
    fonts.removeAll(JdkWarmUp.textFonts(defaults));
    fonts.removeAll(JdkWarmUp.mainWindowPlatformFonts());
    for (Font font : fonts) {
      JdkWarmUp.rasterizeGlyphs(font, desktopHints, JdkWarmUp.GLYPH_SAMPLE);
    }
    return fonts.size();
  }

  /**
   * Clips text with supplementary characters the way Swing clips a table cell
   * (SwingUtilities2.clipString), and asks each UI font whether it can display it and the scripts'
   * sample ({@code ThemeMediator.canDisplayMessage}). A supplementary character no UI font maps
   * makes the JDK's FontResolver open every installed font to see which has supplementary
   * characters (TrueTypeFont.hasSupplementaryChars, CMap.initialize), which the Partial Files
   * dialog's first clipped label did on the EDT (Icebase #f38712501364, #db6adc722046), and
   * checking a message opens deferred composite font slots (Icebase #9b2f277b99e5).
   */
  static void warmUpClipping(UIDefaults defaults) {
    new LineBreakMeasurer(
            new AttributedString(SUPPLEMENTARY_SAMPLE).getIterator(),
            BreakIterator.getCharacterInstance(),
            new FontRenderContext(null, true, true))
        .nextOffset(50);
    Set<Font> fonts = new LinkedHashSet<>(JdkWarmUp.textFonts(defaults));
    fonts.addAll(derivedUiFonts(defaults));
    for (Font font : fonts) {
      font.canDisplayUpTo(SUPPLEMENTARY_SAMPLE);
      font.canDisplayUpTo(JdkWarmUp.SCRIPT_GLYPH_SAMPLE);
    }
  }

  /** What the wizard's language list shows for a locale: its name, in its language. */
  static String languageName(Locale locale) {
    String name = locale.getDisplayName(locale);
    return name.isEmpty()
        ? name
        : name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
  }

  /**
   * Lays out, checks and rasterizes each language name in each font: the setup wizard's list
   * (LanguageFlagFactory.LocaleRenderer) shows every name in its own script, which needs text
   * layout (TextLayout.paragraphInit) and the fallback fonts' strikes (Icebase #0fe29c35f82e,
   * #d96d01bc3b44, #0cc3790765c3).
   */
  static void warmUpLanguageNames(
      List<String> names, Iterable<Font> fonts, Map<?, ?> desktopHints) {
    FontRenderContext frc = new FontRenderContext(null, true, true);
    String all = String.join(" ", names);
    for (Font font : fonts) {
      for (String name : names) {
        if (!name.isEmpty()) {
          new TextLayout(name, font, frc);
        }
        font.canDisplayUpTo(name);
      }
      JdkWarmUp.rasterizeGlyphs(font, desktopHints, all);
    }
  }

  /** The wizard's list fonts: the list's, the label's and the picker's. */
  static Set<Font> languageListFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>();
    for (String key : new String[] {"List.font", "Label.font"}) {
      Font font = defaults.getFont(key);
      if (font != null) {
        fonts.add(font);
      }
    }
    fonts.add(LanguageUtils.LANGUAGE_PICKER_FONT);
    return fonts;
  }

  /**
   * The wizard's language names on a first run (nothing installed yet), none otherwise: it is the
   * only time the list shows, so later launches skip the work.
   */
  static List<String> languageNamesOnFirstRun() {
    if (ApplicationSettings.INSTALLED.getValue()) {
      return List.of();
    }
    LanguageUtils.preloadLocales();
    List<String> names = new ArrayList<>();
    for (Locale locale : LanguageUtils.getLocales(null)) {
      names.add(languageName(locale));
    }
    return names;
  }

  private static Set<Font> uiFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>(JdkWarmUp.textFonts(defaults));
    for (String key : EXTRA_FONT_KEYS) {
      Font font = defaults.getFont(key);
      if (font != null) {
        fonts.add(font);
      }
    }
    return fonts;
  }
}
