/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.util.BoundedFileWalker;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Torrent Data scan behind the Library's saved files must terminate with bounded results: a
 * symlink loop used to make it recurse until the heap ran out (OutOfMemoryError in isPartsFile).
 */
class SavedFilesDirectoryHolderTest {

  @TempDir Path tmp;

  private static boolean symlink(Path link, Path target) {
    try {
      Files.createSymbolicLink(link, target);
      return true;
    } catch (IOException | UnsupportedOperationException e) {
      return false;
    }
  }

  @Test
  void symlinkLoopsInTorrentDataTerminateWithEachFileOnce() throws Exception {
    Path movie = Files.createDirectories(tmp.resolve("Movie"));
    File video = Files.createFile(movie.resolve("movie.mp4")).toFile();
    assumeTrue(symlink(movie.resolve("back"), tmp) && symlink(movie.resolve("self"), movie));

    Set<File> files =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10),
            () ->
                SavedFilesDirectoryHolder.collectFiles(
                    tmp.toFile(), Set.of(), Set.of(), BoundedFileWalker.DEFAULT_MAX_ENTRIES));

    assertEquals(Set.of(video), files);
  }

  @Test
  void filtersHiddenPartsDsStoreAndExcludedFolders() throws Exception {
    File keep = Files.createFile(tmp.resolve("keep.mp3")).toFile();
    File hidden = Files.createFile(tmp.resolve("incomplete.mp3")).toFile();
    Files.createFile(tmp.resolve(".abc.parts"));
    Files.createFile(tmp.resolve(".DS_Store"));
    Path excluded = Files.createDirectories(tmp.resolve("excluded"));
    Files.createFile(excluded.resolve("other.mp3"));

    Set<File> files =
        SavedFilesDirectoryHolder.collectFiles(
            tmp.toFile(),
            Set.of(excluded.toFile()),
            Set.of(hidden),
            BoundedFileWalker.DEFAULT_MAX_ENTRIES);

    assertEquals(Set.of(keep), files);
  }

  @Test
  void hugeFolderIsCappedInsteadOfExhaustingTheHeap() throws Exception {
    for (int i = 0; i < 200; i++) {
      Files.createFile(tmp.resolve("f" + i + ".mp3"));
    }

    Set<File> files = SavedFilesDirectoryHolder.collectFiles(tmp.toFile(), Set.of(), Set.of(), 50);

    assertTrue(files.size() < 50, "collected " + files.size() + " files");
  }
}
