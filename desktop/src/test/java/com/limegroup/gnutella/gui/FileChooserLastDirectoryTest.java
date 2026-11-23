/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.FileChooserHandler.Resolution;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.limewire.util.CommonUtils;

/**
 * Opening a file chooser asked the disk whether the saved directory still exists on the EDT, which
 * froze the window for seconds on a disconnected network drive (Icebase #a8bcddbf3e81,
 * #597016347eb8, frostwire/frostwire#1411).
 */
class FileChooserLastDirectoryTest {
  private static final File SAVED = new File("/saved/downloads");

  @Test
  void theEdtNeverAsksTheDiskAndFallsBackToTheCurrentDirectoryUntilTheLookupFinishes() {
    AtomicInteger diskLookups = new AtomicInteger();
    List<Runnable> background = new ArrayList<>();
    AtomicReference<Resolution> resolved = new AtomicReference<>();

    File first =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED,
            true,
            dir -> {
              diskLookups.incrementAndGet();
              return true;
            },
            background::add,
            resolved);

    assertEquals(0, diskLookups.get());
    assertEquals(CommonUtils.getCurrentDirectory(), first);
    assertEquals(1, background.size());

    background.get(0).run();
    assertEquals(1, diskLookups.get());
    File second =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED, true, dir -> false, background::add, resolved);
    assertEquals(SAVED, second);
    assertEquals(1, diskLookups.get());
    assertEquals(1, background.size());
  }

  @Test
  void aSavedDirectoryThatIsGoneFallsBackToTheCurrentDirectory() {
    AtomicReference<Resolution> resolved = new AtomicReference<>();
    File viaWorker =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED, false, dir -> false, task -> {}, resolved);
    assertEquals(CommonUtils.getCurrentDirectory(), viaWorker);
    assertEquals(
        CommonUtils.getCurrentDirectory(),
        FileChooserHandler.resolveLastInputDirectory(
            SAVED, true, dir -> true, task -> {}, resolved));
  }

  @Test
  void aNonEdtThreadLooksItselfAndWarmsTheAnswerForTheEdt() {
    AtomicInteger diskLookups = new AtomicInteger();
    AtomicReference<Resolution> resolved = new AtomicReference<>();
    File viaWorker =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED,
            false,
            dir -> {
              diskLookups.incrementAndGet();
              return true;
            },
            task -> {},
            resolved);
    assertEquals(SAVED, viaWorker);
    assertEquals(1, diskLookups.get());

    File onEdt =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED, true, dir -> false, task -> {}, resolved);
    assertEquals(SAVED, onEdt);
    assertEquals(1, diskLookups.get());
  }

  @Test
  void aChangedSettingIsLookedUpAgain() {
    AtomicReference<Resolution> resolved = new AtomicReference<>(new Resolution("/old", true));
    List<Runnable> background = new ArrayList<>();
    File onEdt =
        FileChooserHandler.resolveLastInputDirectory(
            SAVED, true, dir -> true, background::add, resolved);
    assertEquals(CommonUtils.getCurrentDirectory(), onEdt);
    assertEquals(1, background.size());
  }

  @Test
  void noSavedDirectoryMeansTheCurrentDirectoryWithoutAnyLookup() {
    AtomicInteger diskLookups = new AtomicInteger();
    for (File none : new File[] {null, new File("")}) {
      File dir =
          FileChooserHandler.resolveLastInputDirectory(
              none,
              true,
              d -> {
                diskLookups.incrementAndGet();
                return true;
              },
              task -> diskLookups.incrementAndGet(),
              new AtomicReference<>());
      assertEquals(CommonUtils.getCurrentDirectory(), dir);
    }
    assertTrue(diskLookups.get() == 0);
  }

  @Test
  void startupLooksUpTheSavedDirectoryOnAParallelExecutor() throws java.io.IOException {
    String initializer =
        java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        initializer.contains(
            "DesktopParallelExecutor.execute(FileChooserHandler::getLastInputDirectory);"));
  }
}
