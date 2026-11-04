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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.Priority;
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
  private BTEngine engine;

  @BeforeEach
  void startEngine() throws Exception {
    local = LocalEngine.start(dir);
    engine = local.engine;
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

    engine.download(torrent, saveDir, null);

    BTDownload dl = Events.next(local.events.added, "album");
    String hash = ti.infoHashV1().toHex();
    assertEquals(saveDir, dl.getSavePath());
    assertEquals(hash, dl.getV1InfoHash());
    assertEquals(ti.totalSize(), dl.getSize());
    assertEquals("album", dl.getDisplayName());
    assertFalse(dl.isPartial());
    assertEquals(new File(saveDir, "album"), dl.getContentSavePath());
    assertEquals(new File(saveDir, "." + hash + ".parts"), dl.partsFile());
    File sessionCopy = engine.resumeTorrentFile(hash);
    assertTrue(sessionCopy.isFile(), "the session copy of the torrent was written");
    assertEquals(
        hash, TorrentInfo.bdecode(Files.readAllBytes(sessionCopy.toPath())).infoHashV1().toHex());
    assertEquals(engine.torrentFile("album"), engine.readTorrentPath(hash));
    await(() -> saveDir.equals(engine.readSavePath(hash)), "the resume data has the folder");
  }

  @Test
  void onlyTheSelectedFilesDownloadAndAddingTheTorrentAgainChangesTheSelection() throws Exception {
    TorrentInfo ti = album("picked");
    File torrent = torrentFile(ti, dir.resolve("in/picked.torrent"));
    File saveDir = dir.resolve("save").toFile();

    engine.download(torrent, saveDir, new boolean[] {false, true});

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

    engine.download(torrent, saveDir, new boolean[] {true, true});

    Events.next(local.events.updated, "picked");
    await(() -> !dl.isPartial(), "both files are wanted");
    assertEquals("picked", dl.getDisplayName());
  }

  @Test
  void aSearchResultsTorrentIsSavedUnderAFileNameEveryFileSystemAccepts() throws Exception {
    TorrentInfo ti = album("live [2024] a|b");

    engine.download(ti, dir.resolve("save").toFile(), null, null, true);

    Events.next(local.events.added, "live [2024] a|b");
    File saved = new File(local.ctx.torrentsDir, "live _2024_ a_b.torrent");
    assertTrue(saved.isFile(), "the torrent was saved to the torrents folder");
    assertEquals(ti.infoHashV1(), new TorrentInfo(saved).infoHashV1());
  }

  @Test
  void aMagnetIsAddedAndOpeningItAgainResumesTheSameTransfer() throws Exception {
    TorrentInfo ti = album("magnet");
    String magnet = ti.makeMagnetUri();
    File saveDir = dir.resolve("save").toFile();

    engine.download(magnet, saveDir, new torrent_flags_t());

    BTDownload dl = Events.next(local.events.added, "magnet");
    assertNotNull(engine.find(ti.infoHashV1()), "the magnet's transfer is in the session");
    dl.pause();
    await(() -> LocalEngine.isPaused(dl), "the transfer paused");

    // A peer in the link is connected to; ports under 1024 are filtered, so this one never is.
    engine.download(magnet + "&x.pe=127.0.0.1:1", saveDir, new torrent_flags_t());

    BTDownload update = Events.next(local.events.updated, "magnet");
    assertEquals(dl.getInfoHash(), update.getInfoHash());
    await(() -> !LocalEngine.isPaused(dl), "the transfer resumed");
  }

  @Test
  void aCrawledSearchResultDownloadsOnlyItsOwnFile() throws Exception {
    TorrentInfo ti = album("crawled");
    File saveDir = dir.resolve("save").toFile();

    engine.download(crawled(ti, 1), saveDir);

    BTDownload dl = Events.next(local.events.added, "crawled");
    await(
        () ->
            Arrays.equals(
                new Priority[] {Priority.IGNORE, Priority.NORMAL},
                dl.getTorrentHandle().filePriorities()),
        "only the crawled file is wanted");
    assertTrue(new File(local.ctx.torrentsDir, "crawled.torrent").isFile());

    engine.download(crawled(ti, 0), saveDir, true);

    Events.next(local.events.updated, "crawled");
    await(() -> !dl.isPartial(), "the second result's file is wanted too");
  }

  @Test
  void withoutASaveFolderDownloadsGoToTheDataFolder() throws Exception {
    TorrentInfo ti = album("default-folder");

    engine.download(torrentFile(ti, dir.resolve("in/d.torrent")), null, null);

    BTDownload dl = Events.next(local.events.added, "default-folder");
    assertEquals(local.ctx.dataDir, dl.getSavePath());
  }

  @Test
  void aSaveFolderThatCannotBeCreatedAddsNothing() throws Exception {
    Path notAFolder = Files.write(dir.resolve("file"), new byte[] {1});
    File impossible = notAFolder.resolve("sub").toFile();
    TorrentInfo lost = album("lost");
    TorrentInfo kept = album("kept");

    engine.download(torrentFile(lost, dir.resolve("in/lost.torrent")), impossible, null);
    engine.download(lost, impossible, null, null, true);
    engine.download(lost.makeMagnetUri(), impossible, new torrent_flags_t());
    engine.download(crawled(lost, 0), impossible);
    engine.download(kept, dir.resolve("save").toFile(), null, null);

    // Alerts arrive in order: once the later download is in, the earlier ones would have been too.
    Events.next(local.events.added, "kept");
    assertTrue(local.events.added.isEmpty(), "nothing else was added");
    assertNull(engine.find(lost.infoHashV1()));
    assertFalse(engine.resumeTorrentFile(lost.infoHashV1().toHex()).exists());
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
