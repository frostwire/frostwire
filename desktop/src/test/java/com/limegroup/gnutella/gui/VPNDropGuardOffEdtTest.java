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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * A VPN status report reaches VPNDropGuard on the EDT. Applying VPN-Drop protection there paused
 * the engine on the EDT, and pausing waits for libtorrent's session thread (Icebase #a4440162fa7c,
 * frostwire/frostwire#1535).
 */
class VPNDropGuardOffEdtTest {
  @Test
  void aStatusReportHandsTheProtectionToTheBackgroundQueue() {
    Consumer<Runnable> original = VPNDropGuard.background;
    List<Runnable> queued = new ArrayList<>();
    VPNDropGuard.background = queued::add;
    try {
      new VPNDropGuard().onStatusUpdated(false);

      assertEquals(1, queued.size(), "the protection is queued, once per report");
    } finally {
      VPNDropGuard.background = original;
    }
  }

  @Test
  void theStatusListenerNeverAppliesTheProtectionOnTheCallingThread() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/VPNDropGuard.java"))
            .replaceAll("\\s+", "");
    String listener = source.substring(source.indexOf("publicvoidonStatusUpdated("));

    assertTrue(listener.contains("background.accept(VPNDropGuard::applyVPNDropProtection);"));
    assertFalse(
        listener.contains("vpnIsOn;applyVPNDropProtection();"),
        "onStatusUpdated runs on the EDT and must not pause or resume the engine itself");
    assertTrue(
        source.contains("background=BackgroundQueuedExecutorService::schedule;"),
        "the default queue is the shared background queue");
  }
}
