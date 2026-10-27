/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

/**
 * What closing the main window (its X button, the Close menu item or the macOS quit event) does,
 * once the user's hide/exit choice is known. Hiding keeps the process running, so it saves the
 * settings in the background. Exiting leaves the save to the shutdown thread, which saves once on
 * every exit path; saving here as well would write the props file twice, the first time on the EDT.
 */
enum WindowCloseAction {
  /** Hide the window and leave the tray icon. */
  HIDE_TO_TRAY(true),
  /** Hide the window without exiting (macOS close button). */
  HIDE_WINDOW(true),
  /** Exit; the shutdown thread saves the settings. */
  SHUTDOWN(false),
  /**
   * Minimize to tray was chosen but no tray icon is available: minimize the window instead, so the
   * close button still does something and the app keeps running.
   */
  MINIMIZE(true);

  private final boolean savesSettingsInBackground;

  WindowCloseAction(boolean savesSettingsInBackground) {
    this.savesSettingsInBackground = savesSettingsInBackground;
  }

  boolean savesSettingsInBackground() {
    return savesSettingsInBackground;
  }

  static WindowCloseAction decide(
      boolean minimizeToTray, boolean trayUsable, boolean macOSX, boolean fromFrame) {
    if (minimizeToTray) {
      return trayUsable ? HIDE_TO_TRAY : MINIMIZE;
    }
    if (macOSX && fromFrame) {
      // On macOS the close button hides the window; the app keeps running until Quit.
      return HIDE_WINDOW;
    }
    return SHUTDOWN;
  }
}
