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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.UIManager;

/**
 * Pays the JDK's one-time initialization costs on a background thread at startup, so the first
 * paint or click that needs them does not stall the EDT.
 *
 * <ul>
 *   <li>Shaping: the first complex-text layout initializes HarfBuzz ({@code HBShaper.<clinit>},
 *       which builds a Foreign Function linker).
 *   <li>Fallback fonts: a composite UI font maps characters missing from its primary font through
 *       fallback "slot" fonts, and each slot reads its character map from disk the first time it is
 *       consulted (several MB for CJK fonts).
 *   <li>Class loading: building the main window creates a UI delegate per component type, and each
 *       delegate class (FrostWire's skin classes and their Synth/Basic superclasses) is read from
 *       the jar or the JDK's module image and defined the first time, on the EDT.
 *   <li>Slow static initializers of JDK classes first used while painting or clicking, see {@link
 *       #CLASSES_TO_INITIALIZE}.
 * </ul>
 *
 * Call after the look and feel is installed, so the UI fonts are the ones the app will paint with.
 */
public final class JdkWarmUp {
  private static final Logger LOG = Logger.getLogger(JdkWarmUp.class);

  /** Complex script text: layoutGlyphVector always goes through GlyphLayout and the shaper. */
  private static final String SHAPING_SAMPLE =
      "FrostWire " + new String(new int[] {0x0915, 0x094D, 0x0937}, 0, 3);

  /** Unassigned code point no font maps, so the lookup walks (and initializes) every slot. */
  private static final int UNMAPPED_CODE_POINT = 0x0378;

  /**
   * JDK classes whose static initializers are slow and would otherwise run on the EDT: DragSource
   * decodes the drag cursors (first mouse press in a drag-enabled table), SunGraphicsCallback
   * brings up java.util.logging through its PlatformLogger (first container paint).
   */
  static final List<String> CLASSES_TO_INITIALIZE =
      List.of("java.awt.dnd.DragSource", "sun.awt.SunGraphicsCallback");

  /** Synth-based looks (Nimbus) map every delegate key to this class, which creates "Synth"+key. */
  private static final String SYNTH_LOOK_AND_FEEL = "javax.swing.plaf.synth.SynthLookAndFeel";

  private static final String SYNTH_UI_PREFIX = "javax.swing.plaf.synth.Synth";

  private JdkWarmUp() {}

  public static void startInBackground() {
    Thread warmUp = new Thread(JdkWarmUp::warmUp, "JdkWarmUp");
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
    try {
      loadWithoutInitializing(
          uiDelegateClassNames(UIManager.getDefaults()), JdkWarmUp.class.getClassLoader());
    } catch (Throwable t) {
      LOG.warn("UI delegate warm-up failed: " + t.getMessage(), t);
    }
    for (String className : CLASSES_TO_INITIALIZE) {
      try {
        Class.forName(className);
      } catch (Throwable t) {
        LOG.warn("Warm-up of " + className + " failed: " + t.getMessage(), t);
      }
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

  /**
   * Class names of the look and feel's component UI delegates, the String values of its "*UI" keys
   * ("PopupMenuSeparatorUI" to "com.frostwire.gui.theme.SkinPopupMenuSeparatorUI"). Keys a
   * Synth-based look maps to {@code SynthLookAndFeel} yield the Synth delegate it creates for them
   * ("ButtonUI" to "javax.swing.plaf.synth.SynthButtonUI"); the few it names differently are
   * skipped when loaded.
   */
  static Set<String> uiDelegateClassNames(Map<?, ?> defaults) {
    Set<String> classNames = new LinkedHashSet<>();
    for (Map.Entry<?, ?> entry : List.copyOf(defaults.entrySet())) {
      if (entry.getKey() instanceof String key
          && key.endsWith("UI")
          && entry.getValue() instanceof String className) {
        classNames.add(SYNTH_LOOK_AND_FEEL.equals(className) ? SYNTH_UI_PREFIX + key : className);
      }
    }
    return classNames;
  }

  /**
   * Loads each class (and so its superclasses and interfaces) without running static initializers,
   * which stay on the thread that first uses the class. Missing classes are skipped.
   */
  static void loadWithoutInitializing(Collection<String> classNames, ClassLoader loader) {
    for (String className : classNames) {
      try {
        Class.forName(className, false, loader);
      } catch (ClassNotFoundException | LinkageError e) {
        // Not on this look and feel or platform; the EDT loads (or fails) it as it would have.
      }
    }
  }
}
