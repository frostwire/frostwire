/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * frostwire/frostwire#1386: the main thread waits for TransferManager's list and map monitors in
 * remove(). Code holding them must not call into a download, which can wait on libtorrent's network
 * thread for seconds (the ANR held the list monitor across dl.getDisplayName() in a log line, and
 * every loop over the list paused, resumed or read status under it).
 *
 * <p>TransferManager is a singleton bound to the engine, so this checks the source: every
 * synchronized (downloadsListMonitor / downloadsMapMonitor) block.
 */
public class TransferManagerLockScopeStructureTest {

  private static final Pattern LOCK =
      Pattern.compile("synchronized\\s*\\(\\s*downloads(List|Map)Monitor\\s*\\)\\s*\\{");

  private static final Pattern DOWNLOAD_CALL =
      Pattern.compile(
          "\\.(getDisplayName|updateUI|pause|resume|pauseForPolicy|resumeFromPolicy|"
              + "forceReannounce|getTorrentHandle|getState|isComplete|isDownloading|isFinished|"
              + "isSeeding|isPaused|remove)\\s*\\(|new\\s+(UIBittorrentDownload|BTDownload)\\s*\\(");

  @Test
  public void noDownloadIsCalledWhileHoldingTheTransferMonitors() throws Exception {
    String source =
        new String(
            Files.readAllBytes(
                new File("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java")
                    .toPath()),
            StandardCharsets.UTF_8);
    List<String> offenders = new ArrayList<>();
    Matcher lock = LOCK.matcher(source);
    int blocks = 0;
    while (lock.find()) {
      blocks++;
      String body = block(source, lock.end());
      Matcher call = DOWNLOAD_CALL.matcher(body);
      while (call.find()) {
        String found = call.group();
        // Removing an element from the list or map itself is what the lock is for.
        boolean collectionRemove =
            found.startsWith(".remove")
                && body.substring(Math.max(0, call.start() - 30), call.start())
                    .matches("(?s).*(bittorrentDownloadsList|bittorrentDownloadsMap)$");
        if (!collectionRemove) {
          offenders.add(found + " in: " + body.trim().replaceAll("\\s+", " "));
        }
      }
    }
    assertTrue("no monitor blocks found", blocks > 5);
    assertTrue("download calls under a transfer monitor: " + offenders, offenders.isEmpty());
  }

  /** The text of the block whose opening brace ends at start. */
  private static String block(String source, int start) {
    int depth = 1;
    for (int i = start; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    throw new IllegalStateException("unbalanced braces after " + start);
  }
}
