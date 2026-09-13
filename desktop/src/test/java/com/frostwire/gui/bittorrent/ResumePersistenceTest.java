/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structural regression test: torrents that are born complete (per-file Share, auto-seed) must
 * still persist resume data, otherwise a restart restores them under the default data dir and they
 * sit at 0% forever. The forced save path must not hide behind libtorrent's dirty gate, and check
 * completion must trigger a forced save.
 */
class ResumePersistenceTest {

  @Test
  void forcedResumeSaveBypassesDirtyGate() throws Exception {
    String download = readSource("common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    int start = download.indexOf("private void doResumeData(boolean force)");
    assertTrue(start >= 0, "doResumeData(force) not found");
    int end = download.indexOf("private File createPartsFile", start);
    String body = download.substring(start, end);
    assertTrue(
        body.contains("th.saveResumeData()"),
        "forced save must call unconditional saveResumeData(), not only ONLY_IF_MODIFIED");
  }

  @Test
  void constructorBackfillsMissingResume() throws Exception {
    String download = readSource("common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    int start = download.indexOf("public BTDownload(BTEngine engine, TorrentHandle th)");
    assertTrue(start >= 0, "BTDownload constructor not found");
    int end = download.indexOf("private static boolean isPaused", start);
    String body = download.substring(start, end);
    assertTrue(
        body.contains("resumeDataFile") && body.contains("doResumeData(true)"),
        "constructor must force a resume save when no resume file exists yet,"
            + " so zero-piece torrents that never check still persist");
  }

  @Test
  void sessionTorrentFileEnsuredAlongsideResume() throws Exception {
    String engine = readSource("common/src/main/java/com/frostwire/bittorrent/BTEngine.java");
    assertTrue(
        engine.contains("ensureResumeTorrentFile"),
        "BTEngine must ensure a session .torrent exists so magnet-origin torrents survive restarts");
    String download = readSource("common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    assertTrue(
        download.contains("ensureResumeTorrentFile"),
        "BTDownload must ensure the session .torrent alongside the resume backfill");
  }

  @Test
  void checkCompletionTriggersForcedResumeSave() throws Exception {
    String download = readSource("common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    int start = download.indexOf("case TORRENT_CHECKED:");
    assertTrue(start >= 0, "TORRENT_CHECKED case not found");
    int end = download.indexOf("case SAVE_RESUME_DATA:", start);
    String body = download.substring(start, end);
    assertTrue(
        body.contains("doResumeData(true)"),
        "TORRENT_CHECKED must force a resume save so born-complete torrents persist");
  }

  private static String readSource(String moduleRelative) throws Exception {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      Path file = dir.resolve(moduleRelative);
      if (Files.exists(file)) {
        return Files.readString(file);
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
