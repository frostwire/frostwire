/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.frostwire.util.Logger;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Switches themes without initializing look and feel classes on the EDT. The first touch of FlatLaf
 * runs its static initializer, which on Windows extracts FlatLaf's native library from the jar to a
 * temp directory and loads it, slow when antivirus scans the new DLL. Class initialization is
 * thread-safe, so it is paid off the EDT, and the look and feel switch, which must stay on the EDT,
 * finds the classes ready.
 */
final class ThemeSwitcher {
  private static final Logger LOG = Logger.getLogger(ThemeSwitcher.class);

  private ThemeSwitcher() {}

  /**
   * Runs {@code classInitializer} for {@code theme} on {@code background}, then {@code applier} on
   * the EDT, even when the initialization failed: applying the theme initializes the classes anyway
   * and reports the failure the way it always did.
   */
  static void switchTheme(
      ThemeMediator.ThemeEnum theme,
      Executor background,
      Consumer<ThemeMediator.ThemeEnum> classInitializer,
      Consumer<ThemeMediator.ThemeEnum> applier) {
    background.execute(
        () -> {
          try {
            classInitializer.accept(theme);
          } catch (Throwable t) {
            LOG.warn("Unable to initialize the look and feel classes of " + theme, t);
          }
          SwingUtilities.invokeLater(() -> applier.accept(theme));
        });
  }

  /**
   * Initializes the FlatLaf classes {@code theme} needs, loading FlatLaf's native library. Nimbus
   * ships with the JDK and loads no native code, so the default theme needs nothing here.
   */
  static void initializeLookAndFeelClasses(ThemeMediator.ThemeEnum theme) {
    Class<?> lookAndFeel;
    if (theme == ThemeMediator.ThemeEnum.DARK_FLAT_LAF) {
      lookAndFeel = FlatDarkLaf.class;
    } else if (theme == ThemeMediator.ThemeEnum.LIGHT_FLAT_LAF) {
      lookAndFeel = FlatLightLaf.class;
    } else {
      return;
    }
    try {
      Class.forName(lookAndFeel.getName(), true, lookAndFeel.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}
