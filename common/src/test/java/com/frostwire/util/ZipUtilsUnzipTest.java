/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ZipUtils.unzip must not write outside the output directory (zip-slip) and must cope with zips
 * that have no explicit directory entries.
 */
class ZipUtilsUnzipTest {
  private static File zip(File dir, String... namesAndContents) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
      for (int i = 0; i < namesAndContents.length; i += 2) {
        zos.putNextEntry(new ZipEntry(namesAndContents[i]));
        zos.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
    File zipFile = new File(dir, "test.zip");
    Files.write(zipFile.toPath(), bytes.toByteArray());
    return zipFile;
  }

  @Test
  void entryWithParentTraversalIsRejectedAndNothingEscapes(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out");
    File zipFile = zip(tmp, "d/", "", "../escaped.txt", "evil");

    assertFalse(ZipUtils.unzip(zipFile, out));

    assertFalse(new File(tmp, "escaped.txt").exists(), "entry escaped the output directory");
  }

  @Test
  void entryWithNestedTraversalIsRejected(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out");
    File zipFile = zip(tmp, "d/", "", "d/../../escaped2.txt", "evil");

    assertFalse(ZipUtils.unzip(zipFile, out));

    assertFalse(new File(tmp, "escaped2.txt").exists());
  }

  @Test
  void nestedPathsWithoutDirectoryEntriesAreExtracted(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out");
    File zipFile = zip(tmp, "a/b/c.txt", "deep", "top.txt", "top");

    assertTrue(ZipUtils.unzip(zipFile, out));

    assertEquals("deep", Files.readString(new File(out, "a/b/c.txt").toPath()));
    assertEquals("top", Files.readString(new File(out, "top.txt").toPath()));
  }

  @Test
  void dotSegmentsThatStayInsideTheOutputDirAreStillAccepted(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out");
    File zipFile = zip(tmp, "a/../b.txt", "fine");

    assertTrue(ZipUtils.unzip(zipFile, out));

    assertEquals("fine", Files.readString(new File(out, "b.txt").toPath()));
  }
}
