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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Showing a Library folder must not touch the file system on the EDT (GitHub #1345): canonical
 * paths and directory flags are read in the background, and rows are added in batches with one
 * table event each, not one event per file.
 */
class LibraryFilesTableRowsOffEdtTest {

  @TempDir Path tmp;

  /**
   * Records the threads that asked the file system for the canonical path or the directory flag.
   */
  private static final class RecordingFile extends File {
    final Set<String> fileSystemThreads;

    RecordingFile(File file, Set<String> fileSystemThreads) {
      super(file.getPath());
      this.fileSystemThreads = fileSystemThreads;
    }

    @Override
    public String getCanonicalPath() throws IOException {
      fileSystemThreads.add(Thread.currentThread().getName());
      return super.getCanonicalPath();
    }

    @Override
    public boolean isDirectory() {
      fileSystemThreads.add(Thread.currentThread().getName());
      return super.isDirectory();
    }
  }

  @Test
  void rowsAreAddedOnTheEdtWithoutFileSystemCallsAndInOneEvent() throws Exception {
    Set<String> fileSystemThreads = ConcurrentHashMap.newKeySet();
    List<File> files = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      files.add(
          new RecordingFile(
              Files.createFile(tmp.resolve("song" + i + ".mp3")).toFile(), fileSystemThreads));
    }
    files.add(
        new RecordingFile(
            Files.createDirectories(tmp.resolve("folder")).toFile(), fileSystemThreads));

    // what the background loop does
    List<LibraryFilesTableDataLine.ResolvedFile> resolved =
        LibraryFilesTableDataLine.resolveAll(files);
    assertTrue(fileSystemThreads.contains(Thread.currentThread().getName()));
    fileSystemThreads.clear();

    LibraryFilesTableModel model = new LibraryFilesTableModel();
    int[] events = {0};
    String[] edt = {null};
    SwingUtilities.invokeAndWait(
        () -> {
          edt[0] = Thread.currentThread().getName();
          // size and date arrive later from the background as row updates; count the inserts
          model.addTableModelListener(
              e -> {
                if (e.getType() == TableModelEvent.INSERT) {
                  events[0]++;
                }
              });
          model.addResolved(resolved);
          // a second batch with the same files adds nothing and fires nothing
          model.addResolved(resolved);
        });

    assertFalse(
        fileSystemThreads.contains(edt[0]),
        "the file system was read on the EDT while adding rows");
    assertEquals(1, events[0], "one insert event per batch, none for a batch of duplicates");
    assertEquals(files.size(), model.getRowCount());
    for (int row = 0; row < files.size(); row++) {
      assertEquals(row, model.getRow(files.get(row)), "index of " + files.get(row));
    }
  }

  @Test
  void everyPathThatShowsLibraryRowsResolvesFilesInTheBackground() throws Exception {
    Path library = Path.of("src/main/java/com/frostwire/gui/library");
    String mediator = source(library.resolve("LibraryFilesTableMediator.java"));
    assertFalse(mediator.contains("addUnsorted(file)"), "one EDT event per file is back");
    assertTrue(mediator.contains("LibraryFilesTableDataLine.resolveAll(partition)"));
  }

  private static String source(Path path) throws IOException {
    return Files.readString(path).replaceAll("\\s+", "");
  }
}
