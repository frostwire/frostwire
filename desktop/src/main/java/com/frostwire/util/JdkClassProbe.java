/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.util.List;

/**
 * Initializes, once and first thing at startup, the JDK classes that have been seen failing on some
 * Windows machines (frostwire/frostwire#1587), and keeps the first failure.
 *
 * <p>A class whose static initializer fails once is dead for the whole run: every later use throws
 * {@code NoClassDefFoundError: Could not initialize class X}, with no trace of why. Icebase then
 * shows that late error from wherever the app happened to use X ({@code sun.nio.ch.Net} in {@code
 * ExternalControl.startServer}, {@code sun.font.HBShaper}, a JPEG reader), while the first failure
 * (an ExceptionInInitializerError or UnsatisfiedLinkError that names the real cause) happened on
 * some other thread and was never reported. Initializing them here, on the main thread before
 * anything else uses them, means the first failure is seen, kept in {@link
 * JdkWarmUp#firstTextWarmUpFailure()} (which every report carries) and logged. Nothing is changed
 * or retried: a class that cannot initialize cannot be repaired from here, only explained.
 *
 * <p>The classes are in the JDK's boot loader, not exported: {@code Class.forName} with the boot
 * loader does not need that. A class a JDK does not have is skipped.
 */
public final class JdkClassProbe {
  private static final Logger LOG = Logger.getLogger(JdkClassProbe.class);

  /** The JDK classes to initialize: networking, the Windows file system and JPEG decoding. */
  static final List<String> CLASSES =
      List.of(
          "sun.nio.ch.Net",
          "sun.nio.ch.IOUtil",
          "sun.nio.fs.WindowsNativeDispatcher",
          "com.sun.imageio.plugins.jpeg.JPEGImageReader");

  /** Initializes one class; throws what its static initializer throws. */
  @FunctionalInterface
  interface Initializer {
    void initialize(String className) throws Throwable;
  }

  private JdkClassProbe() {}

  /** Call early in {@code main}, after {@link TextShapingGuard#install()}. Never throws. */
  public static void install() {
    install(CLASSES, name -> Class.forName(name, true, null));
  }

  /**
   * @return how many classes failed to initialize
   */
  static int install(List<String> classes, Initializer initializer) {
    int failures = 0;
    for (String className : classes) {
      try {
        initializer.initialize(className);
      } catch (ClassNotFoundException notInThisJdk) {
        // Not on this JDK or operating system: nothing to probe.
      } catch (Throwable failure) {
        failures++;
        LOG.warn(
            "JDK class " + className + " failed to initialize: " + failure.getMessage(), failure);
        JdkWarmUp.recordTextFailure(
            new IllegalStateException("JDK class " + className + " failed to initialize", failure));
      }
    }
    return failures;
  }
}
