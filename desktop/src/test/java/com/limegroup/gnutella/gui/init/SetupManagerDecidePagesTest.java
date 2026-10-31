/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Deciding which wizard pages to show reads settings, the intent file and, on Windows, the
 * registry. Done inside {@link SetupManager#createIfNeeded()} on the EDT, closing the intent file
 * stalled startup before the wizard appeared (Icebase #a91f075a3761, GitHub #1334). The decision
 * must run on the startup thread; the EDT only builds the pages.
 */
class SetupManagerDecidePagesTest {

  @Test
  void pagesAreDecidedOnTheStartupThreadNotTheEdt() throws Exception {
    List<Thread> deciders = new CopyOnWriteArrayList<>();
    SetupManager manager =
        new SetupManager(
            () -> {
              deciders.add(Thread.currentThread());
              return SetupManager.Pages.NONE;
            });

    manager.decidePages();
    SwingUtilities.invokeAndWait(manager::createIfNeeded);

    assertEquals(List.of(Thread.currentThread()), deciders, "decided once, on the caller's thread");
    assertFalse(deciders.get(0).getName().startsWith("AWT-EventQueue"));
    // No pages: startup must not wait for a wizard that never opens.
    Thread waiter = new Thread(manager::awaitClosed, "startup");
    waiter.start();
    waiter.join(TimeUnit.SECONDS.toMillis(5));
    assertFalse(waiter.isAlive());
  }

  @Test
  void initializerDecidesThePagesBeforeHoppingToTheEdt() throws Exception {
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
    int decided = body.indexOf("setupManager.decidePages();");
    int created = body.indexOf("GUIMediator.safeInvokeAndWait(setupManager::createIfNeeded);");
    assertTrue(decided >= 0, "the pages must be decided on the startup thread");
    assertTrue(created > decided, "decide first, then build on the EDT");
  }

  /**
   * The save directory page loads {@link com.limegroup.gnutella.settings.SharingSettings}, whose
   * static initializer creates the default download folders: a slow disk stalled the EDT building
   * the page (Icebase #501881c9eb54). The decision, on the startup thread, loads it first.
   */
  @Test
  void theSaveDirectoryPageSettingsAreLoadedWhileDecidingThePages() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/init/SetupManager.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    int start = source.indexOf("privatestaticPagesdecide(){");
    int end = source.indexOf("}", source.indexOf("returnnewPages(", start));
    assertTrue(start >= 0 && end > start);
    String decide = source.substring(start, end);
    assertTrue(
        decide.contains("if(saveDirectory){prepareSaveDirectoryPage();}"),
        "decide() must load the save directory page's settings when it shows that page");
    int prepare = source.indexOf("staticvoidprepareSaveDirectoryPage(){");
    assertTrue(prepare >= 0);
    assertTrue(
        source.substring(prepare, source.indexOf("}", prepare)).contains("SharingSettings."),
        "prepareSaveDirectoryPage() must initialize SharingSettings");
    // and it can run off the EDT without a UI
    SetupManager.prepareSaveDirectoryPage();
  }
}
