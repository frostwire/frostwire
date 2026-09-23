/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.ipfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class IPFilterTableAccessTest {

  @Test
  void tableWorkRunsOnEdtAndReturnsToTheCallingThread() {
    assertFalse(SwingUtilities.isEventDispatchThread());
    assertTrue(IPFilterTableAccess.onEdt(SwingUtilities::isEventDispatchThread));
    assertFalse(SwingUtilities.isEventDispatchThread());
  }

  @Test
  void listSnapshotIsStableDuringLaterTableChanges() {
    List<String> model = new ArrayList<>(List.of("first", "second"));
    assertThrows(
        IllegalStateException.class, () -> IPFilterTableAccess.copyRows(model::size, model::get));

    List<String> snapshot =
        IPFilterTableAccess.onEdt(() -> IPFilterTableAccess.copyRows(model::size, model::get));
    model.clear();
    assertEquals(List.of("first", "second"), snapshot);
  }
}
