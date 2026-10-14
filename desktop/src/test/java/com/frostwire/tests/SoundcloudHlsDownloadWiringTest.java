/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SoundcloudHlsDownloadWiringTest {
  @Test
  void bothTransferPathsAssembleHlsAudioInsteadOfSavingAPlaylist() {
    assertDoesNotThrow(
        () -> {
          String shared = source("../common/src/main/java/com/frostwire/transfers/SoundcloudDownload.java");
          String desktop = source("src/main/java/com/frostwire/gui/bittorrent/SoundcloudDownload.java");
          String base = source("../common/src/main/java/com/frostwire/transfers/BaseHttpDownload.java");

          assertTrue(base.contains("save(client,url,temp,resumeAttempt,httpHeaders)"));
          assertTrue(shared.contains("if(sr.isHls())"));
          assertTrue(shared.contains("HlsAudioDownloader.save(client,url,temp)"));
          assertTrue(shared.contains("super.save(client,url,temp,resume,httpHeaders)"));
          assertTrue(desktop.contains("if(sr.isHls())"));
          assertTrue(desktop.contains("HlsAudioDownloader.save(httpClient,downloadUrl,temp)"));
          assertTrue(desktop.contains("httpClient.save(downloadUrl,temp,false)"));
        });
  }

  @Test
  void aacOutputIsNotMislabelledOrPassedToTheMp3TagWriter() {
    assertDoesNotThrow(
        () -> {
          String shared = source("../common/src/main/java/com/frostwire/transfers/SoundcloudDownload.java");
          String desktop = source("src/main/java/com/frostwire/gui/bittorrent/SoundcloudDownload.java");
          assertTrue(shared.contains("if(!sr.isHls()){downloadAndUpdateCoverArt(sr,tempPath);}"));
          assertTrue(desktop.contains("FilenameUtils.getExtension(filename)"));
          assertTrue(desktop.contains("if(sr.isHls()){returnfalse;"));
        });
  }

  private static String source(String path) throws Exception {
    return Files.readString(Path.of(path)).replaceAll("\\s+", "");
  }
}
