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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZipUtilsTest {

  private static File makeZip(File dir, String... namesAndContents) throws IOException {
    File zip = new File(dir, "test.zip");
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
      for (int i = 0; i < namesAndContents.length; i += 2) {
        out.putNextEntry(new ZipEntry(namesAndContents[i]));
        if (!namesAndContents[i].endsWith("/")) {
          out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        out.closeEntry();
      }
    }
    return zip;
  }

  @Test
  void unzipsFilesAndFoldersAndReportsProgress(@TempDir File tmp) throws IOException {
    File zip = makeZip(tmp, "folder/", "", "folder/a.txt", "alpha", "b.txt", "bravo");
    File out = new File(tmp, "out");
    List<String> names = new ArrayList<>();
    List<Integer> progress = new ArrayList<>();
    boolean ok =
        ZipUtils.unzip(
            zip,
            out,
            new ZipUtils.ZipListener() {
              public void onUnzipping(String fileName, int p) {
                names.add(fileName);
                progress.add(p);
              }

              public boolean isCanceled() {
                return false;
              }
            });
    assertTrue(ok);
    assertEquals("alpha", Files.readString(new File(out, "folder/a.txt").toPath()));
    assertEquals("bravo", Files.readString(new File(out, "b.txt").toPath()));
    assertEquals(List.of("folder/a.txt", "b.txt"), names);
    assertEquals(100, progress.get(progress.size() - 1));
    assertTrue(progress.get(0) < 100);
  }

  @Test
  void unzipReplacesTheOutputDirectory(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out");
    assertTrue(out.mkdirs());
    Files.writeString(new File(out, "stale.txt").toPath(), "old");
    File zip = makeZip(tmp, "dir/", "", "dir/fresh.txt", "new");
    assertTrue(ZipUtils.unzip(zip, out));
    assertFalse(new File(out, "stale.txt").exists());
    assertEquals("new", Files.readString(new File(out, "dir/fresh.txt").toPath()));
  }

  @Test
  void unzipFailsOnMissingOrCorruptArchives(@TempDir File tmp) throws IOException {
    assertFalse(ZipUtils.unzip(new File(tmp, "missing.zip"), new File(tmp, "out")));
    File notZip = new File(tmp, "bad.zip");
    Files.writeString(notZip.toPath(), "this is not a zip file");
    assertFalse(ZipUtils.unzip(notZip, new File(tmp, "out2")));
  }

  @Test
  void cancellationAbortsTheUnzip(@TempDir File tmp) throws IOException {
    File zip = makeZip(tmp, "d/", "", "d/a.txt", "alpha", "d/b.txt", "bravo");
    boolean ok =
        ZipUtils.unzip(
            zip,
            new File(tmp, "out"),
            new ZipUtils.ZipListener() {
              public void onUnzipping(String fileName, int p) {}

              public boolean isCanceled() {
                return true;
              }
            });
    assertFalse(ok);
  }
}
