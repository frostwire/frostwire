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

  private static boolean isEdt(Thread thread) throws Exception {
    Thread[] edt = new Thread[1];
    SwingUtilities.invokeAndWait(() -> edt[0] = Thread.currentThread());
    return thread == edt[0];
  }
}
