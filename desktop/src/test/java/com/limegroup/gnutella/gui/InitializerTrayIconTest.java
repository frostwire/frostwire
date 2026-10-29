/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Adding the tray icon at startup is a native toolkit call (SystemTray.add) that stalled the EDT
 * (frostwire/frostwire#1369): it runs on the startup thread, and only the settings check runs on
 * the EDT, after it.
 */
class InitializerTrayIconTest {

  @Test
  void theTrayIconIsAddedOnTheStartupThreadAndOnlyTheSettingsCheckRunsOnTheEdt() throws Exception {
    AtomicBoolean trayOnEdt = new AtomicBoolean(true);
    AtomicBoolean checkOnEdt = new AtomicBoolean(false);
    List<String> order = new CopyOnWriteArrayList<>();
    Thread startup =
        new Thread(
            () ->
                Initializer.loadTrayAndNotifications(
                    () -> {
                      trayOnEdt.set(EventQueue.isDispatchThread());
                      order.add("tray");
                    },
                    () -> {
                      checkOnEdt.set(EventQueue.isDispatchThread());
                      order.add("settings");
                    },
                    r -> {
                      try {
                        EventQueue.invokeAndWait(r);
                      } catch (Exception e) {
                        throw new IllegalStateException(e);
                      }
                    }));
    startup.start();
    startup.join();
    assertFalse(trayOnEdt.get(), "the tray icon was added on the EDT");
    assertTrue(checkOnEdt.get(), "the settings check must run on the EDT");
    assertEquals(List.of("tray", "settings"), order);
  }
}
