/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A directory walk must terminate with bounded results on symlink loops and deep trees. */
class BoundedFileWalkerTest {

  @TempDir Path tmp;

  private static Set<Path> walkAll(Path root, int maxEntries, int maxDepth) {
    Set<Path> found = new HashSet<>();
    BoundedFileWalker.walk(root.toFile(), maxEntries, maxDepth, (p, attrs) -> found.add(p));
    return found;
  }

  private static boolean symlink(Path link, Path target) {
    try {
      Files.createSymbolicLink(link, target);
      return true;
    } catch (IOException | UnsupportedOperationException e) {
      return false;
    }
  }

  @Test
  void symlinkLoopsAreNotFollowed() throws Exception {
    Path a = Files.createDirectories(tmp.resolve("a"));
    Path song = Files.createFile(a.resolve("song.mp3"));
    // Two links back to the parent at every level: following them explodes exponentially.
    assumeTrue(symlink(a.resolve("loop1"), a) && symlink(a.resolve("loop2"), tmp));

    Set<Path> found =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> walkAll(tmp, BoundedFileWalker.DEFAULT_MAX_ENTRIES, 64));

    assertEquals(Set.of(song), found);
  }

  @Test
  void symlinkedRootAndSymlinkedFilesAreReported() throws Exception {
    Path real = Files.createDirectories(tmp.resolve("real"));
    Path song = Files.createFile(real.resolve("song.mp3"));
    Path linkedRoot = tmp.resolve("linkedRoot");
    assumeTrue(symlink(linkedRoot, real));
    assumeTrue(symlink(real.resolve("alias.mp3"), song));

    Set<Path> found = walkAll(linkedRoot, 100, 64);

    assertEquals(Set.of(linkedRoot.resolve("song.mp3"), linkedRoot.resolve("alias.mp3")), found);
  }

  @Test
  void deepTreeStopsAtMaxDepth() throws Exception {
    Path dir = tmp;
    List<Path> files = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      dir = Files.createDirectories(dir.resolve("d" + i));
      files.add(Files.createFile(dir.resolve("f" + i)));
    }

    Set<Path> found = walkAll(tmp, 100_000, 10);

    // d0..d8 hold f0..f8 at depths 2..10; f9 is at depth 11.
    assertEquals(Set.copyOf(files.subList(0, 9)), found);
  }

  @Test
  void stopsAfterMaxEntriesAndSaysSo() throws Exception {
    for (int i = 0; i < 50; i++) {
      Files.createFile(tmp.resolve("f" + i));
    }
    Set<Path> found = new HashSet<>();

    boolean complete = BoundedFileWalker.walk(tmp.toFile(), 10, 64, (p, a) -> found.add(p));

    assertFalse(complete);
    assertTrue(found.size() < 10, "visited " + found.size() + " files");
  }

  @Test
  void cancelStopsTheWalkAndEveryEnteredDirectoryIsLeft() throws Exception {
    Path sub = Files.createDirectories(tmp.resolve("sub"));
    for (int i = 0; i < 20; i++) {
      Files.createFile(sub.resolve("f" + i));
    }
    List<Path> left = new ArrayList<>();
    int[] seen = {0};

    boolean complete =
        BoundedFileWalker.walk(
            tmp.toFile(),
            new BoundedFileWalker.Visitor() {
              @Override
              public void file(Path file, BasicFileAttributes attrs) {
                seen[0]++;
              }

              @Override
              public void leaveDirectory(Path dir) {
                left.add(dir);
              }
            });
    assertTrue(complete);
    assertEquals(List.of(sub, tmp), left);

    seen[0] = 0;
    complete =
        BoundedFileWalker.walk(
            tmp.toFile(),
            new BoundedFileWalker.Visitor() {
              @Override
              public void file(Path file, BasicFileAttributes attrs) {
                seen[0]++;
              }

              @Override
              public boolean isCanceled() {
                return seen[0] >= 5;
              }
            });
    assertFalse(complete);
    assertEquals(5, seen[0]);
  }

  @Test
  void excludedDirectoriesAndMissingRootsAreSkipped() throws Exception {
    Path keep = Files.createFile(tmp.resolve("keep.mp3"));
    Path skipped = Files.createDirectories(tmp.resolve("skipped"));
    Files.createFile(skipped.resolve("hidden.mp3"));
    Set<Path> found = new HashSet<>();

    boolean complete =
        BoundedFileWalker.walk(
            tmp.toFile(),
            new BoundedFileWalker.Visitor() {
              @Override
              public boolean enterDirectory(Path dir) {
                return !dir.equals(skipped);
              }

              @Override
              public void file(Path file, BasicFileAttributes attrs) {
                found.add(file);
              }
            });

    assertTrue(complete);
    assertEquals(Set.of(keep), found);
    assertTrue(BoundedFileWalker.walk(new File(tmp.toFile(), "missing"), (p, a) -> found.add(p)));
  }
}
