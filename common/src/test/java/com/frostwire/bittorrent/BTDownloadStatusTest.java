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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.FakeTorrentHandle.FakeStatus;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_status.state_t;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the transfers list shows for a download (state, progress, speeds, ETA, peers, files) comes
 * from libtorrent's torrent status. A fake handle sets that status exactly, so each case is checked
 * without waiting for a real torrent to get there.
 */
@Timeout(60)
class BTDownloadStatusTest {

  @TempDir static Path engineDir;
  private static LocalEngine local;

  @TempDir Path dir;

  @BeforeAll
  static void startEngine() throws Exception {
    local = LocalEngine.start(engineDir);
  }

  @AfterAll
  static void stopEngine() {
    local.close();
  }

  @Test
  void eachLibtorrentStateShowsAsATransferState() throws Exception {
    assertEquals(TransferState.CHECKING, stateOf(s -> s.setState(state_t.checking_files)));
    assertEquals(
        TransferState.DOWNLOADING_METADATA, stateOf(s -> s.setState(state_t.downloading_metadata)));
    assertEquals(TransferState.DOWNLOADING, stateOf(s -> s.setState(state_t.downloading)));
    assertEquals(TransferState.FINISHED, stateOf(s -> s.setState(state_t.finished)));
    assertEquals(TransferState.SEEDING, stateOf(s -> s.setState(state_t.seeding)));
    assertEquals(TransferState.CHECKING, stateOf(s -> s.setState(state_t.checking_resume_data)));
  }

  @Test
  void aTransferWithEverythingItWantsShowsAsSeeding() throws Exception {
    assertEquals(TransferState.SEEDING, stateOf(s -> s.setIs_finished(true)));
    assertEquals(TransferState.SEEDING, stateOf(s -> s.setProgress(1f)));
    assertEquals(
        TransferState.SEEDING,
        stateOf(
            s -> {
              s.setTotal_wanted(100);
              s.setTotal_wanted_done(100);
            }));
    assertEquals(TransferState.DOWNLOADING, stateOf(s -> s.setTotal_wanted_done(100)));
  }

  @Test
  void aPausedTorrentShowsAsPausedAndMovesNothing() throws Exception {
    BTDownload dl =
        download(
            s -> {
              s.setFlags(TorrentFlags.PAUSED);
              s.setDownload_payload_rate(1000);
              s.setUpload_payload_rate(1000);
            });

    assertEquals(TransferState.PAUSED, dl.getState());
    assertTrue(dl.isPaused());
    assertEquals(0, dl.getDownloadSpeed());
    assertEquals(0, dl.getUploadSpeed());
  }

  @Test
  void whileTheEngineIsPausedEveryTransferShowsAsPaused() throws Exception {
    BTDownload dl = download(s -> s.setState(state_t.downloading));
    local.engine.pause();
    try {
      assertEquals(TransferState.PAUSED, dl.getState());
      assertTrue(dl.isPaused());
    } finally {
      local.engine.resume();
    }
    assertEquals(TransferState.DOWNLOADING, dl.getState());
    assertFalse(dl.isPaused());
  }

  @Test
  void withoutARunningEngineEveryTransferShowsAsStopped() throws Exception {
    BTEngine stopped = LocalEngine.newEngine();
    FakeTorrentHandle handle = new FakeTorrentHandle(metadata("stopped"));
    handle.status.setSave_path(dir.toString());

    BTDownload dl = new BTDownload(stopped, new TorrentHandle(handle));

    assertEquals(TransferState.STOPPED, dl.getState());
    assertTrue(dl.isPaused());
  }

  @Test
  void aTransferWhoseTorrentIsGoneShowsAsAnErrorAndDoesNothing() throws Exception {
    FakeTorrentHandle handle = handle("gone", s -> s.setProgress(0.5f));
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    handle.valid = false;
    dl.refreshStatusCache();
    dl.pause();
    dl.resume();
    dl.forceRecheck();
    dl.setSequentialDownload(true);

    assertEquals(TransferState.ERROR, dl.getState());
    assertEquals(List.of(), handle.calls(), "nothing was asked of the gone torrent");
    assertEquals(0, dl.getProgress());
    assertFalse(dl.isComplete());
    assertFalse(dl.isFinished());
    assertFalse(dl.isFinished(true));
    assertFalse(dl.isPaused());
    assertFalse(dl.isSeeding());
    assertFalse(dl.isPartial());
    assertFalse(dl.isSequentialDownload());
    assertEquals(0, dl.getDownloadSpeed());
    assertEquals(0, dl.getUploadSpeed());
    assertEquals(0, dl.getETA());
    assertEquals(0, dl.getBytesReceived());
    assertEquals(0, dl.getConnectedPeers());
    assertNull(dl.getContentSavePath());
    assertNull(dl.getV1InfoHash());
    assertTrue(dl.trackers().isEmpty());
    assertTrue(dl.getItems().isEmpty());
    assertTrue(dl.getIncompleteFiles().isEmpty());
  }

