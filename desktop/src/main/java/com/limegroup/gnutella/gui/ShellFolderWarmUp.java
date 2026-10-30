/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.swing.filechooser.FileSystemView;

/**
 * Asks the Windows shell, in the background, for what a new JFileChooser asks it for on the EDT.
 *
 * <p>On Windows, building a JFileChooser fills its folder combo box from the shell, over COM: the
 * Desktop, the drives, and the start folder with each of its ancestors. The EDT waits for those
 * calls, and the first ones in a process also start the shell's COM thread and bind the Desktop
 * namespace. With slow, network or cloud-synced drives, clicking Browse froze FrostWire for seconds
 * (frostwire/frostwire#1411). The JDK doesn't cache those answers, but the one-time setup and much
 * of what Windows looks up stay warm, so making the same calls when a component with a Browse
 * button is created takes most of that wait off the click.
 */
public final class ShellFolderWarmUp {
  private static final Logger LOG = Logger.getLogger(ShellFolderWarmUp.class);

  /** Warm up again only after this long: the shell's own caches don't last forever. */
  static final long REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(5);

  /** Ancestors deeper than this aren't walked; no real path is this deep. */
  private static final int MAX_DEPTH = 64;

  private static final ShellFolderWarmUp INSTANCE =
      new ShellFolderWarmUp(
          task -> {
            // Its own thread: a slow shell must not hold up BackgroundExecutorService's queue.
            Thread thread = new Thread(task, "ShellFolderWarmUp");
            thread.setDaemon(true);
            thread.start();
          },
          FileSystemView::getFileSystemView);

  private final Executor executor;
  private final Supplier<FileSystemView> view;

  /** When the last warm-up was started; 0 before the first. */
  private final AtomicLong lastWarmUp = new AtomicLong();

  ShellFolderWarmUp(Executor executor, Supplier<FileSystemView> view) {
    this.executor = executor;
    this.view = view;
  }

  /**
   * On Windows, starts a background warm-up for a folder chooser opening at {@code startDirectory}
   * (null for the default), unless one ran in the last {@link #REFRESH_MILLIS}. Returns at once.
   */
  public static void warmUpForDirectoryChooser(File startDirectory) {
    if (OSUtils.isWindows()) {
      INSTANCE.warmUp(startDirectory, System.currentTimeMillis());
    }
  }

  /** Whether a warm-up was started. */
  boolean warmUp(File startDirectory, long nowMillis) {
    long last = lastWarmUp.get();
    if (last != 0 && nowMillis - last < REFRESH_MILLIS) {
      return false;
    }
    if (!lastWarmUp.compareAndSet(last, nowMillis)) {
      return false;
    }
    executor.execute(() -> run(startDirectory));
    return true;
  }

  private void run(File startDirectory) {
    try {
      FileSystemView fsv = view.get();
      fsv.getChooserComboBoxFiles();
      File folder = startDirectory;
      for (int depth = 0; folder != null && depth < MAX_DEPTH; depth++) {
        fsv.getSystemDisplayName(folder);
        folder = fsv.getParentDirectory(folder);
      }
    } catch (RuntimeException e) {
      // Best effort: the chooser asks again on the EDT, as it always did.
      LOG.debug("Shell folder warm-up failed: " + e.getMessage());
    }
  }
}
