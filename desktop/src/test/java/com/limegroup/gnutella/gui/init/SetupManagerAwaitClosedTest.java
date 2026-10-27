/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The setup wizard opens from a later EDT event and runs its own modal loop, so startup used to
 * carry on underneath it: the BitTorrent core started and the main window was built, shown and
 * animated inside the wizard's modal loop (Icebase #7dbdac523f1a, #9ea2a2bac172; GitHub #1324),
 * before the user had picked the settings those depend on. Startup must wait for the wizard to
 * close.
 */
class SetupManagerAwaitClosedTest {

  @Test
  void startupThreadWaitsUntilTheWizardCloses() throws Exception {
    SetupManager manager = new SetupManager();
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

    assertFalse(
        returned.await(300, TimeUnit.MILLISECONDS),
        "startup must not go on while the wizard is up");

    SwingUtilities.invokeAndWait(manager::wizardClosed);

    assertTrue(returned.await(5, TimeUnit.SECONDS), "startup resumes once the wizard has closed");
  }

  @Test
  void awaitClosedNeverBlocksTheEdt() throws Exception {
    SetupManager manager = new SetupManager();
    AtomicBoolean returned = new AtomicBoolean();
    SwingUtilities.invokeAndWait(
        () -> {
          manager.awaitClosed();
          returned.set(true);
        });
    assertTrue(returned.get());
  }

  @Test
  void initializerWaitsForTheWizardBeforeStartingTheRest() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    int start = source.indexOf("privatevoidstartSetupManager(");
    int end = source.indexOf("privatevoidloadUI()");
    assertTrue(start >= 0 && end > start);
    String body = source.substring(start, end);
    int created = body.indexOf("setupManager::createIfNeeded");
    int awaited = body.indexOf("setupManager.awaitClosed();");
    assertTrue(created >= 0, "the wizard must still be created on the EDT");
    assertTrue(awaited > created, "startup must wait for the wizard after creating it");
  }
}
