/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Structural regression test: the transfer detail header (progress bar, speeds)
 * renders from a fragment-level cache fed by {@code UIBittorrentDownload}'s own
 * cache. While the detail screen is foreground the transfers list is paused, so
 * nothing else refreshes the wrapper cache — the detail tick must refresh it
 * itself, on a background thread, or the header freezes while tab content
 * (which reads the live torrent handle) keeps moving.
 */
public class TransferDetailHeaderRefreshTest {

  private static final String SOURCE_FILE =
      "src/main/java/com/frostwire/android/gui/views/AbstractTransferDetailFragment.java";

  @Test
  public void updateTransferDataCache_refreshesWrapperCacheFirst() throws IOException {
    String source = readSource();
    int start = source.indexOf("private void updateTransferDataCache()");
    assertTrue("updateTransferDataCache() not found", start >= 0);
    int end = source.indexOf("private void updateCommonComponentsFromCache()", start);
    assertTrue("updateCommonComponentsFromCache() not found", end > start);
    String body = source.substring(start, end);
    assertTrue(
        "updateTransferDataCache() must call uiBittorrentDownload.updateCachedState() "
            + "before copying progress/speeds, otherwise the header freezes while the "
            + "detail screen is foreground and the transfers list is paused",
        body.contains("uiBittorrentDownload.updateCachedState()"));
  }

  @Test
  public void onResume_populatesCacheOffTheUiThread() throws IOException {
    String source = readSource();
    int start = source.indexOf("public void onResume()");
    assertTrue("onResume() not found", start >= 0);
    int end = source.indexOf("public void onDestroyView()", start);
    assertTrue("onDestroyView() not found", end > start);
    String body = source.substring(start, end);
    assertTrue(
        "onResume() must post cache fills to the DOWNLOADER handler — "
            + "updateCachedState() performs blocking JNI and must never run on the UI thread",
        body.contains("postToHandler")
            && body.contains("DOWNLOADER")
            && body.contains("updateTransferDataCache"));
  }

  private static String readSource() throws IOException {
    Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
    Path sourcePath = projectRoot.resolve(SOURCE_FILE);
    if (!Files.exists(sourcePath)) {
      String classFile =
          TransferDetailHeaderRefreshTest.class
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .getFile();
      Path buildDir = Paths.get(classFile).normalize();
      projectRoot = buildDir.getParent().getParent().getParent().getParent();
      sourcePath = projectRoot.resolve(SOURCE_FILE);
    }
    return new String(Files.readAllBytes(sourcePath));
  }
}
