/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static com.frostwire.bittorrent.LocalEngine.content;
import static com.frostwire.bittorrent.LocalEngine.peers;
import static com.frostwire.bittorrent.LocalEngine.torrent;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A transfer's life on a real local session: downloading from a peer on 127.0.0.1, finishing and
 * seeding, coming back to its own folder after a restart, moving to another folder, and the
 * per-torrent settings the transfers list changes.
 */
@Timeout(120)
class BTDownloadTransferTest {

  @TempDir Path dir;
  private LocalEngine local;
  private SessionManager seeder;

  @BeforeEach
  void startEngine() throws Exception {
    local = LocalEngine.start(dir);
  }

  @AfterEach
  void stopSessions() {
    if (seeder != null) {
      seeder.stop();
    }
    local.close();
  }

  @Test
  void aTransferFromAPeerFinishesAndSeeds() throws Exception {
    // The seeder's copy lives elsewhere, so the download starts from nothing.
    TorrentInfo ti = album(dir.resolve("seed"), "song", null);
    seeder = LocalEngine.seed(ti, dir.resolve("seed").toFile());
    File saveDir = dir.resolve("save").toFile();
    Recorder recorder = new Recorder();
    // Paused, so the recorder is in place before the transfer can finish.
    local.engine.pause();
    local.engine.download(ti, saveDir, null, peers(seeder), true);
    BTDownload dl = Events.next(local.events.added, "song");
    dl.setListener(recorder);
    assertSame(recorder, dl.getListener());
    assertEquals(TransferState.PAUSED, dl.getState());

    local.engine.resume();

    assertSame(dl, poll(recorder.finished), "the download's listener heard it finish");
    Events.next(local.events.finished, "song");
    assertTrue(dl.isFinished(true));
    dl.refreshStatusCache();
    assertEquals(100, dl.getProgress());
    assertTrue(dl.isComplete());
    assertTrue(dl.isFinished());
    assertTrue(dl.isSeeding());
    assertFalse(dl.isPaused());
    assertEquals(TransferState.SEEDING, dl.getState());
    assertEquals(0, dl.getETA());
    assertEquals(0, dl.getDownloadSpeed(), "a seeding transfer downloads nothing");
    assertFalse(dl.isDownloading());
    assertEquals(ti.totalSize(), dl.getBytesReceived());
    assertTrue(dl.getIncompleteFiles().isEmpty());
    assertTrue(recorder.stateChanges.get() > 0, "state changes were reported");
    List<TransferItem> items = dl.getItems();
    assertEquals(2, items.size());
    for (int i = 0; i < items.size(); i++) {
      BTDownloadItem item = (BTDownloadItem) items.get(i);
      assertTrue(item.isComplete());
      assertEquals(100, item.getProgress());
      assertEquals(item.getSize(), item.getDownloaded());
      assertEquals(item.getSize(), item.getSequentialDownloaded());
      assertArrayEquals(
          Files.readAllBytes(dir.resolve("seed/song").resolve(item.getName())),
          Files.readAllBytes(item.getFile().toPath()));
    }
    String hash = ti.infoHashV1().toHex();
    await(() -> saveDir.equals(local.engine.readSavePath(hash)), "the resume data has the folder");
    assertEquals(new File(local.ctx.torrentsDir, "song.torrent"), dl.getTorrentFile());
  }

  @Test
  void aPolicyPauseIsNotRememberedAsTheUsersChoice() throws Exception {
    TorrentInfo ti = album("policy");
    local.engine.download(ti, dir.resolve("save").toFile(), null, null);
    BTDownload dl = Events.next(local.events.added, "policy");

    dl.pauseForPolicy();

    assertFalse(dl.wasPaused());
    awaitStatus(dl, () -> dl.isPaused(), "the torrent paused");
  }

  @Test
  void restoringBringsEveryTransferBackToItsOwnFolder() throws Exception {
    TorrentInfo first = album("first");
    TorrentInfo second = album("second");
    File firstDir = dir.resolve("save-1").toFile();
    File secondDir = dir.resolve("save-2").toFile();
    local.engine.download(first, firstDir, null, null);
    local.engine.download(second, secondDir, null, null);
    Events.next(local.events.added, "first");
    Events.next(local.events.added, "second");
    awaitResume(first);
    awaitResume(second);
    Files.write(new File(local.ctx.homeDir, "corrupt.torrent").toPath(), new byte[] {'x'});
    Files.write(new File(local.ctx.homeDir, "notes.txt").toPath(), new byte[] {'x'});

    local.restart();
    local.engine.restoreDownloads();

    assertEquals(firstDir, Events.next(local.events.added, "first").getSavePath());
    assertEquals(secondDir, Events.next(local.events.added, "second").getSavePath());
  }

  @Test
  void aTransferWhoseFolderIsGoneIsRestoredToTheDataFolder() throws Exception {
    TorrentInfo ti = album("moved");
    Path saveDir = dir.resolve("removable");
    local.engine.download(ti, saveDir.toFile(), null, null);
    Events.next(local.events.added, "moved");
    awaitResume(ti);
    local.restart(
        () -> {
          // The folder is now a file, as when a drive is unplugged and something else is there.
          deleteTree(saveDir);
          Files.write(saveDir, new byte[] {1});
        });
    local.engine.restoreDownloads();

    assertEquals(local.ctx.dataDir, Events.next(local.events.added, "moved").getSavePath());
  }

