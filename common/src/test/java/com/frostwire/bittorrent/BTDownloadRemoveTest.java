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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Removing a transfer from the transfers list: the download's listener must hear it, with the
 * partial files the transfer wrote, so the app can delete them and the folders they leave empty.
 */
@Timeout(120)
class BTDownloadRemoveTest {

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
  void removingAnUnfinishedTransferReportsThePartialFilesItWrote() throws Exception {
    Path folder = dir.resolve("content/partial");
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    content(folder.resolve("two.flac"), 128 * 1024, 2);
    TorrentInfo ti = torrent(folder.toFile(), null);
    // Half of two.flac is wrong: the transfer has the first file and half of the second.
    File two = folder.resolve("two.flac").toFile();
    byte[] bytes = Files.readAllBytes(two.toPath());
    for (int i = bytes.length / 2; i < bytes.length; i++) {
      bytes[i] = 0;
    }
    Files.write(two.toPath(), bytes);
    local.engine.download(ti, dir.resolve("content").toFile(), null, null);
    BTDownload dl = Events.next(local.events.added, "partial");
    Recorder recorder = new Recorder(dl);
    await(() -> dl.getTorrentHandle().status(true).progress() > 0, "the data was checked");
    // The transfer wrote two.flac (libtorrent writes the pieces it downloads).
    assertTrue(two.setLastModified(System.currentTimeMillis() + 60_000));

    dl.remove();

    assertSame(dl, recorder.next(), "the download's listener heard it was removed");
    assertEquals(Set.of(two), recorder.incompleteFiles);
    assertTrue(two.isFile(), "the data stays: the app's listener deletes what it reports");
  }

  @Test
  void removingATransferWithItsDataDeletesWhatItLeftBehind() throws Exception {
    Path folder = dir.resolve("content/gone");
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    TorrentInfo ti = torrent(folder.toFile(), null);
    // Already has its data: it checks complete and seeds.
    local.engine.download(ti, dir.resolve("content").toFile(), null, null, true);
    BTDownload dl = Events.next(local.events.added, "gone");
    Recorder recorder = new Recorder(dl);
    String hash = ti.infoHashV1().toHex();
    await(() -> dl.isFinished(true), "the data was checked");
    await(() -> local.engine.resumeDataFile(hash).isFile(), "the resume data was saved");
    File torrentCopy = new File(local.ctx.torrentsDir, "gone.torrent");
    assertTrue(torrentCopy.isFile());

    dl.remove(true, true);

    assertSame(dl, recorder.next());
    assertTrue(recorder.incompleteFiles.isEmpty(), "a complete transfer has no partial files");
    assertFalse(local.engine.resumeDataFile(hash).exists(), "the resume data was deleted");
    assertFalse(local.engine.resumeTorrentFile(hash).exists(), "the session copy was deleted");
    assertFalse(torrentCopy.exists(), "the saved .torrent was deleted");
    await(() -> !folder.resolve("one.mp3").toFile().exists(), "the data was deleted");
    assertFalse(dl.getTorrentHandle().isValid());
    assertEquals(TransferState.ERROR, dl.getState());
    assertEquals(0, dl.getProgress());
    assertFalse(dl.isFinished());
  }

  @Test
  void aTransferUpdatedAfterItWasAddedStillHearsItsRemoval() throws Exception {
    Path folder = dir.resolve("content/updated");
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    TorrentInfo ti = torrent(folder.toFile(), null);
    File saveDir = dir.resolve("save").toFile();
    local.engine.download(ti, saveDir, null, null);
    BTDownload dl = Events.next(local.events.added, "updated");
    Recorder recorder = new Recorder(dl);
    // Opening the torrent again makes a newer BTDownload; the transfers list keeps the first.
    local.engine.download(ti, saveDir, null, null);
    Events.next(local.events.updated, "updated");

    dl.remove();

    assertSame(dl, recorder.next());
  }

  /** Records what a download reports to its listener. */
  private static final class Recorder implements BTDownloadListener {
    private final BlockingQueue<BTDownload> removed = new LinkedBlockingQueue<>();
    private volatile Set<File> incompleteFiles;

    Recorder(BTDownload dl) {
      dl.setListener(this);
      assertSame(this, dl.getListener());
    }

    @Override
    public void finished(BTDownload dl) {}

    @Override
    public void removed(BTDownload dl, Set<File> incompleteFiles) {
      this.incompleteFiles = incompleteFiles;
      removed.add(dl);
    }

    BTDownload next() throws InterruptedException {
      BTDownload dl = removed.poll(LocalEngine.TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(dl, "timed out waiting for the removal");
      return dl;
    }
  }
}
