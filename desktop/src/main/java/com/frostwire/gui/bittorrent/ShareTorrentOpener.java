/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opens the share dialog for a transfer's torrent. Reading the torrent asks jlibtorrent for the
 * torrent's metadata (a JNI call that takes the session's lock) and reads the .torrent file, which
 * froze the EDT when the share button or "Send to friend" was clicked (GitHub #1458). It is read on
 * a background worker, and the dialog opens on the EDT.
 */
final class ShareTorrentOpener {
  private static final Logger LOG = Logger.getLogger(ShareTorrentOpener.class);

  private ShareTorrentOpener() {}

  /** Opens the share dialog for {@code download}'s torrent; does nothing if it can't be read. */
  static void open(BittorrentDownload download) {
    open(
        download::getTorrentInfo,
        // Its button icons are read from the jar too (frostwire/frostwire#1514).
        ShareTorrentDialog::preloadButtonIcons,
        DesktopParallelExecutor::execute,
        info -> new ShareTorrentDialog(GUIMediator.getAppFrame(), info).setVisible(true));
  }

  /**
   * Reads the torrent with {@code read} on {@code background}, then runs {@code show} on the EDT.
   */
  static <T> void open(Supplier<T> read, Executor background, Consumer<T> show) {
    open(read, () -> {}, background, show);
  }

  /**
   * Reads the torrent with {@code read} and, once it was read, runs {@code prepare} (work the
   * dialog would otherwise do on the EDT), both on {@code background}; then runs {@code show} on
   * the EDT.
   */
  static <T> void open(Supplier<T> read, Runnable prepare, Executor background, Consumer<T> show) {
    background.execute(
        () -> {
          T info;
          try {
            info = read.get();
          } catch (RuntimeException e) {
            // A transfer whose .torrent was moved, or whose metadata isn't there yet.
            LOG.warn("Can't read the torrent to share: " + e.getMessage());
            return;
          }
          if (info != null) {
            try {
              prepare.run();
            } catch (RuntimeException e) {
              // Only a head start: the dialog loads what is missing itself.
              LOG.warn("Can't prepare the share dialog: " + e.getMessage());
            }
            GUIMediator.safeInvokeLater(() -> show.accept(info));
          }
        });
  }
}
