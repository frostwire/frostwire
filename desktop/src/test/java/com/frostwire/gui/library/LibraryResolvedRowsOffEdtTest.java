/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A file's size and date, read in the background, must not make the EDT wait for the table's list
 * lock while a sort or a bulk add holds it (Icebase #a09fb9913779, frostwire/frostwire#1543).
 */
class LibraryResolvedRowsOffEdtTest {

  @TempDir Path tmp;

  @Test
  void theEdtKeepsDispatchingWhileAResolvedRowWaitsForTheListLock() throws Exception {
    File file = Files.write(tmp.resolve("song.mp3"), new byte[1234]).toFile();
    LibraryFilesTableDataLine.ResolvedFile resolved = LibraryFilesTableDataLine.resolve(file);
    LibraryFilesTableModel model = new LibraryFilesTableModel();
    SwingUtilities.invokeAndWait(() -> model.addResolved(List.of(resolved)));
    Method getListLock =
        model.getClass().getSuperclass().getSuperclass().getDeclaredMethod("getListLock");
    getListLock.setAccessible(true);
    Object listLock = getListLock.invoke(model);

    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              synchronized (listLock) { // a long sort or bulk add
                locked.countDown();
                try {
                  release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
              }
            },
            "list-lock-holder");
    holder.start();
    assertTrue(locked.await(5, TimeUnit.SECONDS));
    try {
      // what the app does: the row reads the file in the background and posts what it found
      model.createDataLine().initialize(resolved);
      long end = System.currentTimeMillis() + 600;
      while (System.currentTimeMillis() < end) {
        CountDownLatch probe = new CountDownLatch(1);
        SwingUtilities.invokeLater(probe::countDown);
        assertTrue(
            probe.await(100, TimeUnit.MILLISECONDS),
            "the EDT stopped dispatching while another thread held the list lock");
        Thread.sleep(20);
      }
    } finally {
      release.countDown();
      holder.join(10_000);
    }
    assertEquals(1, model.getRowCount());
  }
}
