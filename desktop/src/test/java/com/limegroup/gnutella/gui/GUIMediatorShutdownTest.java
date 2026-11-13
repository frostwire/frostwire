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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * After a start that failed before the GUIMediator was built, closing the main window called
 * GUIMediator.instance() on the way out, which built the main window and its menus and failed on
 * the half-built splash (Icebase #322beebdff0c, #bfa37395b7e5, #f38c6738a224). The exit paths must
 * work without building it.
 */
class GUIMediatorShutdownTest {

  @Test
  void shutdownWithoutAMediatorRunsTheFinalizerAndBuildsNothing() {
    assertNull(GUIMediator.instanceIfConstructed());
    AtomicInteger finalizerRuns = new AtomicInteger();

    GUIMediator.shutdown(finalizerRuns::incrementAndGet);

    assertEquals(1, finalizerRuns.get());
    assertFalse(GUIMediator.isConstructed());
  }

  @Test
  void closingWithoutAMediatorExits() {
    for (boolean minimizeToTray : new boolean[] {false, true}) {
      for (boolean tray : new boolean[] {false, true}) {
        for (boolean mac : new boolean[] {false, true}) {
          for (boolean fromFrame : new boolean[] {false, true}) {
            assertEquals(
                WindowCloseAction.SHUTDOWN,
                WindowCloseAction.decide(false, minimizeToTray, tray, mac, fromFrame));
          }
        }
      }
    }
  }
}
