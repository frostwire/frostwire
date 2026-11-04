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
import static com.frostwire.bittorrent.LocalEngine.torrentFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.transfers.TransferItem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adding transfers to the engine: from a .torrent file, a torrent from a search, a magnet link or
 * one file of a crawled search result. Each one must show up in the transfers list (the engine's
 * downloadAdded), download the files the user picked, and leave a session copy of the torrent in
 * the home folder so the transfer comes back after a restart.
 */
@Timeout(120)
class BTEngineDownloadTest {

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
  void aTorrentFileIsAddedWithASessionCopyThatRemembersWhereItCameFrom() throws Exception {
    TorrentInfo ti = album("album");
    File torrent = torrentFile(ti, dir.resolve("in/album.torrent"));
    File saveDir = dir.resolve("save").toFile();

    local.engine.download(torrent, saveDir, null);

    BTDownload dl = Events.next(local.events.added, "album");
    String hash = ti.infoHashV1().toHex();
    assertEquals(saveDir, dl.getSavePath());
    assertEquals(hash, dl.getV1InfoHash());
    assertEquals(ti.totalSize(), dl.getSize());
    assertEquals("album", dl.getDisplayName());
    assertFalse(dl.isPartial());
    assertEquals(new File(saveDir, "album"), dl.getContentSavePath());
    assertEquals(new File(saveDir, "." + hash + ".parts"), dl.partsFile());
    File sessionCopy = local.engine.resumeTorrentFile(hash);
    assertTrue(sessionCopy.isFile(), "the session copy of the torrent was written");
    assertEquals(
        hash, TorrentInfo.bdecode(Files.readAllBytes(sessionCopy.toPath())).infoHashV1().toHex());
    assertEquals(local.engine.torrentFile("album"), local.engine.readTorrentPath(hash));
    await(() -> saveDir.equals(local.engine.readSavePath(hash)), "the resume data has the folder");
  }

  @Test
  void onlyTheSelectedFilesDownloadAndAddingTheTorrentAgainChangesTheSelection() throws Exception {
    TorrentInfo ti = album("picked");
    File torrent = torrentFile(ti, dir.resolve("in/picked.torrent"));
    File saveDir = dir.resolve("save").toFile();

    local.engine.download(torrent, saveDir, new boolean[] {false, true});

    BTDownload dl = Events.next(local.events.added, "picked");
    TorrentHandle th = dl.getTorrentHandle();
    await(
        () -> Arrays.equals(new Priority[] {Priority.IGNORE, Priority.NORMAL}, th.filePriorities()),
        "only the second file is wanted");
    assertTrue(dl.isPartial());
    assertEquals("two.flac", dl.getDisplayName());
    List<TransferItem> items = dl.getItems();
    assertEquals(2, items.size());
    assertTrue(items.get(0).isSkipped());
    assertFalse(items.get(1).isSkipped());

    local.engine.download(torrent, saveDir, new boolean[] {true, true});

    Events.next(local.events.updated, "picked");
    await(() -> !dl.isPartial(), "both files are wanted");
    assertEquals("picked", dl.getDisplayName());
  }

  @Test
  void aSearchResultsTorrentIsSavedUnderAFileNameEveryFileSystemAccepts() throws Exception {
    TorrentInfo ti = album("live [2024] a|b");

    local.engine.download(ti, dir.resolve("save").toFile(), null, null, true);

    Events.next(local.events.added, "live [2024] a|b");
    File saved = new File(local.ctx.torrentsDir, "live _2024_ a_b.torrent");
    assertTrue(saved.isFile(), "the torrent was saved to the torrents folder");
    assertEquals(ti.infoHashV1(), new TorrentInfo(saved).infoHashV1());
  }

