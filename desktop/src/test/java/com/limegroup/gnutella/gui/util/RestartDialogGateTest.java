/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RestartDialogGateTest {
  @Test
  void oneNoticeAtATime() {
    RestartDialogGate gate = new RestartDialogGate();
    assertTrue(gate.schedule());
    assertFalse(gate.schedule(), "a second apply while the notice is up must not open another");
    gate.finished();
    assertTrue(gate.schedule(), "a later change that needs a restart must still be able to warn");
  }
}
