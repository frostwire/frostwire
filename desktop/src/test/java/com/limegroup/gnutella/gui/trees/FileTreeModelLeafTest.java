/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FileTreeModelLeafTest {

  /** A directory that counts every filesystem query made on it. */
  private static final class CountingDir extends File {
    final AtomicInteger fileSystemCalls = new AtomicInteger();

    CountingDir(String path) {
      super(path);
    }

    @Override
    public boolean canRead() {
      fileSystemCalls.incrementAndGet();
      return true;
    }

    @Override
    public boolean isDirectory() {
      fileSystemCalls.incrementAndGet();
      return true;
    }

    @Override
    public File[] listFiles() {
      fileSystemCalls.incrementAndGet();
      return new File[0];
    }

    @Override
    public File[] listFiles(FileFilter filter) {
      fileSystemCalls.incrementAndGet();
      return new File[0];
    }
  }

  @Test
  void directoryNodesAreNotLeavesAndAskingDoesNotTouchTheDisk() {
    FileTreeModel model = new FileTreeModel("");
    CountingDir dir = new CountingDir("/some/slow/network/folder");

    assertFalse(model.isLeaf(dir));
    assertEquals(0, dir.fileSystemCalls.get());
  }

  @Test
  void anEmptyRootIsStillALeaf() {
    assertTrue(new FileTreeModel("").isLeaf(""));
  }
}
