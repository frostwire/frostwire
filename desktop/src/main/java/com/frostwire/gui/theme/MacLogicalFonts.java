/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Restores the JDK's logical fonts on a Mac where they were never registered.
 *
 * <p>On macOS the JDK builds the logical fonts Dialog, SansSerif and DialogInput as copies of the
 * Lucida Grande family ({@code CFontManager.loadFonts}). When that family is missing or disabled
 * (in Font Book, or by a font cleanup tool), they are silently left out, and from then on any font
 * the JDK cannot find by name falls back to a null logical font: {@code Font.getFont2D} throws a
 * NullPointerException the first time text is measured. FrostWire crashed while laying out its
 * splash screen (Icebase #36017b212d11, frostwire/frostwire#1484), and every Swing component, JDK
 * dialog and HTML text that uses a logical font would follow.
 *
 * <p>{@link #repairIfMissing()} builds those logical fonts again with the JDK's own routine ({@code
 * CFontManager.setupLogicalFonts}), this time from an installed family. It needs the JVM flag
 * {@code --add-opens=java.desktop/sun.font=ALL-UNNAMED}; without it, FrostWire's own fonts still
 * avoid the logical names ({@link PlatformFonts#dialog}).
 */
public final class MacLogicalFonts {
  private static final Logger LOG = Logger.getLogger(MacLogicalFonts.class);

  /** {@code sun.font.FontManager.NO_FALLBACK}: find the font itself, never a fallback. */
  private static final int NO_FALLBACK = 0;

  /** The logical fonts the JDK builds from Lucida Grande on macOS. */
  static final List<String> LUCIDA_GRANDE_LOGICAL_FONTS =
      List.of("Dialog", "SansSerif", "DialogInput");

  private MacLogicalFonts() {}

  /**
   * On macOS, makes sure the logical Dialog font exists, building it (and SansSerif and
   * DialogInput) from the first installed {@link PlatformFonts#MAC_DIALOG_SUBSTITUTES} family when
   * the JDK could not. Call it on the startup thread before any text is measured; elsewhere it does
   * nothing.
   *
   * @return whether the logical Dialog font is usable afterwards; false also when the check is not
   *     possible (the JVM was started without access to {@code sun.font})
   */
  public static boolean repairIfMissing() {
    if (!OSUtils.isMacOSX()) {
      return true;
    }
    try {
      Object fontManager =
          Class.forName("sun.font.FontManagerFactory").getMethod("getInstance").invoke(null);
      Method findFont2D =
          Class.forName("sun.font.FontManager")
              .getMethod("findFont2D", String.class, int.class, int.class);
      if (dialogExists(fontManager, findFont2D)) {
        return true;
      }
      Method setupLogicalFonts =
          fontManager
              .getClass()
              .getDeclaredMethod("setupLogicalFonts", String.class, String.class, String.class);
      setupLogicalFonts.setAccessible(true);
      for (String family : PlatformFonts.MAC_DIALOG_SUBSTITUTES) {
        for (String logical : LUCIDA_GRANDE_LOGICAL_FONTS) {
          setupLogicalFonts.invoke(fontManager, logical, family, family);
        }
        if (dialogExists(fontManager, findFont2D)) {
          LOG.warn(
              "The "
                  + PlatformFonts.MAC_LOGICAL_FONT_BASE
                  + " font is missing or disabled; built the logical fonts from "
                  + family);
          return true;
        }
      }
      LOG.error("No font to build the logical fonts from: " + PlatformFonts.MAC_DIALOG_SUBSTITUTES);
      return false;
    } catch (ReflectiveOperationException | RuntimeException e) {
      LOG.warn("Could not check the macOS logical fonts: " + e);
      return false;
    }
  }

  private static boolean dialogExists(Object fontManager, Method findFont2D)
      throws ReflectiveOperationException {
    return findFont2D.invoke(fontManager, Font.DIALOG, Font.PLAIN, NO_FALLBACK) != null;
  }
}
