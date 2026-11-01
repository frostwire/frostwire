/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Cancelling the wizard called System.exit on the EDT (Icebase #2503b0a74dce, GitHub #1437). Now
 * the exit runs on its own thread and the wizard's modal loop ends normally, so closing the wizard
 * must not let startup go on to start the core while the JVM exits.
 */
@Timeout(15)
class SetupManagerExitTest {

  @Test
  void quittingFromTheWizardKeepsStartupParked() throws Exception {
    List<Integer> exits = new CopyOnWriteArrayList<>();
    SetupManager manager = new SetupManager(() -> SetupManager.Pages.NONE, exits::add);
    CountDownLatch returned = new CountDownLatch(1);
    Thread startup =
        new Thread(
            () -> {
              manager.awaitClosed();
              returned.countDown();
            },
            "startup");
    startup.setDaemon(true);
    startup.start();

    SwingUtilities.invokeAndWait(
        () -> {
          manager.exit(0);
          // The modal loop ends once the dialog is disposed.
          manager.wizardClosed();
        });

    assertEquals(List.of(0), exits);
    assertFalse(
        returned.await(300, TimeUnit.MILLISECONDS),
        "startup must not resume while the app is exiting");
  }
}
