/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * frostwire/frostwire#1420: Swing asks canImport on every drag-motion event, and the Library read
 * the dragged data from the drag source each time (a native call on Windows, Icebase #d6071e410729)
 * to check the files were folders. canImport decides from the target and the data flavors; the drop
 * reads the files once.
 */
class LibraryFolderDropTest {

  private static final DataFlavor[] FILES = {DataFlavor.javaFileListFlavor};

  @TempDir Path dir;

  @Test
  void dragOverAFolderReceiverDoesNotReadTheDraggedData() {
    AtomicInteger reads = new AtomicInteger();
    Callable<File[]> files =
        () -> {
          reads.incrementAndGet();
          return new File[] {dir.toFile()};
        };

    assertTrue(LibraryFilesTransferHandler.droppingFolders(true, FILES, files, true, false));

    assertEquals(0, reads.get(), "the dragged data was read during the drag");
  }

  @Test
  void dragOverAnythingElseIsRefusedWithoutReadingTheData() {
    AtomicInteger reads = new AtomicInteger();
    Callable<File[]> files =
        () -> {
          reads.incrementAndGet();
          return new File[0];
        };

    assertFalse(LibraryFilesTransferHandler.droppingFolders(false, FILES, files, true, false));
    assertFalse(
        LibraryFilesTransferHandler.droppingFolders(
            true, new DataFlavor[] {DataFlavor.stringFlavor}, files, true, false));
    assertEquals(0, reads.get());
  }

  @Test
  void theDropAddsOnlyFolders() throws Exception {
    File folder = Files.createDirectory(dir.resolve("music")).toFile();
    File file = Files.writeString(dir.resolve("song.mp3"), "x").toFile();

    assertTrue(
        LibraryFilesTransferHandler.droppingFolders(
            true, FILES, () -> new File[] {folder}, false, false));
    assertFalse(
        LibraryFilesTransferHandler.droppingFolders(
            true, FILES, () -> new File[] {folder, file}, false, false));
  }
}
