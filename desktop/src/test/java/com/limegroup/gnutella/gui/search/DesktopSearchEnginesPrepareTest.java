/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The first search tab built the search engine registry on the EDT, loading every engine's classes
 * and freezing the window (Icebase #76546772fb11, frostwire/frostwire#1573): startup only built it
 * from the relay services, and only with IceBridge on. Startup now builds it on a background thread
 * before the UI loads.
 */
class DesktopSearchEnginesPrepareTest {

  @Test
  void prepareBuildsTheRegistryOnTheGivenExecutor() throws Exception {
    List<Runnable> tasks = new ArrayList<>();
    DesktopSearchEngines.prepare(tasks::add);
    assertEquals(1, tasks.size(), "the build is handed to the executor, not done by the caller");

    AtomicBoolean onEdt = new AtomicBoolean(true);
    Thread worker =
        new Thread(
            () -> {
              onEdt.set(SwingUtilities.isEventDispatchThread());
              tasks.get(0).run();
            });
    worker.start();
    worker.join(30_000);
    assertFalse(worker.isAlive(), "building the registry finished");
    assertFalse(onEdt.get());
    assertSame(DesktopSearchEngines.registry(), DesktopSearchEngines.registry());
    assertFalse(DesktopSearchEngines.all().isEmpty());
  }

  @Test
  void startupPreparesTheRegistryOffTheEdtBeforeTheUiLoads() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    int prepare = source.indexOf("DesktopSearchEngines.prepare(DesktopParallelExecutor::execute);");
    int loadUi = source.indexOf("loadUI();");
    assertTrue(prepare >= 0, "startup no longer builds the search engine registry off the EDT");
    assertTrue(loadUi >= 0, "loadUI() not found");
    assertTrue(
        prepare < loadUi, "the registry must be prepared before the UI (and its first search)");
  }
}