  @Test
  void progressIsZeroWhileCheckingAndNeverAbove100() throws Exception {
    assertEquals(50, progressOf(state_t.downloading, 0.5f));
    assertEquals(100, progressOf(state_t.downloading, 1f));
    assertEquals(0, progressOf(state_t.downloading, 0.004f));
    assertEquals(0, progressOf(state_t.checking_files, 0.5f));
    assertEquals(0, progressOf(state_t.checking_files, 1f));
    assertEquals(100, progressOf(state_t.seeding, 1.5f));
  }

  @Test
  void aDownloadingTransferShowsItsSpeeds() throws Exception {
    BTDownload dl =
        download(
            s -> {
              s.setDownload_payload_rate(1000);
              s.setUpload_payload_rate(200);
            });

    assertEquals(1000, dl.getDownloadSpeed());
    assertEquals(200, dl.getUploadSpeed());
    assertTrue(dl.isDownloading());
  }

  @Test
  void aSeedingTransferOnlyUploads() throws Exception {
    BTDownload dl =
        download(
            s -> {
              s.setIs_finished(true);
              s.setIs_seeding(true);
              s.setDownload_payload_rate(1000);
              s.setUpload_payload_rate(200);
            });

    assertEquals(0, dl.getDownloadSpeed());
    assertEquals(200, dl.getUploadSpeed());
    assertFalse(dl.isDownloading());
    assertTrue(dl.isSeeding());
  }

  @Test
  void aFinishedTransferThatIsNotSeedingMovesNothing() throws Exception {
    BTDownload dl =
        download(
            s -> {
              s.setIs_finished(true);
              s.setUpload_payload_rate(200);
            });

    assertEquals(0, dl.getUploadSpeed());
    assertTrue(dl.isFinished());
  }

  @Test
  void theEtaIsTheBytesLeftOverTheDownloadRate() throws Exception {
    TorrentInfo ti = metadata("eta");
    long total = ti.totalSize();

    assertEquals(10, etaOf(ti, total - 1000, 100));
    assertEquals(-1, etaOf(ti, total - 1000, 0), "unknown without a rate");
    assertEquals(0, etaOf(ti, total, 100), "nothing left");
  }

  @Test
  void aMagnetWithoutMetadataHasNoSizeEtaOrFiles() throws Exception {
    FakeTorrentHandle handle = new FakeTorrentHandle(null);
    handle.status.setSave_path(dir.toString());
    handle.status.setDownload_payload_rate(100);

    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertEquals(0, dl.getSize());
    assertEquals(0, dl.getETA());
    assertNull(dl.partsFile());
    assertNull(dl.getContentSavePath());
    assertNull(dl.getV1InfoHash());
    assertNull(dl.getPredominantFileExtension());
    assertTrue(dl.getItems().isEmpty());
    assertTrue(dl.getIncompleteFiles().isEmpty());
  }

  @Test
  void peersSeedsTotalsAndDurationsComeFromTheStatus() throws Exception {
    BTDownload dl =
        download(
            s -> {
              s.setNum_peers(3);
              s.setList_peers(10);
              s.setNum_seeds(1);
              s.setList_seeds(4);
              s.activeSeconds = 120;
              s.seedingSeconds = 60;
              s.setAnnouncing_to_dht(true);
              s.setAnnouncing_to_lsd(false);
              s.setTotal_done(5000);
              s.setAll_time_download(7000);
              s.setTotal_upload(300);
              s.setAll_time_upload(900);
            });

    assertEquals(3, dl.getConnectedPeers());
    assertEquals(10, dl.getTotalPeers());
    assertEquals(1, dl.getConnectedSeeds());
    assertEquals(4, dl.getTotalSeeds());
    assertEquals(120, dl.getActiveDuration());
    assertEquals(60, dl.getSeedingDuration());
    assertTrue(dl.isAnnouncingToDht());
    assertFalse(dl.isAnnouncingToLsd());
    assertEquals(5000, dl.getBytesReceived());
    assertEquals(7000, dl.getTotalBytesReceived());
    assertEquals(300, dl.getBytesSent());
    assertEquals(900, dl.getTotalBytesSent());
  }

