/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.BuildConfig;
import com.frostwire.jlibtorrent.swig.libtorrent_jni;
import com.frostwire.util.InMemoryImageIO;
import com.frostwire.util.OSUtils;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import javax.swing.*;
import org.limewire.util.CommonUtils;

/**
 * This class constructs an `Initializer` instance that constructs all the necessary classes for the
 * application.
 */
public class Main {
  /**
   * Forces grayscale (not subpixel-LCD) Swing text rendering on Linux.
   *
   * <p>Swing text ({@code SwingUtilities2.drawString}, used by every Synth label, table cell and
   * progress-bar string) resolves LCD-vs-grayscale from the {@code awt.font.desktophints} desktop
   * property supplied by the GNOME XSettings. Per-Graphics antialiasing hints cannot override it,
   * and the double-buffered offscreen graphics never even see them, so table paints rasterize every
   * glyph through the native subpixel rasterizer. That costs roughly 3x the grayscale path in the
   * software Java2D pipeline and freezes full-table paints on XWayland. The remaining desktop hints
   * (e.g. fractional metrics) are preserved.
   *
   * <p>{@code Toolkit.setDesktopProperty} is protected, so this reflective call relies on the
   * {@code --add-opens=java.desktop/java.awt=ALL-UNNAMED} flag already present in the application
   * and run JVM arguments. When the call is unavailable the desktop hints are left untouched.
   */
  public static void disableLcdFontSmoothing() {
    try {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      java.lang.reflect.Method setter =
          Toolkit.class.getDeclaredMethod("setDesktopProperty", String.class, Object.class);
      setter.setAccessible(true);
      setter.invoke(
          toolkit,
          "awt.font.desktophints",
          grayscaleFontHints(toolkit.getDesktopProperty("awt.font.desktophints")));
    } catch (ReflectiveOperationException | SecurityException ignored) {
      // Leave the desktop hints untouched.
    }
  }

  /**
   * Returns a copy of the given desktop font hints requesting grayscale text antialiasing instead
   * of subpixel-LCD. Pure function for testability.
   */
  public static java.util.Map<Object, Object> grayscaleFontHints(Object currentHints) {
    java.util.Map<Object, Object> hints;
    if (currentHints instanceof java.util.Map) {
      hints = new java.util.HashMap<>((java.util.Map<?, ?>) currentHints);
    } else {
      hints = new java.util.HashMap<>();
    }
    hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    return hints;
  }

  private static URL CHOSEN_SPLASH_URL = null;

  /** The splash image, decoded once and shared by the AWT splash and the Swing splash. */
  private static Image CHOSEN_SPLASH_IMAGE = null;

  /**
   * Creates an `Initializer` instance that constructs the necessary classes for the application.
   *
   * @param args the array of command line arguments
   */
  public static void main(String[] args) {
    // No display (Windows service/session 0, headless JVM, no X server): the GUI cannot start,
    // so exit with a message instead of a HeadlessException crash report.
    if (GraphicsEnvironment.isHeadless()) {
      System.err.println(
          "FrostWire requires a graphical desktop session; no display is available. Exiting.");
      System.exit(1);
    }
    System.setProperty("sun.awt.noerasebackground", "true");
    // On Linux/KDE, set the AWT application class name to "FrostWire" so that
    // KDE Plasma shows "FrostWire" instead of "JavaEmbeddedFrame" in the system tray.
    if (OSUtils.isLinux()) {
      try {
        var toolkit = Toolkit.getDefaultToolkit();
        var field = toolkit.getClass().getDeclaredField("awtAppClassName");
        field.setAccessible(true);
        field.set(toolkit, "FrostWire");
      } catch (Exception ignored) {
      }
      disableLcdFontSmoothing();
    }
    String arch = System.getProperty("os.arch").toLowerCase();
    boolean isARM64 = arch.equals("aarch64") || arch.equals("arm64");

    // Check if running in a development environment (Gradle or IntelliJ)
    boolean isDevEnvironment = BuildConfig.DEBUG;
    boolean hasDebugger = CommonUtils.isStepDebuggerAttached();

    if (isDevEnvironment && !hasDebugger) {
      // DEVELOPMENT ENVIRONMENT: Fail fast if EDT (Event Dispatcher Thread) stalls
      String source = CommonUtils.isRunningFromGradle() ? "Gradle" : "IntelliJ";
      System.out.println("Main: FrostWire is running in DEVELOPMENT environment (" + source + ").");
      com.frostwire.util.StrictEdtMode.install(java.time.Duration.ofMillis(2000));
      System.out.println(
          "Main: Strict EDT mode is ON. (The application will fail fast if the EDT [Event Dispatcher Thread] stalls for more than 2000 ms)");
    } else if (hasDebugger) {
      System.out.println(
          "Main: FrostWire is running in DEVELOPMENT environment (Debugger attached).");
      System.out.println(
          "Main: Strict EDT mode is OFF while stepping through code to avoid false positives.");
    } else {
      System.out.println("FrostWire is running in a PRODUCTION environment.");
      com.frostwire.util.StrictEdtMode.installProduction(java.time.Duration.ofMillis(5000));
    }
    ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
    JPopupMenu.setDefaultLightWeightPopupEnabled(false);

    // System.out.println("1: Main.main("+args+")");
    // make sure jlibtorrent is statically loaded on time to avoid jni symbols not found issues.
    libtorrent_jni.version();
    Frame splash = null;
    try {
      // show initial splash screen only if there are no arguments
      if (args == null || args.length == 0) splash = showInitialSplash();
      // load the GUI through reflection so that we don't reference classes here,
      // which would slow the speed of class-loading, causing the splash to be
      // displayed later.
      try {
        Class.forName("com.limegroup.gnutella.gui.GUILoader")
            .getMethod("load", new Class<?>[] {String[].class, Frame.class})
            .invoke(null, args, splash);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Throwable e) {
      CrashReportSpooler.recordSync(e);
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** Shows the initial splash window. */
  private static Frame showInitialSplash() {
    Frame splashFrame = null;
    Image image = getChosenSplashImage();
    if (image != null) {
      splashFrame = AWTSplashWindow.splash(image);
    }
    return splashFrame;
  }

  /**
   * The decoded splash image, or null when it is missing or unreadable. The first call decodes the
   * PNG, so callers make it off the EDT: the startup thread showing the AWT splash, and the
   * Initializer before it builds the Swing splash on the EDT.
   */
  public static synchronized Image getChosenSplashImage() {
    if (CHOSEN_SPLASH_IMAGE == null) {
      URL imageURL = getChosenSplashURL();
      if (imageURL != null) {
        try {
          CHOSEN_SPLASH_IMAGE = InMemoryImageIO.read(imageURL);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return CHOSEN_SPLASH_IMAGE;
  }

  /**
   * Tries to get a random splash every time. It keeps track of the last 2 shown splashes to avoid
   * recent collisions.
   */
  public static URL getChosenSplashURL() {
    if (CHOSEN_SPLASH_URL != null) return CHOSEN_SPLASH_URL;
    final String splashPath = "org/limewire/gui/images/app_splash.png";
    CHOSEN_SPLASH_URL = ClassLoader.getSystemResource(splashPath);
    return CHOSEN_SPLASH_URL;
  }
}
