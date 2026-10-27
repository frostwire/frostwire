/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Finishing the setup wizard on the EDT wrote the intent file there, the same file whose close
 * stalled the EDT while the wizard decided its pages (Icebase #a91f075a3761, GitHub #1334). The
 * write must be handed to a background thread.
 */
class IntentWindowSaveOffEdtTest {

  @Test
  void finishingTheWizardHandsTheIntentFileWriteToTheBackground() throws Exception {
    List<Runnable> queued = new CopyOnWriteArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          IntentWindow window = new IntentWindow(null, queued::add);
          window.confirmWillNot(true);
          window.applySettings(true);
          assertTrue(window.isConfirmedWillNot());
        });
    assertEquals(1, queued.size(), "the intent file is written by the background queue");
  }

  @Test
  void nothingIsWrittenUntilTheUserConfirms() throws Exception {
    List<Runnable> queued = new CopyOnWriteArrayList<>();
    SwingUtilities.invokeAndWait(() -> new IntentWindow(null, queued::add).applySettings(true));
    assertTrue(queued.isEmpty());
  }
}