  @Test
  void theContentIsTheFileOfASingleFileTorrentOrTheFolderOfAMultiFileOne() throws Exception {
    File single = content(dir.resolve("single/movie.mkv"), 16 * 1024, 3);
    FakeTorrentHandle singleHandle = new FakeTorrentHandle(torrent(single, null));
    singleHandle.status.setSave_path(dir.toString());
    FakeTorrentHandle multiHandle = handle("multi", s -> s.setName("multi"));

    BTDownload singleFile = new BTDownload(local.engine, new TorrentHandle(singleHandle));
    BTDownload multiFile = new BTDownload(local.engine, new TorrentHandle(multiHandle));

    assertEquals(dir.resolve("movie.mkv").toFile(), singleFile.getContentSavePath());
    assertEquals(dir.resolve("multi").toFile(), multiFile.getContentSavePath());
    assertEquals(dir.toFile(), multiFile.getSavePath());
    assertNull(multiFile.previewFile());
  }

  @Test
  void theNameShownIsTheOnlyPickedFileOrTheTorrentsName() throws Exception {
    FakeTorrentHandle handle = handle("picked", s -> s.setName("picked"));
    handle.priorities = new int[] {Priority.IGNORE.swig(), Priority.NORMAL.swig()};
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertEquals("two.flac", dl.getDisplayName());
    assertTrue(dl.isPartial());
    assertEquals("two.flac", dl.getPaymentOptions().getItemName());

    handle.priorities = new int[] {Priority.NORMAL.swig(), Priority.NORMAL.swig()};

    assertEquals("picked", dl.getDisplayName());
    assertEquals("picked", dl.getName());
    assertFalse(dl.isPartial());
  }

  @Test
  void thePredominantExtensionIsTheOneWithTheMostBytes() throws Exception {
    Path folder = dir.resolve("mixed");
    content(folder.resolve("a.mp3"), 64 * 1024, 1);
    content(folder.resolve("b.mp3"), 64 * 1024, 2);
    content(folder.resolve("c.flac"), 96 * 1024, 3);
    content(folder.resolve("README"), 160 * 1024, 4);
    FakeTorrentHandle handle = new FakeTorrentHandle(torrent(folder.toFile(), null));
    handle.status.setSave_path(dir.toString());

    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertEquals("mp3", dl.getPredominantFileExtension());
  }

