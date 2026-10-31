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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * jlibtorrent up to 2.0.12.9 copied its native library on every start to {@code
 * Files.createTempFile("jlibtorrent-", suffix)} and only asked for it to be deleted on exit. Android
 * never runs exit hooks and Windows can't delete a loaded DLL, so every start left a 13 MB (Android)
 * to 30 MB copy behind. 2.0.15.x no longer makes them; FrostWire deletes the ones left over. A
 * copy made in the last hour may belong to another app on old jlibtorrent that is loading it now,
 * so it stays.
 */
class StaleJlibtorrentCopiesTest {

  @TempDir Path dir;

  @Test
  void deletesTheCopiesOldJlibtorrentLeftInTheTempFolder() throws Exception {
    File so = touch("jlibtorrent-5023218823651297221.so");
    File dll = touch("jlibtorrent-118.dll");
    File dylib = touch("jlibtorrent-7.dylib");

    assertEquals(3, StaleJlibtorrentCopies.delete(dir.toFile()));

    assertFalse(so.exists());
    assertFalse(dll.exists());
    assertFalse(dylib.exists());
  }

  @Test
  void keepsACopyMadeInTheLastHour() throws Exception {
    File fresh = touch("jlibtorrent-42.so");
    assertTrue(fresh.setLastModified(System.currentTimeMillis() - 5 * 60_000L));

    assertEquals(0, StaleJlibtorrentCopies.delete(dir.toFile()));

    assertTrue(fresh.exists());
  }

  @Test
  void leavesEverythingElse() throws Exception {
    File[] kept = {
      touch("jlibtorrent-abc.so"),
      touch("jlibtorrent.so"),
      touch("libjlibtorrent.so"),
      touch("jlibtorrent-12.so.part"),
      touch("jlibtorrent-12.txt"),
      touch("other-12.so"),
    };
    Files.createDirectories(dir.resolve("jlibtorrent-99.so"));

    assertEquals(0, StaleJlibtorrentCopies.delete(dir.toFile()));

    for (File f : kept) {
      assertTrue(f.exists(), f.getName());
    }
    assertTrue(dir.resolve("jlibtorrent-99.so").toFile().isDirectory());
  }

  @Test
  void aMissingFolderIsNothingToDo() {
    assertEquals(0, StaleJlibtorrentCopies.delete(dir.resolve("missing").toFile()));
    assertEquals(0, StaleJlibtorrentCopies.delete(null));
  }

  /** A file last modified two hours ago, as a copy left by an earlier start would be. */
  private File touch(String name) throws Exception {
    File f = Files.write(dir.resolve(name), new byte[] {1}).toFile();
    assertTrue(f.setLastModified(System.currentTimeMillis() - 2 * 3_600_000L));
    return f;
  }
}
