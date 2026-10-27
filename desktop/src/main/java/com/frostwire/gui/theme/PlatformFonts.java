/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.OSUtils;
import java.awt.Font;

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

  private PlatformFonts() {}

  /** Helvetica on macOS, the logical Dialog font (also sans serif) everywhere else. */
  public static Font sansSerif(int style, int size) {
    return new Font(sansSerifFamily(OSUtils.isMacOSX()), style, size);
  }

  static String sansSerifFamily(boolean macOS) {
    return macOS ? MAC_SANS_SERIF : Font.DIALOG;
  }
}
