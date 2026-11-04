/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static com.frostwire.bittorrent.LocalEngine.content;
import static com.frostwire.bittorrent.LocalEngine.torrent;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * What FrostWire keeps in a transfer's resume file besides libtorrent's data: whether the user
 * paused it and the distributed-search peer it came from. Both must survive a restart, whichever of
 * the torrent's BTDownloads saved the file last: the engine makes a new one on each update.
 */
@Timeout(120)
class BTDownloadResumeDataTest {

  @TempDir Path dir;
  private LocalEngine local;

  @BeforeEach
  void startEngine() throws Exception {
    local = LocalEngine.start(dir);
  }

  @AfterEach
  void stopEngine() {
    local.close();
  }

  @Test
  void aPausedTransferIsStillPausedAfterARestart() throws Exception {
    TorrentInfo ti = album("paused");
    File saveDir = dir.resolve("save").toFile();
    local.engine.download(ti, saveDir, null, null);
    BTDownload dl = Events.next(local.events.added, "paused");
    BTDownload update = Events.next(local.events.updated, "paused");

    dl.pause();

    assertTrue(dl.wasPaused());
    assertTrue(update.wasPaused(), "every download of the torrent knows it was paused");
    String hash = ti.infoHashV1().toHex();
    await(() -> "true".equals(extraOf(hash).get("was_paused")), "the pause was saved");

    local.restart();
    local.engine.restoreDownloads();

    BTDownload restored = Events.next(local.events.added, "paused");
    assertTrue(restored.wasPaused());
    assertEquals(saveDir, restored.getSavePath(), "restored to its own folder");

    restored.resume();

    assertFalse(restored.wasPaused());
    await(
        () ->
            "false"
                .equals(
                    LocalEngine.resumeExtra(local.engine.resumeDataFile(hash)).get("was_paused")),
        "the resume was saved");
  }

  @Test
  void aDistributedSearchResultKeepsItsSourceAcrossARestart() throws Exception {
    TorrentInfo ti = album("sourced");
    byte[] source = new byte[32];
    Arrays.fill(source, (byte) 7);

    local.engine.download(ti, dir.resolve("save").toFile(), null, null, false, source);

    BTDownload dl = Events.next(local.events.added, "sourced");
    assertArrayEquals(source, dl.getDistributedSourcePeerPub());
    String hash = ti.infoHashV1().toHex();
    await(
        () -> Arrays.equals(source, DistributedSourceAttribution.get(extraOf(hash))),
        "the source was saved with the resume data");

    local.restart();
    local.engine.restoreDownloads();

    assertArrayEquals(
        source, Events.next(local.events.added, "sourced").getDistributedSourcePeerPub());
  }

  @Test
  void aSecondSourceDoesNotReattributeAnExistingTransfer() throws Exception {
    TorrentInfo ti = album("first-source");
    byte[] first = new byte[32];
    byte[] second = new byte[32];
    Arrays.fill(first, (byte) 1);
    Arrays.fill(second, (byte) 2);
    File saveDir = dir.resolve("save").toFile();
    local.engine.download(ti, saveDir, null, null, false, first);
    BTDownload dl = Events.next(local.events.added, "first-source");

    local.engine.download(ti, saveDir, null, null, false, second);

    Events.next(local.events.updated, "first-source");
    BTDownload update = Events.next(local.events.updated, "first-source");
    assertArrayEquals(first, dl.getDistributedSourcePeerPub());
    assertArrayEquals(first, update.getDistributedSourcePeerPub());
    assertFalse(update.setDistributedSourcePeerPub(second), "the first source wins");
    String hash = ti.infoHashV1().toHex();
    await(
        () -> Arrays.equals(first, DistributedSourceAttribution.get(extraOf(hash))),
        "the resume file keeps the first source");
  }

  private Map<String, String> extraOf(String hash) {
    return LocalEngine.resumeExtra(local.engine.resumeDataFile(hash));
  }

  private TorrentInfo album(String name) throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    content(folder.resolve("two.flac"), 128 * 1024, 2);
    return torrent(folder.toFile(), null);
  }
}
