/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.limegroup.gnutella.settings.ApplicationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A stored minimize-to-tray with no tray must not make the pane dirty (frostwire/frostwire#1653).
 */
class ShutdownPaneItemTest {
  private boolean saved;

  @BeforeEach
  void save() {
    saved = ApplicationSettings.MINIMIZE_TO_TRAY.getValue();
  }

  @AfterEach
  void restore() {
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(saved);
  }

  @Test
  void storedMinimizeToTrayWithoutATrayIsNotDirtyOnOpen() throws Exception {
    assumeFalse(ShutdownPaneItem.trayOffered(), "needs a system without a tray");
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(true);
    ShutdownPaneItem pane = PaneSupport.onEdt(ShutdownPaneItem::new);
    pane.initOptions();
    assertFalse(pane.isDirty());
  }

  @Test
  void storedImmediateShutdownIsNotDirtyOnOpen() throws Exception {
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(false);
    ShutdownPaneItem pane = PaneSupport.onEdt(ShutdownPaneItem::new);
    pane.initOptions();
    assertFalse(pane.isDirty());
  }
}
