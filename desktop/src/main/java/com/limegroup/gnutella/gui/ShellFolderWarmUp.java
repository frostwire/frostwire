/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.util.WarmExecutors;
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
 * button is created takes most of that wait off the click. Every class that opens a {@link
 * FileChooserHandler} chooser starts one when it is built, and the startup thread starts the first,
 * so the one-time shell setup is done before any click.
 */
public final class ShellFolderWarmUp {
  private static final Logger LOG = Logger.getLogger(ShellFolderWarmUp.class);

  /** Warm up again only after this long: the shell's own caches don't last forever. */
  static final long REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(5);

  /** Ancestors deeper than this aren't walked; no real path is this deep. */
  private static final int MAX_DEPTH = 64;

  private static final ShellFolderWarmUp INSTANCE =
      new ShellFolderWarmUp(
          // Its own worker, so a slow shell doesn't hold up BackgroundExecutorService's queue, and
          // one that is always running: the components that start a warm-up are built on the EDT,
          // which must not start a thread.
          WarmExecutors.serialQueue("ShellFolderWarmUp"), FileSystemView::getFileSystemView);

  private final Executor executor;
  private final Supplier<FileSystemView> view;

  /** When the last warm-up was started; 0 before the first. */
  private final AtomicLong lastWarmUp = new AtomicLong();

  ShellFolderWarmUp(Executor executor, Supplier<FileSystemView> view) {
    this.executor = executor;
    this.view = view;
  }

  /**
   * On Windows, starts a background warm-up for a chooser opening at {@link
   * FileChooserHandler#getLastInputDirectory()}, unless one ran in the last {@link
   * #REFRESH_MILLIS}. Returns at once: that folder is looked up on the warm-up thread, since
   * checking it touches the disk.
   */
  public static void warmUpForDirectoryChooser() {
    if (OSUtils.isWindows()) {
      INSTANCE.warmUp(FileChooserHandler::getLastInputDirectory, System.currentTimeMillis());
    }
  }

  /** Whether a warm-up was started; {@code startDirectory} is read on the executor. */
  boolean warmUp(Supplier<File> startDirectory, long nowMillis) {
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

  private void run(Supplier<File> startDirectory) {
    try {
      FileSystemView fsv = view.get();
      fsv.getChooserComboBoxFiles();
      File folder = startDirectory.get();
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
