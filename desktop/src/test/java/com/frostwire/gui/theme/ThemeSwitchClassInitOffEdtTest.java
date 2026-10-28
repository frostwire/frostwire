/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1330: switching to a FlatLaf theme from Tools → Theme ran FlatLaf's static
 * initializer, which extracts and loads FlatLaf's native library, on the EDT. The class
 * initialization a theme switch asks for must run off the EDT, before the EDT applies the theme.
 */
public class ThemeSwitchClassInitOffEdtTest {

  @Test
  public void classInitializationRunsOffTheEdtBeforeTheEdtAppliesTheTheme() throws Exception {
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      List<String> events = new CopyOnWriteArrayList<>();
      Thread[] initThread = new Thread[1];
      Thread[] applyThread = new Thread[1];
      CountDownLatch applied = new CountDownLatch(1);

      // the menu action calls switchTheme on the EDT
      SwingUtilities.invokeAndWait(
          () ->
              ThemeSwitcher.switchTheme(
                  ThemeMediator.ThemeEnum.DARK_FLAT_LAF,
                  background,
                  theme -> {
                    initThread[0] = Thread.currentThread();
                    events.add("init " + theme);
                  },
                  theme -> {
                    applyThread[0] = Thread.currentThread();
                    events.add("apply " + theme);
                    applied.countDown();
                  }));

      assertTrue(applied.await(10, TimeUnit.SECONDS), "the theme was never applied");
      assertNotNull(initThread[0], "the look and feel classes were never initialized");
      assertFalse(
          isEdt(initThread[0]), "class initialization ran on the EDT: " + initThread[0].getName());
      assertTrue(isEdt(applyThread[0]), "the theme was applied off the EDT");
      assertEquals(List.of("init DARK_FLAT_LAF", "apply DARK_FLAT_LAF"), events);
    } finally {
      background.shutdownNow();
    }
  }

  @Test
  public void aFailedClassInitializationStillAppliesTheThemeOnTheEdt() throws Exception {
    ExecutorService background = Executors.newSingleThreadExecutor();
    try {
      CountDownLatch applied = new CountDownLatch(1);
      Thread[] applyThread = new Thread[1];
      SwingUtilities.invokeAndWait(
          () ->
              ThemeSwitcher.switchTheme(
                  ThemeMediator.ThemeEnum.LIGHT_FLAT_LAF,
                  background,
                  theme -> {
                    throw new UnsatisfiedLinkError("no native library");
                  },
                  theme -> {
                    applyThread[0] = Thread.currentThread();
                    applied.countDown();
                  }));
      assertTrue(applied.await(10, TimeUnit.SECONDS), "the theme was never applied");
      assertTrue(isEdt(applyThread[0]), "the theme was applied off the EDT");
    } finally {
      background.shutdownNow();
    }
  }

  @Test
  public void initializesTheFlatLafClassesOfEveryTheme() {
    for (ThemeMediator.ThemeEnum theme : ThemeMediator.ThemeEnum.values()) {
      ThemeSwitcher.initializeLookAndFeelClasses(theme);
    }
  }

  /**
   * GitHub #1348: applying a FlatLaf theme creates a UI delegate per component type, and each
   * delegate class was read from the jar on the EDT (Icebase #54e4eb98d3ef, FlatSliderUI). The
   * background step loads them, without initializing, so a fresh JVM has them loaded before the EDT
   * applies the theme.
   */
  @Test
  public void backgroundStepLoadsTheFlatLafDelegatesWithoutInitializingThem() throws Exception {
    Process probe =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info",
                "-Xlog:class+init=info",
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                DelegateProbe.class.getName())
            .redirectErrorStream(true)
            .start();
    String log = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(probe.waitFor(120, TimeUnit.SECONDS), "probe timed out");
    assertEquals(0, probe.exitValue(), log);
    for (String delegate :
        List.of("FlatSliderUI", "FlatButtonUI", "FlatTableUI", "FlatScrollBarUI")) {
      assertTrue(
          log.contains("com.formdev.flatlaf.ui." + delegate + " "),
          delegate + " not loaded by the background step");
    }
    assertFalse(
        log.contains("Initializing 'com/formdev/flatlaf/ui/FlatSliderUI'"),
        "a delegate's static initializer ran off the EDT");
  }

  /** Runs only the background step of a switch to the dark FlatLaf theme in a fresh JVM. */
  static final class DelegateProbe {
    public static void main(String[] args) {
      ThemeSwitcher.initializeLookAndFeelClasses(ThemeMediator.ThemeEnum.DARK_FLAT_LAF);
    }
  }

  private static boolean isEdt(Thread thread) throws Exception {
    Thread[] edt = new Thread[1];
    SwingUtilities.invokeAndWait(() -> edt[0] = Thread.currentThread());
    return thread == edt[0];
  }
}
