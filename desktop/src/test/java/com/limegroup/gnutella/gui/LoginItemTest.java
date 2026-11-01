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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The setup wizard and the Options pane set the login item on the EDT, which on Windows checks the
 * Start Menu folder and copies or deletes a shortcut, the same kind of file check that froze the
 * wizard on a slow disk (Icebase #9325f45dd41f). The OS must only be called from the background.
 */
@Timeout(15)
class LoginItemTest {

  @Test
  void theOsIsCalledOffTheEdt() throws Exception {
    List<Runnable> queued = new CopyOnWriteArrayList<>();
    List<Boolean> calls = new CopyOnWriteArrayList<>();
    List<Boolean> calledOnEdt = new CopyOnWriteArrayList<>();

    SwingUtilities.invokeAndWait(
        () ->
            LoginItem.set(
                true,
                queued::add,
                allow -> {
                  calls.add(allow);
                  calledOnEdt.add(SwingUtilities.isEventDispatchThread());
                }));

    assertTrue(calls.isEmpty(), "the EDT must not call the OS");
    assertEquals(1, queued.size());

    queued.get(0).run(); // the test thread, off the EDT

    assertEquals(List.of(true), calls);
    assertFalse(calledOnEdt.get(0));
  }
}
