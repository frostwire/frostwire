/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.filechooser.FileSystemView;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1411: on Windows, the first JFileChooser built on the EDT waited on the shell
 * (COM) for the Desktop, the drives and the start folder's ancestors (Icebase #c12a377b40e1).
 * ShellFolderWarmUp makes the same calls in the background when a component with a Browse button is
 * created, so the one-time shell initialization isn't paid on the click.
 */
class ShellFolderWarmUpTest {

  /** Counts the FileSystemView calls the warm-up makes. */
  private static final class CountingView extends FileSystemView {
    final AtomicInteger comboBoxFiles = new AtomicInteger();
    final List<File> displayNames = new ArrayList<>();

    @Override
    public File createNewFolder(File containingDir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public File[] getChooserComboBoxFiles() {
      comboBoxFiles.incrementAndGet();
      return new File[0];
    }

    @Override
    public String getSystemDisplayName(File f) {
      displayNames.add(f);
      return f.getName();
    }

    @Override
    public File getParentDirectory(File dir) {
      return dir.getParentFile();
    }
  }

  @Test
  void theShellIsAskedInTheBackgroundNotOnTheCallersThread() {
    CountingView view = new CountingView();
    List<Runnable> background = new ArrayList<>();
    ShellFolderWarmUp warmUp = new ShellFolderWarmUp(background::add, () -> view);

    assertTrue(warmUp.warmUp(new File("/a/b/c"), 1_000L));
    assertEquals(0, view.comboBoxFiles.get(), "the shell was asked on the caller's thread");

    background.forEach(Runnable::run);
    assertEquals(1, view.comboBoxFiles.get());
    // The start folder and each ancestor, as JFileChooser's folder combo box resolves them.
    assertEquals(
        List.of(new File("/a/b/c"), new File("/a/b"), new File("/a"), new File("/")),
        view.displayNames);
  }

  @Test
  void itRunsAtMostOncePerRefreshInterval() {
    CountingView view = new CountingView();
    List<Runnable> background = new ArrayList<>();
    ShellFolderWarmUp warmUp = new ShellFolderWarmUp(background::add, () -> view);

    assertTrue(warmUp.warmUp(null, 10_000L));
    assertFalse(warmUp.warmUp(null, 10_000L + ShellFolderWarmUp.REFRESH_MILLIS - 1));
    assertTrue(warmUp.warmUp(null, 10_000L + ShellFolderWarmUp.REFRESH_MILLIS));
    assertEquals(2, background.size());
  }

  @Test
  void aFailingShellIsHarmless() {
    ShellFolderWarmUp warmUp =
        new ShellFolderWarmUp(
            Runnable::run,
            () -> {
              throw new IllegalStateException("COM failed");
            });
    assertTrue(warmUp.warmUp(new File("/a"), 1L));
  }
}
