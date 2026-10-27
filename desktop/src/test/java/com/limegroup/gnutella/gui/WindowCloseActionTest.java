/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Closing the main window saved every settings file synchronously on the EDT, then exited through
 * the shutdown thread, which saved them again. Settings must be saved exactly once per close: in
 * the background when the window only hides, and only by the shutdown thread when FrostWire exits.
 */
class WindowCloseActionTest {

  @Test
  void hidingToTheTraySavesInTheBackground() {
    for (boolean mac : new boolean[] {false, true}) {
      for (boolean fromFrame : new boolean[] {false, true}) {
        WindowCloseAction action = WindowCloseAction.decide(true, true, mac, fromFrame);
        assertEquals(WindowCloseAction.HIDE_TO_TRAY, action);
        assertTrue(action.savesSettingsInBackground());
      }
    }
  }

  @Test
  void macCloseButtonHidesAndSavesInTheBackground() {
    WindowCloseAction action = WindowCloseAction.decide(false, true, true, true);
    assertEquals(WindowCloseAction.HIDE_WINDOW, action);
    assertTrue(action.savesSettingsInBackground());
  }

  @Test
  void exitingLeavesTheSaveToTheShutdownThread() {
    // Windows/Linux close button, the Close menu item, and the macOS quit event.
    for (boolean mac : new boolean[] {false, true}) {
      for (boolean tray : new boolean[] {false, true}) {
        WindowCloseAction action = WindowCloseAction.decide(false, tray, mac, false);
        assertEquals(WindowCloseAction.SHUTDOWN, action);
        assertFalse(action.savesSettingsInBackground());
      }
    }
    WindowCloseAction closeButton = WindowCloseAction.decide(false, true, false, true);
    assertEquals(WindowCloseAction.SHUTDOWN, closeButton);
    assertFalse(closeButton.savesSettingsInBackground());
  }

  @Test
  void minimizeToTrayWithoutATrayKeepsTheWindowAndDoesNotSave() {
    WindowCloseAction action = WindowCloseAction.decide(true, false, false, true);
    assertEquals(WindowCloseAction.NONE, action);
    assertFalse(action.savesSettingsInBackground());
  }
}