  @Test
  void aV2OnlyTorrentDownloadsOnlyThePickedFilesAndIsStoredUnderItsV2Hash() throws Exception {
    TorrentInfo ti = album("v2album", TorrentBuilder.V2_ONLY);
    File torrent = torrentFile(ti, dir.resolve("in/v2album.torrent"));
    // A v2-only torrent's pick is applied on its ADD_TORRENT alert. Hold the adding thread right
    // after it adds the torrent until that alert has been handled, as a busy machine can.
    java.util.logging.Logger log = java.util.logging.Logger.getLogger("BTEngine");
    Handler holdAfterAdding =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            String message = record.getMessage();
            if (message != null && message.contains("new v2-only torrent added")) {
              long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
              while (local.events.added.isEmpty() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
              }
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    log.addHandler(holdAfterAdding);
    try {
      local.engine.download(torrent, dir.resolve("save").toFile(), new boolean[] {true, false});
    } finally {
      log.removeHandler(holdAfterAdding);
    }

    BTDownload dl = Events.next(local.events.added, "v2album");
    await(
        () ->
            Arrays.equals(
                new Priority[] {Priority.NORMAL, Priority.IGNORE},
                dl.getTorrentHandle().filePriorities()),
        "only the first file is wanted");
    assertFalse(LocalEngine.isPaused(dl), "the transfer starts once its files are picked");
    assertNull(dl.getV1InfoHash(), "a v2-only torrent has no v1 hash");
    String v2 = ti.infoHashV2().toHex();
    assertTrue(
        local.engine.resumeTorrentFile(v2).isFile(), "the session copy is named by its v2 hash");
  }

  @Test
  void aMagnetIsAddedAndOpeningItAgainResumesTheSameTransfer() throws Exception {
    TorrentInfo ti = album("magnet");
    String magnet = ti.makeMagnetUri();
    File saveDir = dir.resolve("save").toFile();

    local.engine.download(magnet, saveDir, new torrent_flags_t());

    BTDownload dl = Events.next(local.events.added, "magnet");
    assertNotNull(local.engine.find(ti.infoHashV1()), "the magnet's transfer is in the session");
    dl.pause();
    await(() -> LocalEngine.isPaused(dl), "the transfer paused");

    // A peer in the link is connected to; ports under 1024 are filtered, so this one never is.
    local.engine.download(magnet + "&x.pe=127.0.0.1:1", saveDir, new torrent_flags_t());

    BTDownload update = Events.next(local.events.updated, "magnet");
    assertEquals(dl.getInfoHash(), update.getInfoHash());
    await(() -> !LocalEngine.isPaused(dl), "the transfer resumed");
  }

  @Test
  void aMagnetStillFetchingItsMetadataIsReplacedByItsTorrent() throws Exception {
    TorrentInfo ti = album("zombie");
    File saveDir = dir.resolve("save").toFile();
    // No peers: it never gets its metadata.
    local.engine.download(ti.makeMagnetUri(), saveDir, new torrent_flags_t());
    BTDownload magnet = Events.next(local.events.added, "zombie");
    assertEquals(0, magnet.getSize(), "no metadata yet");

    local.engine.download(ti, saveDir, null, null);

    BTDownload full = Events.next(local.events.added, "zombie");
    assertEquals(ti.totalSize(), full.getSize());
    assertEquals(2, full.getItems().size());
  }

  @Test
  void aV1MagnetFromADistributedSearchKeepsItsSource() throws Exception {
    TorrentInfo ti = album("v1-magnet", TorrentBuilder.V1_ONLY);
    byte[] source = new byte[32];
    Arrays.fill(source, (byte) 5);

    local.engine.download(
        ti.makeMagnetUri(), dir.resolve("save").toFile(), new torrent_flags_t(), source);

    assertArrayEquals(
        source, Events.next(local.events.added, "v1-magnet").getDistributedSourcePeerPub());
  }

  @Test
  void aHybridMagnetFromADistributedSearchKeepsItsSource() throws Exception {
    TorrentInfo ti = album("hybrid-magnet");
    byte[] source = new byte[32];
    Arrays.fill(source, (byte) 6);
    String magnet = ti.makeMagnetUri();
    assertTrue(magnet.contains("urn:btmh:"), magnet);

    local.engine.download(magnet, dir.resolve("save").toFile(), new torrent_flags_t(), source);

    assertArrayEquals(
        source, Events.next(local.events.added, "hybrid-magnet").getDistributedSourcePeerPub());
  }

  @Test
  void aSourceThatIsNotAPeerKeyIsIgnored() throws Exception {
    TorrentInfo ti = album("bad-source");

    local.engine.download(
        ti.makeMagnetUri(), dir.resolve("save").toFile(), new torrent_flags_t(), new byte[16]);

    assertNull(Events.next(local.events.added, "bad-source").getDistributedSourcePeerPub());
  }

  @Test
  void aCrawledSearchResultDownloadsOnlyItsOwnFile() throws Exception {
    TorrentInfo ti = album("crawled");
    File saveDir = dir.resolve("save").toFile();

    local.engine.download(crawled(ti, 1), saveDir);

    BTDownload dl = Events.next(local.events.added, "crawled");
    await(
        () ->
            Arrays.equals(
                new Priority[] {Priority.IGNORE, Priority.NORMAL},
                dl.getTorrentHandle().filePriorities()),
        "only the crawled file is wanted");
    assertTrue(new File(local.ctx.torrentsDir, "crawled.torrent").isFile());

    local.engine.download(crawled(ti, 0), saveDir, true);

    Events.next(local.events.updated, "crawled");
    await(() -> !dl.isPartial(), "the second result's file is wanted too");
  }

  @Test
  void withoutASaveFolderDownloadsGoToTheDataFolder() throws Exception {
    TorrentInfo ti = album("default-folder");

    local.engine.download(torrentFile(ti, dir.resolve("in/d.torrent")), null, null);

    BTDownload dl = Events.next(local.events.added, "default-folder");
    assertEquals(local.ctx.dataDir, dl.getSavePath());
  }

  @Test
  void aSaveFolderThatCannotBeCreatedAddsNothing() throws Exception {
    Path notAFolder = Files.write(dir.resolve("file"), new byte[] {1});
    File impossible = notAFolder.resolve("sub").toFile();
    TorrentInfo lost = album("lost");
    TorrentInfo kept = album("kept");

    local.engine.download(torrentFile(lost, dir.resolve("in/lost.torrent")), impossible, null);
    local.engine.download(lost, impossible, null, null, true);
    local.engine.download(lost.makeMagnetUri(), impossible, new torrent_flags_t());
    local.engine.download(crawled(lost, 0), impossible);
    local.engine.download(kept, dir.resolve("save").toFile(), null, null);

    // Alerts arrive in order: once the later download is in, the earlier ones would have been too.
    Events.next(local.events.added, "kept");
    assertTrue(local.events.added.isEmpty(), "nothing else was added");
    assertNull(local.engine.find(lost.infoHashV1()));
    assertFalse(local.engine.resumeTorrentFile(lost.infoHashV1().toHex()).exists());
  }

  private TorrentInfo album(String name) throws Exception {
    return album(name, null);
  }

  private TorrentInfo album(String name, com.frostwire.jlibtorrent.swig.create_flags_t flags)
      throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    content(folder.resolve("one.mp3"), 64 * 1024, 1);
    content(folder.resolve("two.flac"), 128 * 1024, 2);
    return torrent(folder.toFile(), flags);
  }

  private static TorrentCrawledSearchResult crawled(TorrentInfo ti, int index) {
    return new TorrentCrawledSearchResult(
        null, ti, index, ti.files().filePath(index), ti.files().fileSize(index));
  }
}
