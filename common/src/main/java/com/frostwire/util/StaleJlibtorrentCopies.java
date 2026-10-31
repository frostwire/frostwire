/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Deletes the native library copies jlibtorrent up to 2.0.12.9 left in the temp folder.
 *
 * <p>Those versions copied the library to {@code Files.createTempFile("jlibtorrent-", suffix)} on
 * every start and relied on {@code deleteOnExit()}, which Android never runs (the system kills the
 * process) and which can't delete a loaded DLL on Windows. Each start left a 13 MB (Android) to 30
 * MB copy behind. From 2.0.15.0 jlibtorrent loads the installed library on Android and keeps one
 * copy per version under its own cache folder on desktop, so nothing it uses now has these names.
 * A copy made in the last hour is kept: the desktop temp folder is shared with other Java apps, and
 * one still on old jlibtorrent may be loading it right now.
 */
public final class StaleJlibtorrentCopies {
  private static final Logger LOG = Logger.getLogger(StaleJlibtorrentCopies.class);

  /** {@code createTempFile("jlibtorrent-", ".so")} and friends: the prefix, digits, the suffix. */
  private static final Pattern NAME = Pattern.compile("jlibtorrent-\\d+\\.(so|dll|dylib)");

  private static final long MIN_AGE_MILLIS = TimeUnit.HOURS.toMillis(1);

  private static final AtomicBoolean STARTED = new AtomicBoolean();

  private StaleJlibtorrentCopies() {}

  /** Deletes them from the JVM's temp folder on a background thread, once per process. */
  public static void deleteInBackground() {
    String tmp = System.getProperty("java.io.tmpdir");
    if (tmp == null || tmp.isEmpty() || !STARTED.compareAndSet(false, true)) {
      return;
    }
    Thread thread =
        new Thread(
            () -> {
              int deleted = delete(new File(tmp));
              if (deleted > 0) {
                LOG.info("Deleted " + deleted + " stale jlibtorrent library copies from " + tmp);
              }
            },
            "StaleJlibtorrentCopies");
    thread.setDaemon(true);
    thread.start();
  }

  /** Deletes the copies in {@code dir}; returns how many were deleted. */
  static int delete(File dir) {
    long cutoff = System.currentTimeMillis() - MIN_AGE_MILLIS;
    File[] copies =
        dir == null
            ? null
            : dir.listFiles(
                f ->
                    f.isFile()
                        && NAME.matcher(f.getName()).matches()
                        && f.lastModified() < cutoff);
    if (copies == null) {
      return 0;
    }
    int deleted = 0;
    for (File copy : copies) {
      // A copy still loaded by a running FrostWire on Windows can't be deleted; skip it.
      if (copy.delete()) {
        deleted++;
      }
    }
    return deleted;
  }
}
