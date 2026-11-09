/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostwire.search.soundcloud.SoundcloudItem;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A SoundCloud download that fails shows an error. Its error handler called a listener field nobody
 * assigned, so the failure threw a NullPointerException on the download thread and the transfer
 * stayed WAITING forever (Icebase #55228c116f04, frostwire/frostwire#1510).
 */
class SoundcloudDownloadErrorTest {
  @TempDir Path directory;
  private File previousDirectory;

  @BeforeEach
  void setUp() throws Exception {
    previousDirectory = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(
        Files.createDirectory(directory.resolve("complete")).toFile());
  }

  @AfterEach
  void tearDown() {
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(previousDirectory);
  }

  @Test
  void aTrackWithNoStreamUrlEndsInError() throws Exception {
    SoundcloudItem item = new SoundcloudItem();
    item.title = "No stream";
    item.permalink = "no-stream";
    item.permalink_url = "https://soundcloud.com/someone/no-stream";
    item.created_at = "2026/01/01 00:00:00 +0000";
    item.downloadable = false; // no transcoding: getDownloadUrl() returns null, no network
    SoundcloudSearchResult sr = new SoundcloudSearchResult(item, "client", "1");
    assertNull(sr.getDownloadUrl());

    SoundcloudDownload download = new SoundcloudDownload(sr);
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (download.getState() != TransferState.ERROR && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertEquals(TransferState.ERROR, download.getState());
  }
}
