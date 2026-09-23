/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.vpn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class VPNDropProtectionToolTest {

  @Test
  void statusLineRefreshIsPostedToTheEdt() throws Exception {
    AtomicBoolean onEdt = new AtomicBoolean();
    assertFalse(SwingUtilities.isEventDispatchThread());

    VPNDropProtectionTool.postStatusRefresh(
        () -> onEdt.set(SwingUtilities.isEventDispatchThread()));
    SwingUtilities.invokeAndWait(() -> {});

    assertTrue(onEdt.get());
    assertFalse(SwingUtilities.isEventDispatchThread());
  }
}
