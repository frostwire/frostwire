/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Walks a directory tree with a hard bound on the work it does, so a symlink loop, a huge or a very
 * deep folder can neither hang the caller nor exhaust the heap.
 *
 * <ul>
 *   <li>Symbolic links are followed, the root included, but each real directory is entered at most
 *       once: a link back to an ancestor cannot loop, and two links to the same folder do not walk
 *       it twice. Symbolic links to regular files are reported like regular files.
 *   <li>The walk stops after {@code maxEntries} files and directories and never goes deeper than
 *       {@code maxDepth} levels below the root.
 *   <li>It is iterative ({@link Files#walkFileTree}), so depth does not grow the Java stack, and
 *       the visitor accumulates into a single collection instead of copying one per level.
 * </ul>
 */
public final class BoundedFileWalker {
  private static final Logger LOG = Logger.getLogger(BoundedFileWalker.class);

  /** Files plus directories one walk visits before it gives up. */
  public static final int DEFAULT_MAX_ENTRIES = 250_000;

  /** Directory levels below the root one walk descends. */
  public static final int DEFAULT_MAX_DEPTH = 64;

  /** Receives what the walk finds. Called on the walking thread. */
  public interface Visitor {
    /**
     * Whether to descend into {@code dir}, the root included. Returning false skips it and
     * everything under it.
     */
    default boolean enterDirectory(Path dir) {
      return true;
    }

    /**
     * Called for each regular file, or symbolic link to one, with the attributes of the file it
     * resolves to.
     */
    void file(Path file, BasicFileAttributes attrs);

    /** Called after everything under an entered directory was visited. */
    default void leaveDirectory(Path dir) {}

    /** Checked before each entry; returning true stops the walk. */
    default boolean isCanceled() {
      return false;
    }
  }

  private BoundedFileWalker() {}

  /** Walks {@code root} with the default bounds. */
  public static boolean walk(File root, Visitor visitor) {
    return walk(root, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_DEPTH, visitor);
  }

  /**
   * Walks {@code root}, reporting regular files to {@code visitor}. Unreadable entries are skipped.
   *
   * @return true if the whole tree was walked, false if it stopped at {@code maxEntries} or was
   *     canceled
   */
  public static boolean walk(File root, int maxEntries, int maxDepth, Visitor visitor) {
    if (root == null) {
      return true;
    }
    Path start = root.toPath();
    if (!Files.isDirectory(start)) {
      return true;
    }
    Walk walk = new Walk(maxEntries, visitor);
    try {
      // FOLLOW_LINKS enters symlinked directories and reports a symlinked file with its target's
      // attributes; walk.preVisitDirectory enters each real directory only once.
      Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), maxDepth, walk);
    } catch (IOException e) {
      LOG.warn("BoundedFileWalker: error walking " + root + ": " + e.getMessage());
    }
    if (walk.truncated) {
      LOG.warn(
          "BoundedFileWalker: stopped walking "
              + root
              + " after "
              + maxEntries
              + " entries; results are incomplete");
    }
    return !walk.truncated && !walk.canceled;
  }

  private static final class Walk extends SimpleFileVisitor<Path> {
    private final int maxEntries;
    private final Visitor visitor;

    /** Real directories already entered, so no folder is walked twice through different links. */
    private final Set<Object> enteredDirectories = new HashSet<>();

    private int entries;
    private boolean truncated;
    private boolean canceled;

    Walk(int maxEntries, Visitor visitor) {
      this.maxEntries = maxEntries;
      this.visitor = visitor;
    }

    /** Counts one entry; true when the walk must stop because it is canceled or over budget. */
    private boolean overBudget() {
      if (visitor.isCanceled()) {
        canceled = true;
        return true;
      }
      if (++entries > maxEntries) {
        truncated = true;
        return true;
      }
      return false;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      if (overBudget()) {
        return FileVisitResult.TERMINATE;
      }
      if (!enteredDirectories.add(directoryKey(dir, attrs))) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return visitor.enterDirectory(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
    }

    /**
     * Identity of the real directory behind {@code dir}: its file key (device and inode) where the
     * file system has one, else its real path (Windows).
     */
    private static Object directoryKey(Path dir, BasicFileAttributes attrs) {
      Object key = attrs.fileKey();
      if (key != null) {
        return key;
      }
      try {
        return dir.toRealPath();
      } catch (IOException e) {
        return dir.toAbsolutePath().normalize();
      }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (overBudget()) {
        return FileVisitResult.TERMINATE;
      }
      // At maxDepth directories are reported here instead of being entered.
      if (attrs.isRegularFile()) {
        visitor.file(file, attrs);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      visitor.leaveDirectory(dir);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      // Unreadable entries, broken links and FileSystemLoopException: skip and keep going.
      return overBudget() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
    }
  }
}