  @Test
  void aV2TransferSavedUnderItsOldResumeNameIsRestoredToItsFolder() throws Exception {
    TorrentInfo ti = album(dir.resolve("content"), "legacy", TorrentBuilder.V2_ONLY);
    File saveDir = dir.resolve("legacy-save").toFile();
    local.engine.download(ti, saveDir, null, null);
    Events.next(local.events.added, "legacy");
    String v2 = ti.infoHashV2().toHex();
    await(() -> local.engine.resumeDataFile(v2).isFile(), "the resume data was saved");
    // Before the naming was unified, the resume file of a v2 torrent had the truncated hash.
    local.restart(
        () ->
            Files.move(
                local.engine.resumeDataFile(v2).toPath(),
                local.engine.resumeDataFile(v2.substring(0, 40)).toPath()));
    local.engine.restoreDownloads();

    assertEquals(saveDir, Events.next(local.events.added, "legacy").getSavePath());
  }

  @Test
  void movingTheStorageMovesUnfinishedTransfersAndRemembersTheNewFolder() throws Exception {
    TorrentInfo ti = album("moving");
    File saveDir = dir.resolve("save").toFile();
    local.engine.download(ti, saveDir, null, null);
    BTDownload dl = Events.next(local.events.added, "moving");
    String hash = ti.infoHashV1().toHex();
    awaitResume(ti);
    File elsewhere = Files.createDirectories(dir.resolve("elsewhere")).toFile();

    local.engine.moveStorage(elsewhere);

    await(() -> elsewhere.equals(local.engine.readSavePath(hash)), "the new folder was saved");
    assertEquals(elsewhere.getAbsolutePath(), dl.getTorrentHandle().savePath());
  }

  @Test
  void replacedTrackersAreKeptAcrossARestart() throws Exception {
    TorrentInfo ti = album("tracked");
    local.engine.download(ti, dir.resolve("save").toFile(), null, null);
    BTDownload dl = Events.next(local.events.added, "tracked");
    Set<String> trackers = Set.of("udp://127.0.0.1:65530/announce");

    dl.trackers(trackers);
    // Asking the tracker for peers or stats doesn't change the list.
    dl.requestTrackerAnnounce();
    dl.requestTrackerScrape();

    assertEquals(trackers, dl.trackers());
    File resume = local.engine.resumeDataFile(ti.infoHashV1().toHex());
    await(
        () -> {
          try {
            return new String(Files.readAllBytes(resume.toPath()), StandardCharsets.ISO_8859_1)
                .contains("udp://127.0.0.1:65530/announce");
          } catch (Exception notSavedYet) {
            return false;
          }
        },
        "the trackers were saved");
    local.restart();
    local.engine.restoreDownloads();
    assertEquals(trackers, Events.next(local.events.added, "tracked").trackers());
  }

  @Test
  void rateLimitsAndSequentialDownloadApplyToTheTorrent() throws Exception {
    TorrentInfo ti = album("limits");
    local.engine.download(ti, dir.resolve("save").toFile(), null, null);
    BTDownload dl = Events.next(local.events.added, "limits");

    dl.setDownloadRateLimit(50_000);
    dl.setUploadRateLimit(20_000);
    dl.setSequentialDownload(true);

    assertEquals(50_000, dl.getDownloadRateLimit());
    assertEquals(20_000, dl.getUploadRateLimit());
    awaitStatus(dl, () -> dl.isSequentialDownload(), "sequential download is on");

    dl.setSequentialDownload(false);

    awaitStatus(dl, () -> !dl.isSequentialDownload(), "sequential download is off");
    assertTrue(dl.magnetUri().contains(ti.infoHashV1().toHex()));
  }

  @Test
  void aRecheckIsReportedAsAStateChange() throws Exception {
    TorrentInfo ti = album("rechecked");
    local.engine.download(ti, dir.resolve("content").toFile(), null, null);
    BTDownload dl = Events.next(local.events.added, "rechecked");
    await(() -> dl.isFinished(true), "the data was checked");
    Recorder recorder = new Recorder();
    dl.setListener(recorder);

    dl.forceRecheck();

    await(() -> recorder.stateChanges.get() > 0, "the recheck changed the state");
    await(() -> dl.isFinished(true), "the data checked complete again");
  }

  /** Waits for {@code condition} on fresh status; the handle caches status for half a second. */
  private static void awaitStatus(BTDownload dl, BooleanSupplier condition, String what) {
    await(
        () -> {
          dl.refreshStatusCache();
          return condition.getAsBoolean();
        },
        what);
  }

  private void awaitResume(TorrentInfo ti) {
    String hash = ti.infoHashV1().toHex();
    await(() -> local.engine.resumeDataFile(hash).isFile(), "the resume data of " + ti.name());
  }

  private TorrentInfo album(String name) throws Exception {
    return album(dir.resolve("content"), name, null);
  }

  private TorrentInfo album(Path parent, String name, create_flags_t flags) throws Exception {
    Path folder = parent.resolve(name);
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    content(folder.resolve("two.flac"), 128 * 1024, 2);
    return torrent(folder.toFile(), flags);
  }

  private static void deleteTree(Path path) throws Exception {
    try (var walk = Files.walk(path)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  private static <T> T poll(BlockingQueue<T> queue) throws InterruptedException {
    T value = queue.poll(LocalEngine.TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    assertNotNull(value, "timed out waiting for the listener");
    return value;
  }

  /** Records what a download reports to its listener. */
  private static final class Recorder implements BTDownloadListener {
    final BlockingQueue<BTDownload> finished = new LinkedBlockingQueue<>();
    final BlockingQueue<Set<File>> removed = new LinkedBlockingQueue<>();
    final AtomicInteger stateChanges = new AtomicInteger();

    @Override
    public void finished(BTDownload dl) {
      finished.add(dl);
    }

    @Override
    public void removed(BTDownload dl, Set<File> incompleteFiles) {
      removed.add(incompleteFiles);
    }

    @Override
    public void stateChanged(BTDownload dl) {
      stateChanges.incrementAndGet();
    }
  }
}
