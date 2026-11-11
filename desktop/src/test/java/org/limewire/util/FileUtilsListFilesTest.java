/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A directory that fails while it is being read (Icebase #4a6ae62712ee) must give the entries read
 * so far instead of an unchecked exception that stops the Library from listing its whole tree.
 */
class FileUtilsListFilesTest {
  @Test
  void aDirectoryThatFailsHalfwayGivesTheEntriesReadSoFar(@TempDir Path directory) {
    Path first = directory.resolve("first.mp3");
    DirectoryStream<Path> failing =
        new DirectoryStream<>() {
          @Override
          public Iterator<Path> iterator() {
            Iterator<Path> entries = List.of(first).iterator();
            return new Iterator<>() {
              @Override
              public boolean hasNext() {
                if (!entries.hasNext()) {
                  throw new DirectoryIteratorException(
                      new IOException("fixture: device not ready"));
                }
                return true;
              }

              @Override
              public Path next() {
                return entries.next();
              }
            };
          }

          @Override
          public void close() {}
        };

    assertArrayEquals(new File[] {first.toAbsolutePath().toFile()}, FileUtils.listFiles(failing));
  }

  @Test
  void aReadableDirectoryListsItsEntriesAsAbsoluteFiles(@TempDir Path directory) throws Exception {
    Files.createFile(directory.resolve("a.txt"));
    Files.createDirectory(directory.resolve("sub"));

    File[] listed = FileUtils.listFiles(directory.toFile());
    Arrays.sort(listed);

    assertArrayEquals(
        new File[] {
          directory.resolve("a.txt").toAbsolutePath().toFile(),
          directory.resolve("sub").toAbsolutePath().toFile()
        },
        listed);
  }

  @Test
  void aMissingDirectoryListsNothing(@TempDir Path directory) {
    assertEquals(0, FileUtils.listFiles(directory.resolve("gone").toFile()).length);
  }
}
