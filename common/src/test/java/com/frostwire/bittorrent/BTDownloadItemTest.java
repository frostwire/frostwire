/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.content;
import static com.frostwire.bittorrent.LocalEngine.torrent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.PiecesTracker;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One file of a transfer, as the transfers list's details show it: picked, progress, playable. */
class BTDownloadItemTest {

  private static final long SIZE = 64 * 1024;

  @TempDir Path dir;
  private TorrentInfo ti;
  private FakeTorrentHandle handle;
  private BTDownloadItem item;

  @BeforeEach
  void setUp() throws Exception {
    Path folder = dir.resolve("album");
    content(folder.resolve("one.mp3"), (int) SIZE, 1);
    content(folder.resolve("two.flac"), 128 * 1024, 2);
    ti = torrent(folder.toFile(), null);
    handle = new FakeTorrentHandle(ti);
    item = item(null);
  }

  @Test
  void itIsTheFileInTheSaveFolder() {
    assertEquals(new File(dir.toFile(), "album/one.mp3"), item.getFile());
    assertEquals("one.mp3", item.getName());
    assertEquals("one.mp3", item.getDisplayName());
    assertEquals(SIZE, item.getSize());
  }

  @Test
  void progressIsWhatLibtorrentHasOfTheFile() {
    handle.progress = new long[] {SIZE / 4, 0};
    assertEquals(SIZE / 4, item.getDownloaded());
    assertEquals(25, item.getProgress());
    assertFalse(item.isComplete());

    handle.progress = new long[] {SIZE, 0};
    assertEquals(100, item.getProgress());
    assertTrue(item.isComplete());
  }

  @Test
  void anEmptyFileHasNoProgress() {
    BTDownloadItem empty =
        new BTDownloadItem(new TorrentHandle(handle), 0, "empty.txt", 0, null, dir.toFile());

    assertEquals(0, empty.getProgress());
  }

  @Test
  void aFileProgressThatCannotBeReadIsNothingDownloaded() {
    handle.progressFailure = new IllegalStateException("std::bad_alloc");

    assertEquals(0, item.getDownloaded());
    assertEquals(0, item.getProgress());
    assertFalse(item.isComplete());
  }

  @Test
  void itsPriorityIsTheFilesPriorityInTheTorrent() {
    assertFalse(item.isSkipped());
    assertEquals(Priority.NORMAL.swig(), item.getPriority());

    item.setPriority(Priority.IGNORE);

    assertEquals(Priority.IGNORE.swig(), handle.priorities[0]);
    assertTrue(item.isSkipped());
    assertEquals(Priority.IGNORE.swig(), item.getPriority());
  }

  @Test
  void aPriorityThatCannotBeReadMeansSkipped() {
    handle.priorityFailure = new IllegalStateException("invalid handle");

    assertTrue(item.isSkipped());
    assertEquals(Priority.IGNORE.swig(), item.getPriority());
  }

  @Test
  void aFileOfATorrentThatIsGoneIsSkippedAndHasNothing() {
    handle.progress = new long[] {SIZE, 0};
    handle.valid = false;

    item.setPriority(Priority.NORMAL);

    assertEquals(Priority.NORMAL.swig(), handle.priorities[0], "unchanged: nothing was asked");
    assertTrue(item.isSkipped());
    assertEquals(Priority.IGNORE.swig(), item.getPriority());
    assertEquals(0, item.getDownloaded());
    assertEquals(0, item.getProgress());
  }

  @Test
  void aFileWithoutAHandleIsSkipped() {
    BTDownloadItem orphan = new BTDownloadItem(null, 0, "one.mp3", SIZE, null, dir.toFile());

    orphan.setPriority(Priority.NORMAL);

    assertTrue(orphan.isSkipped());
    assertEquals(Priority.IGNORE.swig(), orphan.getPriority());
  }

  @Test
  void itsSequentialBytesAreTheCompletePiecesFromItsStart() {
    PiecesTracker pieces = new PiecesTracker(ti);
    BTDownloadItem tracked = item(pieces);
    assertEquals(0, tracked.getSequentialDownloaded());
    assertEquals(0, item.getSequentialDownloaded(), "nothing without a pieces tracker");

    pieces.setComplete(0, true);

    assertEquals(Math.min(ti.pieceLength(), SIZE), tracked.getSequentialDownloaded());
  }

  private BTDownloadItem item(PiecesTracker pieces) {
    return new BTDownloadItem(
        new TorrentHandle(handle), 0, ti.files().filePath(0), SIZE, pieces, dir.toFile());
  }
}
