/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

/**
 * Makes sure Swing's complex-text shaping works before anything lays out text, and switches the JDK
 * to its older JNI shaper when it does not (frostwire/frostwire#1587).
 *
 * <p>Since JDK 21 text shaping goes through {@code sun.font.HBShaper}, which binds HarfBuzz with
 * the Foreign Function API in its static initializer. On some Windows machines that initializer
 * fails once, with an ExceptionInInitializerError nobody sees. The class is then dead for the whole
 * run: every later layout of text that needs shaping (a translated label, a non-Latin torrent name,
 * any {@code TextLayout}) throws {@code NoClassDefFoundError: Could not initialize class
 * sun.font.HBShaper}, while plain Latin text keeps working through a fast path, so the app can run
 * for hours before it hits a label that needs shaping. JDK 27's {@code SunLayoutEngine} reads the
 * property {@value #FFM_PROPERTY} once, when it is initialized: with {@code false} it shapes
 * through the JNI path and never touches {@code HBShaper}.
 *
 * <p>So the guard initializes {@code HBShaper} first, before any text layout, and on failure sets
 * the property. It must run before the first layout of any text: once {@code SunLayoutEngine} has
 * read the property, setting it is too late (the experiment that shows both is {@code
 * TextShapingGuardEndToEndTest}). A user who set the property explicitly keeps their choice.
 */
public final class TextShapingGuard {
  private static final Logger LOG = Logger.getLogger(TextShapingGuard.class);

  /** The JDK's switch between the FFM (HBShaper) and the JNI text shapers. */
  static final String FFM_PROPERTY = "sun.font.layout.ffm";

  private static final String HB_SHAPER = "sun.font.HBShaper";

  /** Initializes the FFM shaper; throws what its static initializer throws. */
  @FunctionalInterface
  interface Probe {
    void initialize() throws Throwable;
  }

  private TextShapingGuard() {}

  /**
   * Call first thing in {@code main}, before any AWT or Swing text. Never throws.
   *
   * @return true when the FFM shaper works (or there is none, or the user chose), false when it
   *     failed and the JNI shaper was selected
   */
  public static boolean install() {
    return install(TextShapingGuard::initializeHbShaper);
  }

  static boolean install(Probe probe) {
    if (System.getProperty(FFM_PROPERTY) != null) {
      return true; // an explicit choice, ours to respect
    }
    try {
      probe.initialize();
      return true;
    } catch (ClassNotFoundException noFfmShaper) {
      return true; // a JDK without HBShaper shapes through the JNI path already
    } catch (NativeLibrariesUnavailable noFonts) {
      // No shaper can work without the font libraries: nothing to switch, but keep the cause.
      LOG.warn("The JDK's font native libraries failed to load: " + noFonts.getCause(), noFonts);
      JdkWarmUp.recordTextFailure(noFonts.getCause());
      return true;
    } catch (Throwable failure) {
      System.setProperty(FFM_PROPERTY, "false");
      LOG.warn(
          "HBShaper (HarfBuzz through the Foreign Function API) failed to initialize; text is"
              + " shaped through the JNI path instead: "
              + failure,
          failure);
      JdkWarmUp.recordTextFailure(failure);
      return false;
    }
  }

  /** The font libraries could not be loaded: not a shaper failure. */
  static final class NativeLibrariesUnavailable extends Exception {
    NativeLibrariesUnavailable(Throwable cause) {
      super(cause);
    }
  }

  private static void initializeHbShaper() throws Throwable {
    // HBShaper's static initializer looks its native functions up (SymbolLookup.loaderLookup) in
    // the
    // libraries the boot loader has already loaded, and the JDK only loads them when the font
    // classes first need them (SunLayoutEngine, SunFontManager): initialized before that, it
    // fails with "Symbol not found: HBSetFreeFn", which is not the failure being guarded against.
    // The classes are not exported; Class.forName with the boot loader does not need that.
    try {
      Class.forName("sun.font.FontManagerNativeLibrary", true, null);
    } catch (ClassNotFoundException e) {
      throw e;
    } catch (Throwable t) {
      throw new NativeLibrariesUnavailable(t);
    }
    Class.forName(HB_SHAPER, true, null);
  }
}
