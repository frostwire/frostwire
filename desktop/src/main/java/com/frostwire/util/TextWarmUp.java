/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.UIManager;

/**
 * Pays the JDK's one-time text costs on a background thread at startup, so the first paint that
 * needs them does not stall the EDT.
 *
 * <ul>
 *   <li>Shaping: the first complex-text layout initializes HarfBuzz ({@code HBShaper.<clinit>},
 *       which builds a Foreign Function linker).
 *   <li>Fallback fonts: a composite UI font maps characters missing from its primary font through
 *       fallback "slot" fonts, and each slot reads its character map from disk the first time it is
 *       consulted (several MB for CJK fonts).
 * </ul>
 *
 * Call after the look and feel is installed, so the UI fonts are the ones the app will paint with.
 */
public final class TextWarmUp {
  private static final Logger LOG = Logger.getLogger(TextWarmUp.class);

  /** Complex script text: layoutGlyphVector always goes through GlyphLayout and the shaper. */
  private static final String SHAPING_SAMPLE =
      "FrostWire " + new String(new int[] {0x0915, 0x094D, 0x0937}, 0, 3);

  /** Unassigned code point no font maps, so the lookup walks (and initializes) every slot. */
  private static final int UNMAPPED_CODE_POINT = 0x0378;

  private TextWarmUp() {}

  public static void startInBackground() {
    Thread warmUp = new Thread(TextWarmUp::warmUp, "TextWarmUp");
    warmUp.setDaemon(true);
    warmUp.start();
  }

  private static void warmUp() {
    try {
      char[] text = SHAPING_SAMPLE.toCharArray();
      new Font(Font.DIALOG, Font.PLAIN, 12)
          .layoutGlyphVector(
              new FontRenderContext(null, true, true),
              text,
              0,
              text.length,
              Font.LAYOUT_LEFT_TO_RIGHT);
      for (Font font : distinctFonts(UIManager.getDefaults())) {
        font.canDisplay(UNMAPPED_CODE_POINT);
      }
    } catch (Throwable t) {
      // Best effort: the first on-EDT use pays these costs instead.
      LOG.warn("Text warm-up failed: " + t.getMessage(), t);
    }
  }

  /** One font per family and style among the values; sizes share the same font data. */
  static List<Font> distinctFonts(Map<?, ?> defaults) {
    Set<String> seen = new HashSet<>();
    List<Font> fonts = new ArrayList<>();
    for (Object value : defaults.values().toArray()) {
      if (value instanceof Font font && seen.add(font.getFontName() + "/" + font.getStyle())) {
        fonts.add(font);
      }
    }
    return fonts;
  }
}
