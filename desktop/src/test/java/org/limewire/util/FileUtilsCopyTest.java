/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsCopyTest {
  @Test
  void copyOfAMissingSourceFailsAndCreatesNothing(@TempDir File tmp) {
    File dst = new File(tmp, "dst.bin");

    assertFalse(FileUtils.copy(new File(tmp, "missing.bin"), dst));

    assertFalse(dst.exists(), "a failed copy must not leave an empty destination behind");
  }

  @Test
  void copyOfADirectoryFails(@TempDir File tmp) {
    File dst = new File(tmp, "dst.bin");

    assertFalse(FileUtils.copy(tmp, dst));

    assertFalse(dst.exists());
  }

  @Test
  void copyCopiesTheContent(@TempDir File tmp) throws IOException {
    File src = new File(tmp, "src.bin");
    byte[] content = "some content".repeat(1000).getBytes(StandardCharsets.UTF_8);
    Files.write(src.toPath(), content);
    File dst = new File(tmp, "dst.bin");

    assertTrue(FileUtils.copy(src, dst));

    assertArrayEquals(content, Files.readAllBytes(dst.toPath()));
  }

  @Test
  void copyOfAnEmptyFileSucceeds(@TempDir File tmp) throws IOException {
    File src = new File(tmp, "empty.bin");
    assertTrue(src.createNewFile());
    File dst = new File(tmp, "dst.bin");

    assertTrue(FileUtils.copy(src, dst));

    assertEquals(0, dst.length());
  }

  /**
   * The length used to be cast to int, so anything over 2 GiB failed. The source is sparse (it
   * takes no disk space) and the destination is /dev/null, so nothing big is ever stored.
   */
  @Test
  void copyOfAFileOverTwoGiBSucceeds(@TempDir File tmp) throws IOException {
    File devNull = new File("/dev/null");
    Assumptions.assumeTrue(
        devNull.exists() && !System.getProperty("os.name").startsWith("Windows"));
    File src = new File(tmp, "sparse.bin");
    long size = (1L << 31) + 5;
    try (RandomAccessFile raf = new RandomAccessFile(src, "rw")) {
      raf.setLength(size);
    }
    Assumptions.assumeTrue(src.length() == size);

    assertTrue(FileUtils.copy(src, devNull));
  }

  @Test
  void buildFileOfANameWithoutExtensionHasNoTrailingDot(@TempDir File tmp) throws IOException {
    assertTrue(new File(tmp, "README").createNewFile());

    assertEquals("README (1)", FileUtils.buildFile(tmp, "README").getName());

    assertTrue(new File(tmp, "README (1)").createNewFile());
    assertEquals("README (2)", FileUtils.buildFile(tmp, "README").getName());
  }

  @Test
  void buildFileKeepsTheExtension(@TempDir File tmp) throws IOException {
    assertEquals("a.txt", FileUtils.buildFile(tmp, "a.txt").getName());
    assertTrue(new File(tmp, "a.txt").createNewFile());
    assertEquals("a (1).txt", FileUtils.buildFile(tmp, "a.txt").getName());
  }
}
