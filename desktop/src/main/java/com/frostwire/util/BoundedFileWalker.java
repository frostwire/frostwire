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

/**
 * Walks a directory tree with a hard bound on the work it does, so a symlink loop, a huge or a very
 * deep folder can neither hang the caller nor exhaust the heap.
 *
 * <ul>
 *   <li>The root is followed if it is a symbolic link; symbolic links to directories below it are
 *       not entered, so a link back to an ancestor (or anywhere else) cannot loop or duplicate a
 *       tree. Symbolic links to regular files are reported like regular files.
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
  }

  private BoundedFileWalker() {}

  /** Walks {@code root} with the default bounds. */
  public static boolean walk(File root, Visitor visitor) {
    return walk(root, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_DEPTH, visitor);
  }

  /**
   * Walks {@code root}, reporting regular files to {@code visitor}. Unreadable entries are skipped.
   *
   * @return true if the whole tree was walked, false if it stopped at {@code maxEntries}
   */
  public static boolean walk(File root, int maxEntries, int maxDepth, Visitor visitor) {
    if (root == null) {
      return true;
    }
    Path start = root.toPath();
    if (!Files.isDirectory(start)) {
      return true;
    }
    Walk walk = new Walk(start, maxEntries, visitor);
    try {
      // FOLLOW_LINKS lets a symlinked root through and reports a symlinked file with its target's
      // attributes; walk.preVisitDirectory refuses every symlinked directory below the root.
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
    return !walk.truncated;
  }

  private static final class Walk extends SimpleFileVisitor<Path> {
    private final Path start;
    private final int maxEntries;
    private final Visitor visitor;
    private int entries;
    private boolean truncated;

    Walk(Path start, int maxEntries, Visitor visitor) {
      this.start = start;
      this.maxEntries = maxEntries;
      this.visitor = visitor;
    }

    private boolean overBudget() {
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
      if (!dir.equals(start) && Files.isSymbolicLink(dir)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return visitor.enterDirectory(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
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
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      // Unreadable entries, broken links and FileSystemLoopException: skip and keep going.
      return overBudget() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
    }
  }
}
