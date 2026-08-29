/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class TransferDetailsMetadataStructureTest {

  @Test
  public void detailsHiddenWhileDownloadingMetadata() throws Exception {
    String adapter =
        readProjectFile("src/main/java/com/frostwire/android/gui/adapters/TransferListAdapter.java")
            .replaceAll("\\s+", "");
    String manager =
        readProjectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java")
            .replaceAll("\\s+", "");
    String activity =
        readProjectFile("src/main/java/com/frostwire/android/gui/activities/TransferDetailActivity.java")
            .replaceAll("\\s+", "");

    assertTrue(adapter.contains("state==TransferState.DOWNLOADING_METADATA"));
    assertTrue(adapter.contains("canOpenTorrentDetails(download)"));
    assertTrue(activity.contains("TransferListAdapter.canOpenTorrentDetails(uiBittorrentDownload)"));
    assertTrue(manager.contains("for(BittorrentDownloadd:bittorrentDownloadsList)"));
    assertTrue(manager.contains("uriLower.contains(hash.toLowerCase())"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
