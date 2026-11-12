/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.OSUtils;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fonts by family name that are installed wherever FrostWire runs.
 *
 * <p>Asking for a family the system does not have does not fail fast: the first time text in that
 * font is measured, the JDK opens and registers every file in the system font folders looking for
 * it (hundreds on Windows, seconds on a slow disk), then falls back to Dialog. Laying out the
 * Transfers tab, whose buttons asked for Helvetica, did that on the EDT on Windows. Use these
 * instead of physical family names; logical names ({@link Font#DIALOG}, {@link Font#SANS_SERIF},
 * {@link Font#MONOSPACED}...) are always safe.
 */
public final class PlatformFonts {

  /** Ships with every macOS; Windows and most Linux installs do not have it. */
  static final String MAC_SANS_SERIF = "Helvetica";

  /**
   * The family the JDK builds the macOS logical fonts (Dialog, SansSerif, DialogInput) from. When
   * it is missing or disabled (in Font Book, or by a font cleanup tool), those logical fonts are
   * never registered, and measuring text in any font the JDK has to fall back for throws a
   * NullPointerException in Font.getFont2D (frostwire/frostwire#1484).
   */
  static final String MAC_LOGICAL_FONT_BASE = "Lucida Grande";

  /**
   * Physical families used for FrostWire's own fonts on a Mac without {@link
   * #MAC_LOGICAL_FONT_BASE}.
   */
  static final List<String> MAC_DIALOG_SUBSTITUTES =
      List.of("Helvetica Neue", "Helvetica", "Arial");

  /**
   * The donation buttons' font, bold 12 pt Dialog. The startup warm-up rasterizes it (JdkWarmUp),
   * or the main window's first paint did that on the EDT (frostwire/frostwire#1467).
   */
  public static final Font DIALOG_BOLD = dialog(Font.BOLD, 12);

  /**
   * The transfer details' "hide" link, plain 13 pt sans serif: the only text at that size, so a
   * strike of its own. The startup warm-up rasterizes it (JdkWarmUp), or building the transfer
   * details the first time did that on the EDT (frostwire/frostwire#1537).
   */
  public static final Font TRANSFER_DETAIL_LINK = sansSerif(Font.PLAIN, 13);

  private PlatformFonts() {}

  /**
   * FrostWire's general UI font: the logical Dialog font, or on a Mac whose logical fonts are
   * unusable (see {@link #MAC_LOGICAL_FONT_BASE}), an installed physical family. Use this instead
   * of {@code new Font(Font.DIALOG, ...)}.
   */
  public static Font dialog(int style, int size) {
    return new Font(DialogFamily.NAME, style, size);
  }

  /**
   * The search fields' font: the text field font 2 pt larger. GoogleSearchField paints with it and
   * JdkWarmUp rasterizes its glyphs at startup, so both must derive it here
   * (frostwire/frostwire#1490).
   */
  public static Font searchField(Font textFieldFont) {
    return textFieldFont.deriveFont(textFieldFont.getSize2D() + 2f);
  }

  /**
   * Whether FrostWire uses a physical family in place of Dialog, because the Mac lacks its base.
   */
  public static boolean dialogIsSubstituted() {
    return !Font.DIALOG.equals(DialogFamily.NAME);
  }

  /**
   * {@link Font#DIALOG}, unless this is a Mac without {@link #MAC_LOGICAL_FONT_BASE}: then the
   * first installed {@link #MAC_DIALOG_SUBSTITUTES} family (Dialog if none is installed either).
   */
  static String dialogFamily(boolean macOS, Set<String> installedLowerCase) {
    if (!macOS || installedLowerCase.contains(MAC_LOGICAL_FONT_BASE.toLowerCase(Locale.ROOT))) {
      return Font.DIALOG;
    }
    for (String family : MAC_DIALOG_SUBSTITUTES) {
      if (installedLowerCase.contains(family.toLowerCase(Locale.ROOT))) {
        return family;
      }
    }
    return Font.DIALOG;
  }

  /** The family {@link #dialog} uses; the installed families are only read on macOS. */
  private static final class DialogFamily {
    static final String NAME =
        OSUtils.isMacOSX() ? dialogFamily(true, InstalledFamilies.LOWER_CASE) : Font.DIALOG;
  }

  /** Helvetica on macOS, the logical Dialog font (also sans serif) everywhere else. */
  public static Font sansSerif(int style, int size) {
    return new Font(sansSerifFamily(OSUtils.isMacOSX()), style, size);
  }

  static String sansSerifFamily(boolean macOS) {
    return macOS ? MAC_SANS_SERIF : Font.DIALOG;
  }

  /**
   * The Windows UI font family recommended for the language, or null. These are physical families
   * and not every Windows has them: Windows 10 and 11 ship Gisha, Leelawadee and Meiryo only as
   * optional supplemental fonts. Use {@link #windowsUIFamily(String)} to pick one.
   */
  static String recommendedFamily(String language) {
    if (language == null) {
      return null;
    } else if (language.startsWith("ja")) {
      return "Meiryo";
    } else if (language.startsWith("ko")) {
      return "Malgun Gothic";
    } else if (language.startsWith("zh")) {
      // Microsoft JhengHei for Chinese (Traditional), Microsoft YaHei for Chinese (Simplified)
      return "Microsoft JhengHei";
    } else if (language.startsWith("he")) {
      return "Gisha";
    } else if (language.startsWith("th")) {
      return "Leelawadee";
    }
    return null;
  }

  /**
   * The recommended Windows UI font family for the language if this system has it, else null, which
   * keeps the look and feel's logical font: its fallback fonts cover these scripts, and it is what
   * the JDK falls back to for a missing family anyway, after the scan. The installed families are
   * read once, on the first call for such a language; at startup that is the thread installing the
   * look and feel, not the EDT, and reading them also builds the JDK's map of platform font files
   * that resolving the family needs.
   */
  public static String windowsUIFamily(String language) {
    return recommendedFamily(language) == null
        ? null
        : windowsUIFamily(language, InstalledFamilies.LOWER_CASE);
  }

  static String windowsUIFamily(String language, Set<String> installedLowerCase) {
    String family = recommendedFamily(language);
    return family != null && installedLowerCase.contains(family.toLowerCase(Locale.ROOT))
        ? family
        : null;
  }

  /** The system's font family names, lower case, read on first use. */
  private static final class InstalledFamilies {
    static final Set<String> LOWER_CASE =
        Arrays.stream(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
  }
}
