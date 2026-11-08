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
import java.util.Map;

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

  /**
   * The PostScript names of the plain, bold, italic and bold italic faces of every family the
   * logical fonts are built from, for {@code CFontManager.registerFont} when the JDK's native font
   * listing never registered the family.
   */
  static final Map<String, List<String>> FACES =
      Map.of(
          "Helvetica Neue",
          List.of(
              "HelveticaNeue",
              "HelveticaNeue-Bold",
              "HelveticaNeue-Italic",
              "HelveticaNeue-BoldItalic"),
          "Helvetica",
          List.of("Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique"),
          "Arial",
          List.of("ArialMT", "Arial-BoldMT", "Arial-ItalicMT", "Arial-BoldItalicMT"),
          "Times",
          List.of("Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic"),
          "Menlo",
          List.of("Menlo-Regular", "Menlo-Bold", "Menlo-Italic", "Menlo-BoldItalic"));

  private MacLogicalFonts() {}

  /**
   * On macOS, makes sure the logical fonts exist: Dialog, SansSerif and DialogInput, built from the
   * first {@link PlatformFonts#MAC_DIALOG_SUBSTITUTES} family when the JDK could not build them
   * from Lucida Grande, and Serif and Monospaced, from Times and Menlo. A family the JDK did not
   * register is registered first, face by face. Call it on the startup thread before any text is
   * measured; elsewhere it does nothing.
   *
   * <p>The JDK registers the Mac's fonts one by one from a native listing ({@code
   * CFontManager.loadNativeFonts}). On macOS 27 one bad font in a fonts folder can end that listing
   * early, and every font after it, Lucida Grande and its substitutes included, is never registered
   * (frostwire/frostwire#1484).
   *
   * @return whether the logical Dialog font is usable afterwards; false also when the check is not
   *     possible (the JVM was started without access to {@code sun.font})
   */
  public static boolean repairIfMissing() {
    if (!OSUtils.isMacOSX()) {
      return true;
    }
    try {
      Fonts fonts = new Fonts();
      boolean dialog =
          fonts.repair(LUCIDA_GRANDE_LOGICAL_FONTS, PlatformFonts.MAC_DIALOG_SUBSTITUTES);
      fonts.repair(List.of("Serif"), List.of("Times"));
      fonts.repair(List.of("Monospaced"), List.of("Menlo"));
      return dialog;
    } catch (ReflectiveOperationException | RuntimeException e) {
      LOG.warn("Could not check the macOS logical fonts", e.getCause() != null ? e.getCause() : e);
      return false;
    }
  }

  /** The JDK's font manager, through the methods the repair needs. */
  private static final class Fonts {
    private final Object fontManager;
    private final Method findFont2D;
    private final Method setupLogicalFonts;
    private final Method registerFont;
    private final Method getFamily;

    Fonts() throws ReflectiveOperationException {
      fontManager =
          Class.forName("sun.font.FontManagerFactory").getMethod("getInstance").invoke(null);
      findFont2D =
          Class.forName("sun.font.FontManager")
              .getMethod("findFont2D", String.class, int.class, int.class);
      setupLogicalFonts =
          fontManager
              .getClass()
              .getDeclaredMethod("setupLogicalFonts", String.class, String.class, String.class);
      setupLogicalFonts.setAccessible(true);
      registerFont =
          fontManager.getClass().getDeclaredMethod("registerFont", String.class, String.class);
      registerFont.setAccessible(true);
      getFamily = Class.forName("sun.font.FontFamily").getMethod("getFamily", String.class);
    }

    /**
     * Builds the logical fonts, if the first is missing, from the first family that makes it exist:
     * the registered families first, then each family registered again from its faces.
     */
    boolean repair(List<String> logicalFonts, List<String> families)
        throws ReflectiveOperationException {
      String logical = logicalFonts.get(0);
      if (exists(logical)) {
        return true;
      }
      for (boolean registerFaces : new boolean[] {false, true}) {
        for (String family : families) {
          if (registerFaces) {
            for (String face : FACES.get(family)) {
              registerFont.invoke(fontManager, face, family);
            }
          } else if (!registered(family)) {
            continue;
          }
          for (String name : logicalFonts) {
            setupLogicalFonts.invoke(fontManager, name, family, family);
          }
          if (exists(logical)) {
            LOG.warn(
                "The logical "
                    + logicalFonts
                    + " fonts were missing; built them from "
                    + family
                    + (registerFaces ? ", registered by name" : ""));
            return true;
          }
        }
      }
      LOG.error("No font to build the logical " + logicalFonts + " fonts from: " + families);
      return false;
    }

    /** Whether the logical font can be found, without falling back to another font. */
    private boolean exists(String logical) throws ReflectiveOperationException {
      return findFont2D.invoke(fontManager, logical, Font.PLAIN, NO_FALLBACK) != null;
    }

    /**
     * Whether the font manager knows the family. Not a font lookup: the JDK resolves a missing
     * "Helvetica" as an alias of SansSerif, and when that is missing too, throws.
     */
    private boolean registered(String family) throws ReflectiveOperationException {
      return getFamily.invoke(null, family) != null;
    }
  }
}
