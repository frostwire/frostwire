/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.frostwire.android.gui.transfers.RealTransfer;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What the piece card shows, read from libtorrent for a transfer that has some of its pieces (from
 * a peer that has only the first file), is missing others and does not want the rest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PieceDetailsRealTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private RealTransfer transfer;

  @After
  public void tearDown() {
    if (transfer != null) {
      transfer.close();
    }
  }

  /** A transfer fed by a peer that has one.mp3; with {@code mp3Only}, two.txt is not wanted. */
  private TorrentHandle open(boolean mp3Only) throws Exception {
    transfer = RealTransfer.start(tmp.newFolder("root").toPath(), null, false, true);
    if (mp3Only) {
      TorrentInfo album = transfer.torrent.album;
      for (int i = 0; i < album.numFiles(); i++) {
        if (album.files().filePath(i).endsWith("two.txt")) {
          transfer.dl.getTorrentHandle().filePriority(i, Priority.IGNORE);
        }
      }
    }
    TorrentHandle handle = transfer.dl.getTorrentHandle();
    int pieceLength = transfer.torrent.album.pieceLength();
    // two.txt (20000 bytes) comes first in the torrent, so the pieces that hold only one.mp3 are
    // the ones after it
    int firstMp3Piece = (20_000 + pieceLength - 1) / pieceLength;
    TestEngine.await(
        () -> {
          for (int piece = firstMp3Piece; piece < transfer.torrent.album.numPieces(); piece++) {
            if (!handle.havePiece(piece)) {
              return false;
            }
          }
          return true;
        },
        "the peer's pieces arrived");
    return handle;
  }

  @Test
  public void aPieceTheTransferHasShowsAsDownloadedWithItsFileAndHash() throws Exception {
    TorrentHandle handle = open(false);
    TorrentInfo info = transfer.torrent.album;

    // the first piece that holds only one.mp3
    int piece = (20_000 + info.pieceLength() - 1) / info.pieceLength();

    PieceDetails details = PieceDetails.read(transfer.ui.getInfoHash(), handle, info, piece);

    assertNotNull(details);
    assertEquals(PieceDetails.State.DOWNLOADED, details.state);
    assertTrue(details.downloaded());
    assertEquals(piece, details.piece);
    assertEquals(info.numPieces(), details.numPieces);
    assertEquals(info.pieceSize(piece), details.size);
    assertEquals((long) piece * info.pieceLength(), details.firstByte);
    assertEquals(info.hashForPiece(piece).toHex(), details.sha1);
    assertEquals(1, details.files.size());
    assertEquals("one.mp3", details.files.get(0).name);
    assertEquals(0, details.moreFiles);
    assertEquals(Priority.NORMAL, details.priority);
  }

  @Test
  public void aPieceThatSpansTwoFilesListsBothAndOneTheTransferLacksIsMissing() throws Exception {
    TorrentHandle handle = open(false);
    TorrentInfo info = transfer.torrent.album;
    // the piece that holds the end of two.txt and the start of one.mp3
    int spanning = 20_000 / info.pieceLength();

    PieceDetails details = PieceDetails.read(transfer.ui.getInfoHash(), handle, info, spanning);

    assertNotNull(details);
    // the peer doesn't have it: it is not downloaded, and not being fetched from anyone
    assertFalse(details.downloaded());
    assertEquals(2, details.files.size());
    assertEquals(spanning * (long) info.pieceLength(), details.firstByte);
    assertEquals("two.txt", details.files.get(0).name);
    assertEquals("one.mp3", details.files.get(1).name);
  }

  @Test
  public void aPieceOfAFileTheUserDoesNotWantIsNotDownloading() throws Exception {
    TorrentHandle handle = open(true);
    TorrentInfo info = transfer.torrent.album;
    // the first piece is two.txt's alone
    PieceDetails details = PieceDetails.read(transfer.ui.getInfoHash(), handle, info, 0);

    assertNotNull(details);
    assertEquals(PieceDetails.State.NOT_DOWNLOADING, details.state);
    assertEquals(Priority.IGNORE, details.priority);
    assertEquals("two.txt", details.files.get(0).name);
  }

  @Test
  public void thereIsNothingToReadForAGoneHandleAnUnknownPieceOrNoTorrent() throws Exception {
    TorrentHandle handle = open(false);
    TorrentInfo info = transfer.torrent.album;
    String hash = transfer.ui.getInfoHash();

    assertNull(PieceDetails.read(hash, null, info, 0));
    assertNull(PieceDetails.read(hash, handle, null, 0));
    assertNull(PieceDetails.read(hash, handle, info, -1));
    assertNull(PieceDetails.read(hash, handle, info, info.numPieces()));
  }

  @Test
  public void paddingFilesAreNotContent() {
    assertTrue(PieceDetails.isPadFile(".pad/1234"));
    assertTrue(PieceDetails.isPadFile("album/.pad/1234"));
    assertTrue(PieceDetails.isPadFile("album\\.pad\\1234"));
    assertFalse(PieceDetails.isPadFile("album/one.mp3"));
    assertFalse(PieceDetails.isPadFile(null));
  }
}