  @Test
  void sequentialDownloadIsReadFromTheTorrentsFlags() throws Exception {
    assertTrue(download(s -> s.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)).isSequentialDownload());
    assertFalse(download(s -> s.setFlags(new torrent_flags_t())).isSequentialDownload());
  }

  @Test
  void sequentialDownloadSetsAndClearsTheTorrentsFlag() throws Exception {
    FakeTorrentHandle handle = handle("sequential", s -> {});
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));
    int flag = TorrentFlags.SEQUENTIAL_DOWNLOAD.to_int();

    dl.setSequentialDownload(true);
    dl.setSequentialDownload(false);

    assertEquals(List.of("set " + flag, "unset " + flag), handle.calls());
  }

  @Test
  void pausingAndResumingChangeTheTorrentAndSaveItsResumeData() throws Exception {
    FakeTorrentHandle handle = handle("pausing", s -> {});
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));
    int requests = handle.resumeDataRequests.get();
    int autoManaged = TorrentFlags.AUTO_MANAGED.to_int();

    dl.pause();

    assertTrue(dl.wasPaused());
    assertEquals(List.of("unset " + autoManaged, "pause"), handle.calls());
    await(() -> handle.resumeDataRequests.get() > requests, "the pause asked for resume data");

    dl.resume();

    assertFalse(dl.wasPaused());
    assertEquals(
        List.of("unset " + autoManaged, "pause", "set " + autoManaged, "resume"), handle.calls());
    await(() -> handle.resumeDataRequests.get() > requests + 1, "the resume asked for it too");
  }

  @Test
  void aRecheckIsAskedOfTheTorrent() throws Exception {
    FakeTorrentHandle handle = handle("recheck", s -> {});
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    dl.forceRecheck();

    assertEquals(List.of("recheck"), handle.calls());
  }

  @Test
  void aStaleStatusIsRefreshedInTheBackgroundAndAFailedRefreshIsNotShown() throws Exception {
    FakeTorrentHandle handle = handle("stale", s -> s.setNum_peers(1));
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));
    FakeStatus fresh = new FakeStatus();
    fresh.setNum_peers(7);
    handle.status = fresh;

    // Past the download's one-second status cache (and the handle's half-second one).
    Thread.sleep(1100);

    assertEquals(1, dl.getConnectedPeers(), "the stale status shows while it refreshes");
    await(() -> dl.getConnectedPeers() == 7, "the refreshed status shows");

    handle.statusFailure = new IllegalStateException("torrent gone");
    Thread.sleep(600);
    dl.refreshStatusCache();

    assertEquals(0, dl.getConnectedPeers(), "no status rather than a stale one");
  }

  @Test
  void theFilesLeftIncompleteAreTheOnesThisTransferWrote() throws Exception {
    TorrentInfo ti = metadata("incomplete");
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());
    long now = System.currentTimeMillis();
    handle.status.setAdded_time(now / 1000 - 10);
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));
    File one = content(dir.resolve("incomplete/one.mp3"), 64 * 1024, 1);
    File two = content(dir.resolve("incomplete/two.flac"), 128 * 1024, 2);

    handle.progress = new long[] {32 * 1024, 64 * 1024};
    assertTrue(one.setLastModified(now - 3_600_000), "written before this transfer started");

    assertEquals(java.util.Set.of(two), dl.getIncompleteFiles());

    handle.progress = new long[] {64 * 1024, 128 * 1024};
    assertTrue(dl.getIncompleteFiles().isEmpty(), "complete files aren't incomplete");

    assertTrue(two.delete());
    handle.progress = new long[] {64 * 1024, 0};
    assertTrue(dl.getIncompleteFiles().isEmpty(), "a file that isn't there needs no deleting");

    handle.progressFailure = new IllegalStateException("std::bad_alloc");
    assertTrue(dl.getIncompleteFiles().isEmpty());
  }

  @Test
  void theFilesCompletePiecesAreReadOnceForSequentialPlayback() throws Exception {
    TorrentInfo ti = metadata("pieces");
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());
    handle.status.pieces = ti.numPieces();
    handle.status.havePieces = 64 * 1024 / ti.pieceLength(); // the first 64 KiB: one.mp3
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    List<BTDownloadItem> items = dl.getItems().stream().map(BTDownloadItem.class::cast).toList();

    assertEquals(64 * 1024, items.get(0).getSequentialDownloaded());
    assertEquals(0, items.get(1).getSequentialDownloaded());
    assertEquals(dir.resolve("pieces/two.flac").toFile(), items.get(1).getFile());
  }

  @Test
  void aNewDownloadAsksForResumeDataAndKeepsASessionCopyOfItsTorrent() throws Exception {
    TorrentInfo ti = metadata("backfill");
    String hash = ti.infoHashV1().toHex();
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());

    new BTDownload(local.engine, new TorrentHandle(handle));

    assertEquals(1, handle.resumeDataRequests.get(), "resume data for the restore");
    File copy = local.engine.resumeTorrentFile(hash);
    assertTrue(copy.isFile());
    assertEquals(hash, new TorrentInfo(copy).infoHashV1().toHex());
  }

  @Test
  void extraDataSavedByAnEarlierRunIsReadBack() throws Exception {
    TorrentInfo ti = metadata("earlier");
    String hash = ti.infoHashV1().toHex();
    Entry resume =
        Entry.fromMap(
            Map.of(
                "save_path",
                new Entry(dir.toString()),
                "extra_data",
                Entry.fromMap(Map.of("was_paused", new Entry("true"), "count", new Entry(5)))));
    Files.write(local.engine.resumeDataFile(hash).toPath(), resume.bencode());
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());

    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertTrue(dl.wasPaused());
    assertEquals(Map.of("was_paused", "true"), dl.getExtra(), "only text values are kept");
    assertEquals(0, handle.resumeDataRequests.get(), "it already has resume data");
  }

  @Test
  void anUnreadableResumeFileLeavesNoExtraData() throws Exception {
    TorrentInfo ti = metadata("unreadable");
    Files.write(
        local.engine.resumeDataFile(ti.infoHashV1().toHex()).toPath(), new byte[] {'d', 'x'});
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());

    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertTrue(dl.getExtra().isEmpty());
    assertFalse(dl.wasPaused());
  }

  @Test
  void theFirstDistributedSourceIsKeptAndSaved() throws Exception {
    FakeTorrentHandle handle = handle("source", s -> {});
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));
    int requests = handle.resumeDataRequests.get();
    byte[] first = new byte[32];
    byte[] second = new byte[32];
    Arrays.fill(first, (byte) 1);
    Arrays.fill(second, (byte) 2);

    assertFalse(dl.setDistributedSourcePeerPub(new byte[31]), "not a peer key");
    assertTrue(dl.setDistributedSourcePeerPub(first));
    assertFalse(dl.setDistributedSourcePeerPub(second));

    assertArrayEquals(first, dl.getDistributedSourcePeerPub());
    assertEquals(requests + 1, handle.resumeDataRequests.get(), "saved once");
  }

  @Test
  void aNewerDownloadsSourceIsCopiedOnlyWhenThereIsNone() throws Exception {
    BTDownload withSource = new BTDownload(local.engine, new TorrentHandle(handle("a", s -> {})));
    BTDownload without = new BTDownload(local.engine, new TorrentHandle(handle("b", s -> {})));
    BTDownload other = new BTDownload(local.engine, new TorrentHandle(handle("c", s -> {})));
    byte[] source = new byte[32];
    Arrays.fill(source, (byte) 9);
    byte[] own = new byte[32];
    Arrays.fill(own, (byte) 3);
    withSource.setDistributedSourcePeerPub(own);
    other.setDistributedSourcePeerPub(source);

    without.copyDistributedSourcePeerFrom(other);
    withSource.copyDistributedSourcePeerFrom(other);
    other.copyDistributedSourcePeerFrom(null);

    assertArrayEquals(source, without.getDistributedSourcePeerPub());
    assertArrayEquals(own, withSource.getDistributedSourcePeerPub());
    assertArrayEquals(source, other.getDistributedSourcePeerPub());
  }

  @Test
  void anInfoHashThatCannotBeReadIsNull() throws Exception {
    FakeTorrentHandle handle = handle("nohash", s -> {});
    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    handle.hashFailure = new IllegalStateException("invalid handle");

    assertNull(dl.getInfoHash());
  }

  @Test
  void aV2OnlyTorrentsPartsFileIsNamedByItsHandlesHash() throws Exception {
    File content = content(dir.resolve("v2/file.bin"), 16 * 1024, 1);
    FakeTorrentHandle handle =
        new FakeTorrentHandle(torrent(content.getParentFile(), TorrentBuilder.V2_ONLY));
    handle.status.setSave_path(dir.toString());

    BTDownload dl = new BTDownload(local.engine, new TorrentHandle(handle));

    assertEquals(new File(dir.toFile(), "." + handle.hash + ".parts"), dl.partsFile());
  }

  private TransferState stateOf(Consumer<FakeStatus> setup) throws Exception {
    return download(setup).getState();
  }

  private int progressOf(state_t state, float progress) throws Exception {
    return download(
            s -> {
              s.setState(state);
              s.setProgress(progress);
            })
        .getProgress();
  }

  private long etaOf(TorrentInfo ti, long done, int rate) {
    FakeTorrentHandle handle = new FakeTorrentHandle(ti);
    handle.status.setSave_path(dir.toString());
    handle.status.setTotal_done(done);
    handle.status.setDownload_payload_rate(rate);
    return new BTDownload(local.engine, new TorrentHandle(handle)).getETA();
  }

  private BTDownload download(Consumer<FakeStatus> setup) throws Exception {
    return new BTDownload(local.engine, new TorrentHandle(handle("t" + counter++, setup)));
  }

  private int counter;

  private FakeTorrentHandle handle(String name, Consumer<FakeStatus> setup) throws Exception {
    FakeTorrentHandle handle = new FakeTorrentHandle(metadata(name));
    handle.status.setSave_path(dir.toString());
    setup.accept(handle.status);
    return handle;
  }

  /** A two-file torrent of its own, so no two downloads here share resume files. */
  private TorrentInfo metadata(String name) throws Exception {
    Path folder = dir.resolve(name);
    content(folder.resolve("one.mp3"), 64 * 1024, name.hashCode());
    content(folder.resolve("two.flac"), 128 * 1024, name.hashCode() + 1);
    return torrent(folder.toFile(), null);
  }
}
